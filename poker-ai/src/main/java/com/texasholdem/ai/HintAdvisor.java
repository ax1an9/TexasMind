package com.texasholdem.ai;

import com.texasholdem.core.eval.HandEvaluator;
import com.texasholdem.core.eval.HandValue;
import com.texasholdem.core.model.Card;
import com.texasholdem.core.model.GameState;
import com.texasholdem.core.model.HandRank;
import com.texasholdem.core.model.PlayerState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HintAdvisor {
    private final HandEvaluator handEvaluator = new HandEvaluator();

    public HintResult analyze(GameState state, PlayerState self) {
        StrengthEstimate estimate = estimateStrength(state, self);
        double strength = estimate.strength;

        int toCall = Math.max(0, state.getCurrentBet() - self.getRoundContribution());
        int totalPot = state.getPot().getTotalPot();
        double potOdds = (toCall > 0) ? (double) toCall / (totalPot + toCall) : 0.0;

        String suggestion;
        String reasoning;
        String simpleReasoning;

        if (toCall == 0) {
            if (strength >= 0.75) {
                suggestion = "RAISE";
                reasoning = buildReasoning(strength, potOdds, "牌力很强，建议加注获取价值");
                simpleReasoning = "你的牌很强，建议加注。";
            } else if (strength >= 0.50) {
                suggestion = "CHECK";
                reasoning = buildReasoning(strength, potOdds, "牌力中等，过牌看看下一张牌");
                simpleReasoning = "牌还可以，先过牌看看。";
            } else {
                suggestion = "CHECK";
                reasoning = buildReasoning(strength, potOdds, "牌力较弱，过牌争取免费看牌");
                simpleReasoning = "牌不太强，过牌就好。";
            }
        } else if (strength < potOdds * 0.7) {
            suggestion = "FOLD";
            reasoning = buildReasoning(strength, potOdds, "牌力不足以支撑底池赔率，建议弃牌");
            simpleReasoning = "牌太弱了，不值得跟，建议弃牌。";
        } else if (strength >= 0.75) {
            suggestion = "RAISE";
            reasoning = buildReasoning(strength, potOdds, "牌力很强，加注获取价值");
            simpleReasoning = "你的牌很强，建议加注。";
        } else if (strength >= potOdds) {
            suggestion = "CALL";
            reasoning = buildReasoning(strength, potOdds, "底池赔率合适，跟注有利可图");
            simpleReasoning = "赔率不错，跟注是划算的。";
        } else {
            suggestion = "CALL";
            reasoning = buildReasoning(strength, potOdds, "边缘局面，赔率接近时可以跟注");
            simpleReasoning = "局面比较边缘，跟注看看也行。";
        }

        return new HintResult(suggestion, strength, potOdds, reasoning,
                estimate.handRankName, estimate.factors, simpleReasoning, toCall, totalPot);
    }

    private String buildReasoning(double strength, double potOdds, String advice) {
        return String.format("手牌强度 %.0f%%，底池赔率 %.0f%%。%s。", strength * 100, potOdds * 100, advice);
    }

    private StrengthEstimate estimateStrength(GameState state, PlayerState self) {
        List<Card> knownCards = new ArrayList<Card>(state.getBoard());
        knownCards.addAll(self.getHoleCards());

        if (knownCards.size() >= 5) {
            HandValue value = handEvaluator.evaluateBest(knownCards);
            double s = score(value.getHandRank());
            String rankName = handRankChinese(value.getHandRank());
            List<HintResult.StrengthFactor> factors = Collections.singletonList(
                    new HintResult.StrengthFactor("牌型评估", s, "当前牌型: " + rankName));
            return new StrengthEstimate(s, factors, rankName);
        }

        if (self.getHoleCards().size() < 2) {
            return new StrengthEstimate(0.0d, Collections.emptyList(), "底牌评估");
        }

        int first = self.getHoleCards().get(0).getRank().getValue();
        int second = self.getHoleCards().get(1).getRank().getValue();
        boolean pair = first == second;
        boolean suited = self.getHoleCards().get(0).getSuit() == self.getHoleCards().get(1).getSuit();
        int high = Math.max(first, second);
        int low = Math.min(first, second);
        int gap = high - low;

        List<HintResult.StrengthFactor> factors = new ArrayList<>();
        double base = 0.12d + (high - 2) / 12.0d * 0.2d;
        factors.add(new HintResult.StrengthFactor("基础牌力", base,
                "高牌 " + cardName(high) + " 的基础分"));

        double s = base;
        if (pair) {
            s += 0.45d;
            factors.add(new HintResult.StrengthFactor("对子加成", 0.45d,
                    cardName(first) + " 口袋对子"));
        }
        if (suited) {
            s += 0.05d;
            factors.add(new HintResult.StrengthFactor("同花加成", 0.05d, "同花花色"));
        }
        if (gap == 1) {
            s += 0.08d;
            factors.add(new HintResult.StrengthFactor("连牌加成", 0.08d,
                    cardName(high) + cardName(low) + " 相连"));
        } else if (gap == 2) {
            s += 0.04d;
            factors.add(new HintResult.StrengthFactor("邻牌加成", 0.04d,
                    cardName(high) + cardName(low) + " 间隔一张"));
        }
        if (high >= 11) {
            s += 0.08d;
            factors.add(new HintResult.StrengthFactor("高牌加成", 0.08d,
                    cardName(high) + " 为高牌(J+)"));
        }
        s = Math.max(0.0d, Math.min(1.0d, s));

        return new StrengthEstimate(s, factors, "底牌评估");
    }

    private double score(HandRank handRank) {
        switch (handRank) {
            case HIGH_CARD: return 0.12d;
            case ONE_PAIR: return 0.30d;
            case TWO_PAIR: return 0.44d;
            case THREE_OF_A_KIND: return 0.60d;
            case STRAIGHT: return 0.74d;
            case FLUSH: return 0.82d;
            case FULL_HOUSE: return 0.91d;
            case FOUR_OF_A_KIND: return 0.97d;
            case STRAIGHT_FLUSH:
            case ROYAL_FLUSH: return 1.0d;
            default: return 0.0d;
        }
    }

    private String handRankChinese(HandRank handRank) {
        switch (handRank) {
            case HIGH_CARD: return "高牌";
            case ONE_PAIR: return "一对";
            case TWO_PAIR: return "两对";
            case THREE_OF_A_KIND: return "三条";
            case STRAIGHT: return "顺子";
            case FLUSH: return "同花";
            case FULL_HOUSE: return "葫芦";
            case FOUR_OF_A_KIND: return "四条";
            case STRAIGHT_FLUSH: return "同花顺";
            case ROYAL_FLUSH: return "皇家同花顺";
            default: return "未知";
        }
    }

    private String cardName(int value) {
        switch (value) {
            case 14: return "A";
            case 13: return "K";
            case 12: return "Q";
            case 11: return "J";
            default: return String.valueOf(value);
        }
    }

    private static final class StrengthEstimate {
        final double strength;
        final List<HintResult.StrengthFactor> factors;
        final String handRankName;

        StrengthEstimate(double strength, List<HintResult.StrengthFactor> factors, String handRankName) {
            this.strength = strength;
            this.factors = factors;
            this.handRankName = handRankName;
        }
    }
}
