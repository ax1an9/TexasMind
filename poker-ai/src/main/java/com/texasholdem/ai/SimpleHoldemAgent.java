package com.texasholdem.ai;

import com.texasholdem.core.eval.HandEvaluator;
import com.texasholdem.core.eval.HandValue;
import com.texasholdem.core.model.Action;
import com.texasholdem.core.model.AllInAction;
import com.texasholdem.core.model.BetAction;
import com.texasholdem.core.model.CallAction;
import com.texasholdem.core.model.Card;
import com.texasholdem.core.model.CheckAction;
import com.texasholdem.core.model.FoldAction;
import com.texasholdem.core.model.GameState;
import com.texasholdem.core.model.HandRank;
import com.texasholdem.core.model.PlayerState;
import com.texasholdem.core.model.RaiseAction;

import java.util.ArrayList;
import java.util.List;

public final class SimpleHoldemAgent implements BuiltinAgent {
    private final HandEvaluator handEvaluator = new HandEvaluator();

    @Override
    public Action decide(GameState state, PlayerState self) {
        double strength = estimateStrength(state, self);
        int toCall = Math.max(0, state.getCurrentBet() - self.getRoundContribution());

        if (self.getChips() <= toCall) {
            return new AllInAction(self.getSeatId());
        }

        if (toCall == 0) {
            if (strength >= 0.75d && self.getChips() > 0) {
                int betSize = Math.min(Math.max(2, state.getCurrentBet() == 0 ? 4 : state.getCurrentBet() * 2),
                        self.getChips());
                return state.getCurrentBet() == 0 ? new BetAction(self.getSeatId(), betSize)
                        : new RaiseAction(self.getSeatId(), self.getRoundContribution() + betSize);
            }
            return new CheckAction(self.getSeatId());
        }

        if (strength < 0.28d) {
            return new FoldAction(self.getSeatId());
        }
        if (strength < 0.58d) {
            return new CallAction(self.getSeatId());
        }

        int raiseTo = self.getRoundContribution() + toCall
                + Math.min(state.getCurrentBet() == 0 ? 2 : state.getCurrentBet(), self.getChips() - toCall);
        if (raiseTo <= state.getCurrentBet()) {
            return new CallAction(self.getSeatId());
        }
        return state.getCurrentBet() == 0 ? new BetAction(self.getSeatId(), raiseTo)
                : new RaiseAction(self.getSeatId(), raiseTo);
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

        double score = 0.12d + (high - 2) / 12.0d * 0.2d;
        if (pair) {
            score += 0.45d;
        }
        if (suited) {
            score += 0.05d;
        }
        if (gap == 1) {
            score += 0.08d;
        } else if (gap == 2) {
            score += 0.04d;
        }
        if (high >= 11) {
            score += 0.08d;
        }
        return Math.max(0.0d, Math.min(1.0d, score));
    }

    private double score(HandRank handRank) {
        switch (handRank) {
            case HIGH_CARD:
                return 0.12d;
            case ONE_PAIR:
                return 0.30d;
            case TWO_PAIR:
                return 0.44d;
            case THREE_OF_A_KIND:
                return 0.60d;
            case STRAIGHT:
                return 0.74d;
            case FLUSH:
                return 0.82d;
            case FULL_HOUSE:
                return 0.91d;
            case FOUR_OF_A_KIND:
                return 0.97d;
            case STRAIGHT_FLUSH:
            case ROYAL_FLUSH:
                return 1.0d;
            default:
                return 0.0d;
        }
    }
}