package com.texasholdem.ai.grpc;

import com.texasholdem.core.model.*;
import poker_agent.PokerAgentOuterClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts Java poker model objects to protobuf messages for gRPC communication
 * with the Python agent.
 */
public final class ProtoAdapter {

    private static final int BIG_BLIND = 10;

    private ProtoAdapter() {}

    // --- Enum conversions ---

    public static PokerAgentOuterClass.Rank toProto(Rank rank) {
        switch (rank) {
            case TWO:   return PokerAgentOuterClass.Rank.TWO;
            case THREE: return PokerAgentOuterClass.Rank.THREE;
            case FOUR:  return PokerAgentOuterClass.Rank.FOUR;
            case FIVE:  return PokerAgentOuterClass.Rank.FIVE;
            case SIX:   return PokerAgentOuterClass.Rank.SIX;
            case SEVEN: return PokerAgentOuterClass.Rank.SEVEN;
            case EIGHT: return PokerAgentOuterClass.Rank.EIGHT;
            case NINE:  return PokerAgentOuterClass.Rank.NINE;
            case TEN:   return PokerAgentOuterClass.Rank.TEN;
            case JACK:  return PokerAgentOuterClass.Rank.JACK;
            case QUEEN: return PokerAgentOuterClass.Rank.QUEEN;
            case KING:  return PokerAgentOuterClass.Rank.KING;
            case ACE:   return PokerAgentOuterClass.Rank.ACE;
            default:
                throw new IllegalArgumentException("Unknown rank: " + rank);
        }
    }

    public static PokerAgentOuterClass.Suit toProto(Suit suit) {
        switch (suit) {
            case HEARTS:   return PokerAgentOuterClass.Suit.HEARTS;
            case DIAMONDS: return PokerAgentOuterClass.Suit.DIAMONDS;
            case CLUBS:    return PokerAgentOuterClass.Suit.CLUBS;
            case SPADES:   return PokerAgentOuterClass.Suit.SPADES;
            default:
                throw new IllegalArgumentException("Unknown suit: " + suit);
        }
    }

    public static PokerAgentOuterClass.GamePhase toProto(GamePhase phase) {
        switch (phase) {
            case PRE_FLOP:  return PokerAgentOuterClass.GamePhase.PRE_FLOP;
            case FLOP:      return PokerAgentOuterClass.GamePhase.FLOP;
            case TURN:      return PokerAgentOuterClass.GamePhase.TURN;
            case RIVER:     return PokerAgentOuterClass.GamePhase.RIVER;
            case SHOWDOWN:  return PokerAgentOuterClass.GamePhase.SHOWDOWN;
            case SETTLED:   return PokerAgentOuterClass.GamePhase.SETTLED;
            case WAITING:   return PokerAgentOuterClass.GamePhase.PRE_FLOP;
            default:
                throw new IllegalArgumentException("Unknown phase: " + phase);
        }
    }

    public static PokerAgentOuterClass.ActionType toProto(ActionType type) {
        switch (type) {
            case FOLD:   return PokerAgentOuterClass.ActionType.FOLD;
            case CHECK:  return PokerAgentOuterClass.ActionType.CHECK;
            case CALL:   return PokerAgentOuterClass.ActionType.CALL;
            case BET:    return PokerAgentOuterClass.ActionType.BET;
            case RAISE:  return PokerAgentOuterClass.ActionType.RAISE;
            case ALL_IN: return PokerAgentOuterClass.ActionType.ALL_IN;
            default:
                throw new IllegalArgumentException("Unknown action type: " + type);
        }
    }

    // --- Card conversion ---

    public static PokerAgentOuterClass.Card toProto(Card card) {
        return PokerAgentOuterClass.Card.newBuilder()
                .setRank(toProto(card.getRank()))
                .setSuit(toProto(card.getSuit()))
                .build();
    }

    // --- Player conversions ---

    public static PokerAgentOuterClass.PlayerView toProto(PlayerState player, List<Card> holeCards) {
        PokerAgentOuterClass.PlayerView.Builder builder =
                PokerAgentOuterClass.PlayerView.newBuilder()
                        .setSeatId(player.getSeatId())
                        .setChips(player.getChips())
                        .setRoundContribution(player.getRoundContribution())
                        .setIsAllIn(player.isAllIn())
                        .setIsFolded(player.isFolded());

        for (Card card : holeCards) {
            builder.addHoleCards(toProto(card));
        }

        return builder.build();
    }

    public static PokerAgentOuterClass.PlayerSummary toSummaryProto(PlayerState player) {
        return PokerAgentOuterClass.PlayerSummary.newBuilder()
                .setSeatId(player.getSeatId())
                .setChips(player.getChips())
                .setRoundContribution(player.getRoundContribution())
                .setIsAllIn(player.isAllIn())
                .setIsFolded(player.isFolded())
                .build();
    }

    // --- Pot conversions ---

