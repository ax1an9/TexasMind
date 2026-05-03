package com.texasholdem.server.stats;

import com.texasholdem.core.model.*;
import lombok.Data;

import java.util.List;

public class StatsCalculator {

    @Data
    public static class HandStatsUpdate {
        private boolean vpip = false;
        private boolean pfr = false;
        private boolean threeBetOpportunity = false;
        private boolean threeBet = false;
        private int aggressiveActions = 0;
        private int postFlopActions = 0;
        private boolean reachedShowdown = false;
        private boolean wonAtShowdown = false;
        private boolean facesCbet = false;
        private boolean foldedToCbet = false;
    }

    public static HandStatsUpdate analyzeHand(GameState state, String playerId) {
        HandStatsUpdate update = new HandStatsUpdate();
        List<Action> actions = state.getActionHistory();

        update.setVpip(hasVoluntaryEntry(actions, playerId));
        update.setPfr(hasPreflopRaise(actions, playerId));
        analyzeThreeBet(actions, playerId, update);
        analyzeAggression(actions, playerId, update);

        PlayerState player = findPlayer(state, playerId);
        boolean playerFolded = player != null && player.isFolded();
        boolean isShowdown = state.getPhase() == GamePhase.SETTLED && !playerFolded;
        update.setReachedShowdown(isShowdown && hasMultiplePlayersAtShowdown(state));

        if (update.isReachedShowdown()) {
            update.setWonAtShowdown(isWinner(state, playerId));
        }

        analyzeFoldToCbet(actions, playerId, update);
        return update;
    }

    private static boolean hasVoluntaryEntry(List<Action> actions, String playerId) {
        for (Action a : actions) {
            if (!a.getPlayerId().equals(playerId)) continue;
            return a.getType() == ActionType.CALL || a.getType() == ActionType.RAISE
                    || a.getType() == ActionType.BET || a.getType() == ActionType.ALL_IN;
        }
        return false;
    }

    private static boolean hasPreflopRaise(List<Action> actions, String playerId) {
        for (Action a : actions) {
            if (!a.getPlayerId().equals(playerId)) continue;
            if (a.getType() == ActionType.RAISE || a.getType() == ActionType.BET) return true;
            return false;
        }
        return false;
    }

    private static void analyzeThreeBet(List<Action> actions, String playerId, HandStatsUpdate update) {
        boolean playerFacesOpenRaise = false;
        for (Action a : actions) {
            if ((a.getType() == ActionType.RAISE || a.getType() == ActionType.BET)
                    && !a.getPlayerId().equals(playerId)) {
                playerFacesOpenRaise = true;
            }
            if (playerFacesOpenRaise && a.getPlayerId().equals(playerId)) {
                update.setThreeBetOpportunity(true);
                if (a.getType() == ActionType.RAISE || a.getType() == ActionType.BET) {
                    update.setThreeBet(true);
                }
                return;
            }
        }
    }

    private static void analyzeAggression(List<Action> actions, String playerId, HandStatsUpdate update) {
        for (Action a : actions) {
            if (!a.getPlayerId().equals(playerId)) continue;
            if (a.getType() == ActionType.RAISE || a.getType() == ActionType.BET
                    || a.getType() == ActionType.ALL_IN) {
                update.setAggressiveActions(update.getAggressiveActions() + 1);
            }
            if (a.getType() != ActionType.FOLD) {
                update.setPostFlopActions(update.getPostFlopActions() + 1);
            }
        }
    }

    private static void analyzeFoldToCbet(List<Action> actions, String playerId, HandStatsUpdate update) {
        String preflopRaiser = null;

        for (Action a : actions) {
            GamePhase phase = a.getPhase();
            if (phase == null) continue;

            if (phase == GamePhase.PRE_FLOP) {
                if (a.getType() == ActionType.RAISE || a.getType() == ActionType.BET) {
                    preflopRaiser = a.getPlayerId();
                }
            } else if (preflopRaiser != null && !preflopRaiser.equals(playerId)) {
                if ((a.getType() == ActionType.BET || a.getType() == ActionType.RAISE)
                        && a.getPlayerId().equals(preflopRaiser)) {
                    update.setFacesCbet(true);
                }
                if (update.isFacesCbet() && a.getPlayerId().equals(playerId)
                        && a.getType() == ActionType.FOLD) {
                    update.setFoldedToCbet(true);
                    return;
                }
            }
        }
    }

    private static PlayerState findPlayer(GameState state, String playerId) {
        for (PlayerState p : state.getPlayers()) {
            if (p.getSeatId().equals(playerId)) return p;
        }
        return null;
    }

    private static boolean hasMultiplePlayersAtShowdown(GameState state) {
        int active = 0;
        for (PlayerState p : state.getPlayers()) {
            if (!p.isFolded()) active++;
        }
        return active >= 2;
    }

    private static boolean isWinner(GameState state, String playerId) {
        PlayerState player = findPlayer(state, playerId);
        if (player == null || player.isFolded()) return false;
        int nonFolded = 0;
        for (PlayerState p : state.getPlayers()) {
            if (!p.isFolded()) nonFolded++;
        }
        return nonFolded == 1;
    }
}
