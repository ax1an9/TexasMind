package com.texasholdem.ai;

import com.texasholdem.core.eval.HandEvaluator;
import com.texasholdem.core.eval.HandValue;
import com.texasholdem.core.model.Card;
import com.texasholdem.core.model.GameState;
import com.texasholdem.core.model.HandRank;
import com.texasholdem.core.model.PlayerState;

import java.util.ArrayList;
import java.util.List;

public final class HintAdvisor {
    private final HandEvaluator handEvaluator = new HandEvaluator();

    public HintResult analyze(GameState state, PlayerState self) {
        double strength = estimateStrength(state, self);
        int toCall = Math.max(0, state.getCurrentBet() - self.getRoundContribution());
        int totalPot = state.getPot().getTotalPot();
        double potOdds = (toCall > 0) ? (double) toCall / (totalPot + toCall) : 0.0;

        String suggestion;
        String reasoning;

        if (toCall == 0) {
            if (strength >= 0.75) {
                suggestion = "RAISE";
                reasoning = buildReasoning(strength, potOdds, "Strong hand, bet for value");
            } else if (strength >= 0.50) {
                suggestion = "CHECK";
                reasoning = buildReasoning(strength, potOdds, "Decent hand, check and see next card");
            } else {
                suggestion = "CHECK";
                reasoning = buildReasoning(strength, potOdds, "Weak hand, check to see a free card");
            }
        } else if (strength < potOdds * 0.7) {
            suggestion = "FOLD";
            reasoning = buildReasoning(strength, potOdds, "Hand too weak for the pot odds, fold");
        } else if (strength >= 0.75) {
            suggestion = "RAISE";
            reasoning = buildReasoning(strength, potOdds, "Strong hand, raise for value");
        } else if (strength >= potOdds) {
            suggestion = "CALL";
            reasoning = buildReasoning(strength, potOdds, "Favorable pot odds, call is profitable");
        } else {
            suggestion = "CALL";
            reasoning = buildReasoning(strength, potOdds, "Marginal spot, call if odds are close");
        }

        return new HintResult(suggestion, strength, potOdds, reasoning);
    }

    private String buildReasoning(double strength, double potOdds, String advice) {
        return String.format("Hand strength: %.0f%%, Pot odds: %.0f%%. %s.",
                strength * 100, potOdds * 100, advice);
    }

    private double estimateStrength(GameState state, PlayerState self) {
        List<Card> knownCards = new ArrayList<Card>(state.getBoard());
        knownCards.addAll(self.getHoleCards());
        if (knownCards.size() >= 5) {
            HandValue value = handEvaluator.evaluateBest(knownCards);
            return score(value.getHandRank());
        }

        if (self.getHoleCards().size() < 2) {
            return 0.0d;
        }

        int first = self.getHoleCards().get(0).getRank().getValue();
        int second = self.getHoleCards().get(1).getRank().getValue();
        boolean pair = first == second;
        boolean suited = self.getHoleCards().get(0).getSuit() == self.getHoleCards().get(1).getSuit();
        int high = Math.max(first, second);
        int low = Math.min(first, second);
        int gap = high - low;

        double s = 0.12d + (high - 2) / 12.0d * 0.2d;
        if (pair) s += 0.45d;
        if (suited) s += 0.05d;
        if (gap == 1) s += 0.08d;
        else if (gap == 2) s += 0.04d;
        if (high >= 11) s += 0.08d;
        return Math.max(0.0d, Math.min(1.0d, s));
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
}