    public static PokerAgentOuterClass.PotSlice toProto(PotSlice slice) {
        PokerAgentOuterClass.PotSlice.Builder builder =
                PokerAgentOuterClass.PotSlice.newBuilder()
                        .setAmount(slice.getAmount());

        for (String seatId : slice.getEligibleSeatIds()) {
            builder.addEligibleSeatIds(seatId);
        }

        return builder.build();
    }

    public static PokerAgentOuterClass.PotInfo toProto(PotInfo pot) {
        PokerAgentOuterClass.PotInfo.Builder builder =
                PokerAgentOuterClass.PotInfo.newBuilder()
                        .setTotalPot(pot.getTotalPot())
                        .setMainPot(pot.getMainPot());

        for (PotSlice sidePot : pot.getSidePots()) {
            builder.addSidePots(toProto(sidePot));
        }

        return builder.build();
    }

    // --- Action entry conversion ---

    public static PokerAgentOuterClass.ActionEntry toActionEntryProto(Action action, GamePhase phase) {
        return PokerAgentOuterClass.ActionEntry.newBuilder()
                .setSeatId(action.getPlayerId())
                .setActionType(toProto(action.getType()))
                .setAmount(action.getAmount())
                .setPhase(toProto(phase))
                .build();
    }

    // --- Legal action conversion ---

    public static PokerAgentOuterClass.LegalAction toProto(ActionType type, boolean available,
                                                            int minAmount, int maxAmount) {
        return PokerAgentOuterClass.LegalAction.newBuilder()
                .setType(toProto(type))
                .setIsAvailable(available)
                .setMinAmount(minAmount)
                .setMaxAmount(maxAmount)
                .build();
    }

    // --- Main method: build DecisionRequest ---

    public static PokerAgentOuterClass.DecisionRequest buildDecisionRequest(String gameId,
                                                                             GameState state,
                                                                             PlayerState self) {
        // Build GameStateView
        PokerAgentOuterClass.GameStateView.Builder stateBuilder =
                PokerAgentOuterClass.GameStateView.newBuilder()
                        .setPhase(toProto(state.getPhase()))
                        .setDealerPosition(state.getDealerPosition())
                        .setCurrentBet(state.getCurrentBet());

        // Board cards
        for (Card card : state.getBoard()) {
            stateBuilder.addBoard(toProto(card));
        }

        // Self (with hole cards)
        stateBuilder.setSelf(toProto(self, self.getHoleCards()));

        // Opponents (without hole cards)
        for (PlayerState player : state.getPlayers()) {
            if (!player.getSeatId().equals(self.getSeatId())) {
                stateBuilder.addOpponents(toSummaryProto(player));
            }
        }

        // Pot info
        if (state.getPot() != null) {
            stateBuilder.setPot(toProto(state.getPot()));
        }

        // Action history
        for (Action action : state.getActionHistory()) {
            stateBuilder.addActionHistory(toActionEntryProto(action, state.getPhase()));
        }

        // Build DecisionRequest
        PokerAgentOuterClass.DecisionRequest.Builder reqBuilder =
                PokerAgentOuterClass.DecisionRequest.newBuilder()
                        .setGameId(gameId)
                        .setGameState(stateBuilder.build());

        // Legal actions
        for (PokerAgentOuterClass.LegalAction legalAction : determineLegalActions(state, self)) {
            reqBuilder.addLegalActions(legalAction);
        }

        return reqBuilder.build();
    }

    // --- Legal action determination ---

    static List<PokerAgentOuterClass.LegalAction> determineLegalActions(GameState state,
                                                                         PlayerState self) {
        List<PokerAgentOuterClass.LegalAction> actions =
                new ArrayList<PokerAgentOuterClass.LegalAction>();

        int currentBet = state.getCurrentBet();
        int toCall = currentBet - self.getRoundContribution();
        int chips = self.getChips();

        // FOLD: available if there is something to call
        actions.add(toProto(ActionType.FOLD, toCall > 0, 0, 0));

        // CHECK: available if nothing to call
        actions.add(toProto(ActionType.CHECK, toCall <= 0, 0, 0));

        // CALL: available if there is something to call and player has chips
        if (toCall > 0 && chips > 0) {
            int callAmount = Math.min(toCall, chips);
            actions.add(toProto(ActionType.CALL, true, callAmount, callAmount));
        } else {
            actions.add(toProto(ActionType.CALL, false, 0, 0));
        }

        // BET: available if no current bet and player has chips
        if (currentBet == 0 && chips > 0) {
            actions.add(toProto(ActionType.BET, true, BIG_BLIND, chips));
        } else {
            actions.add(toProto(ActionType.BET, false, 0, 0));
        }

        // RAISE: available if current bet > 0 and player has more chips than toCall
        if (currentBet > 0 && chips > toCall) {
            int minRaise = currentBet * 2;
            actions.add(toProto(ActionType.RAISE, true, minRaise, chips));
        } else {
            actions.add(toProto(ActionType.RAISE, false, 0, 0));
        }

        // ALL_IN: available if player has chips
        actions.add(toProto(ActionType.ALL_IN, chips > 0, chips, chips));

        return actions;
    }
}
