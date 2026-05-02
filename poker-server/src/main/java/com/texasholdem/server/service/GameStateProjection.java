package com.texasholdem.server.service;

import com.texasholdem.common.protocol.*;
import com.texasholdem.core.eval.HandEvaluator;
import com.texasholdem.core.eval.HandValue;
import com.texasholdem.core.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GameStateProjection {
    private static final HandEvaluator HAND_EVALUATOR = new HandEvaluator();

    private static final Map<HandRank, String> RANK_DISPLAY = new HashMap<>();
    static {
        RANK_DISPLAY.put(HandRank.HIGH_CARD, "High Card");
        RANK_DISPLAY.put(HandRank.ONE_PAIR, "One Pair");
        RANK_DISPLAY.put(HandRank.TWO_PAIR, "Two Pair");
        RANK_DISPLAY.put(HandRank.THREE_OF_A_KIND, "Three of a Kind");
        RANK_DISPLAY.put(HandRank.STRAIGHT, "Straight");
        RANK_DISPLAY.put(HandRank.FLUSH, "Flush");
        RANK_DISPLAY.put(HandRank.FULL_HOUSE, "Full House");
        RANK_DISPLAY.put(HandRank.FOUR_OF_A_KIND, "Four of a Kind");
        RANK_DISPLAY.put(HandRank.STRAIGHT_FLUSH, "Straight Flush");
        RANK_DISPLAY.put(HandRank.ROYAL_FLUSH, "Royal Flush");
    }

    public static GameStateMessage toPlayerView(String roomId, GameState state, String viewerId,
                                                  Map<String, String> agentTypeMap) {
        GameStateMessage msg = new GameStateMessage();
        msg.setGameId(roomId + "_" + state.getHandNumber());
        msg.setRoomId(roomId);
        msg.setPhase(state.getPhase().name());
        msg.setCurrentBet(state.getCurrentBet());

        PlayerState currentPlayer = state.getCurrentPlayer();
        msg.setCurrentPlayerId(currentPlayer != null ? currentPlayer.getSeatId() : null);

        msg.setBoard(state.getBoard().stream()
                .map(c -> new CardView(c.getRank().name(), c.getSuit().name()))
                .collect(Collectors.toList()));

        boolean showAllCards = state.getPhase() == GamePhase.SETTLED || state.getPhase() == GamePhase.SHOWDOWN;
        boolean showHandRank = showAllCards && state.getBoard().size() >= 5;

        List<PlayerView> playerViews = new ArrayList<>();
        for (PlayerState p : state.getPlayers()) {
            PlayerView pv = new PlayerView();
            pv.setSeatId(p.getSeatId());
            pv.setChips(p.getChips());
            pv.setFolded(p.isFolded());
            pv.setAllIn(p.isAllIn());
            pv.setRoundContribution(p.getRoundContribution());
            pv.setTotalContribution(p.getTotalContribution());

            if (showAllCards || p.getSeatId().equals(viewerId)) {
                pv.setHoleCards(p.getHoleCards().stream()
                        .map(c -> new CardView(c.getRank().name(), c.getSuit().name()))
                        .collect(Collectors.toList()));
            }

            // Show hand rank at showdown for non-folded players
            if (showHandRank && !p.isFolded() && p.getHoleCards().size() == 2) {
                List<Card> allCards = new ArrayList<>(state.getBoard());
                allCards.addAll(p.getHoleCards());
                try {
                    HandValue value = HAND_EVALUATOR.evaluateBest(allCards);
                    pv.setHandRank(value.getHandRank().name());
                    pv.setHandRankDisplay(RANK_DISPLAY.getOrDefault(value.getHandRank(), value.getHandRank().name()));
                } catch (Exception ignored) {}
            }

            if (agentTypeMap != null && agentTypeMap.containsKey(p.getSeatId())) {
                pv.setAgentType(agentTypeMap.get(p.getSeatId()));
            }
            playerViews.add(pv);
        }
        msg.setPlayers(playerViews);

        PotView potView = new PotView();
        potView.setTotalPot(state.getPot().getTotalPot());
        potView.setMainPot(state.getPot().getMainPot());
        potView.setSidePots(state.getPot().getSidePots().stream()
                .map(sp -> {
                    PotView.SidePotView spv = new PotView.SidePotView();
                    spv.setAmount(sp.getAmount());
                    spv.setEligibleSeatIds(sp.getEligibleSeatIds());
                    return spv;
                }).collect(Collectors.toList()));
        msg.setPot(potView);

        msg.setActionHistory(state.getActionHistory().stream()
                .map(a -> new ActionView(a.getPlayerId(), a.getType().name(),
                        a.getAmount(), state.getPhase().name()))
                .collect(Collectors.toList()));

        return msg;
    }
}
