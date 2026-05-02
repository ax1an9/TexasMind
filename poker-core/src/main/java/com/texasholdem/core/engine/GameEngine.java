package com.texasholdem.core.engine;

import com.texasholdem.core.eval.HandEvaluator;
import com.texasholdem.core.eval.HandValue;
import com.texasholdem.core.model.Action;
import com.texasholdem.core.model.ActionType;
import com.texasholdem.core.model.Card;
import com.texasholdem.core.model.Deck;
import com.texasholdem.core.model.GameConfig;
import com.texasholdem.core.model.GamePhase;
import com.texasholdem.core.model.GameState;
import com.texasholdem.core.model.PlayerState;
import com.texasholdem.core.model.PotInfo;
import com.texasholdem.core.model.PotSlice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameEngine {
    private final HandEvaluator handEvaluator;

    public GameEngine() {
        this(new HandEvaluator());
    }

    public GameEngine(HandEvaluator handEvaluator) {
        this.handEvaluator = handEvaluator;
    }

    public GameState startNewHand(GameConfig config, List<PlayerState> seats, int dealerPosition, long seed,
            int handNumber) {
        if (seats == null || seats.size() < 2) {
            throw new IllegalArgumentException("At least two players are required");
        }
        if (seats.size() > config.getMaxPlayers()) {
            throw new IllegalArgumentException("Too many players for this table");
        }

        Deck deck = Deck.standardShuffled(seed);
        List<Card> shuffled = deck.getCards();
        int deckIndex = 0;

        List<PlayerState> players = new ArrayList<PlayerState>();
        for (PlayerState seat : seats) {
            List<Card> holeCards = new ArrayList<Card>();
            holeCards.add(shuffled.get(deckIndex++));
            holeCards.add(shuffled.get(deckIndex++));
            players.add(seat.resetForNewHand(holeCards));
        }

        int normalizedDealer = normalizePosition(dealerPosition, players.size());

        int smallBlindIndex;
        int bigBlindIndex;
        int firstToAct;

        if (players.size() == 2) {
            // Heads-up: dealer is SB, other player is BB. Dealer acts first preflop.
            smallBlindIndex = normalizedDealer;
            bigBlindIndex = nextActiveIndex(players, normalizedDealer);
            firstToAct = smallBlindIndex;
        } else {
            // 3+ players: SB is next after dealer, BB after SB. First to act is after BB.
            smallBlindIndex = nextActiveIndex(players, normalizedDealer);
            bigBlindIndex = nextActiveIndex(players, smallBlindIndex);
            firstToAct = nextActiveIndex(players, bigBlindIndex);
        }

        players.set(smallBlindIndex, postBlind(players.get(smallBlindIndex), config.getSmallBlind()));
        players.set(bigBlindIndex, postBlind(players.get(bigBlindIndex), config.getBigBlind()));

        // Blinds are forced bets, not voluntary actions — reset acted flags
        // so SB/BB get a proper turn to act preflop
        players.set(smallBlindIndex, players.get(smallBlindIndex).withActedThisRound(false));
        players.set(bigBlindIndex, players.get(bigBlindIndex).withActedThisRound(false));

        PotInfo pot = PotInfo.fromPlayers(players);
        return new GameState(GamePhase.PRE_FLOP, Collections.<Card>emptyList(), players, pot,
                Collections.<Action>emptyList(), normalizedDealer, firstToAct, config.getBigBlind(), handNumber, 1,
                shuffled, deckIndex);
    }

    public GameState applyAction(GameState state, Action action) {
        if (state.getPhase() == GamePhase.SETTLED) {
            throw new IllegalStateException("Hand is already settled");
        }
        int currentIndex = state.getCurrentPlayerPosition();
        if (currentIndex < 0) {
            throw new IllegalStateException("No active player to act");
        }

        PlayerState actor = state.getPlayers().get(currentIndex);
        if (!actor.getSeatId().equals(action.getPlayerId())) {
            throw new IllegalArgumentException("Action does not match current player");
        }
        if (actor.isFolded() || actor.isAllIn()) {
            throw new IllegalStateException("Actor cannot take an action");
        }

        List<PlayerState> players = new ArrayList<PlayerState>(state.getPlayers());
        List<Action> history = new ArrayList<Action>(state.getActionHistory());
        history.add(action);

        int currentBet = state.getCurrentBet();
        boolean raised = false;
        PlayerState updatedActor;

        switch (action.getType()) {
            case FOLD:
                updatedActor = actor.fold();
                break;
            case CHECK:
                if (actor.getRoundContribution() != currentBet) {
                    throw new IllegalArgumentException("Cannot check when facing a bet");
                }
                updatedActor = actor.withActedThisRound(true);
                break;
            case CALL:
                updatedActor = commitToTarget(actor, currentBet);
                break;
            case BET:
            case RAISE:
                if (action.getAmount() <= currentBet) {
                    throw new IllegalArgumentException("Bet or raise must exceed the current bet");
                }
                updatedActor = commitToTarget(actor, action.getAmount());
                raised = updatedActor.getRoundContribution() > currentBet;
                break;
            case ALL_IN:
                updatedActor = commitToTarget(actor, actor.getRoundContribution() + actor.getChips());
                raised = updatedActor.getRoundContribution() > currentBet;
                break;
            default:
                throw new IllegalStateException("Unsupported action type: " + action.getType());
        }

        players.set(currentIndex, updatedActor);
        if (raised) {
            players = resetActedFlags(players, currentIndex);
            currentBet = Math.max(currentBet, updatedActor.getRoundContribution());
        }

        if (countPlayersStillIn(players) <= 1) {
            return settleByFold(state, players, history);
        }

        if (isBettingRoundComplete(players, currentBet)) {
            return advanceStreet(state, players, history);
        }

        int nextPlayer = nextActiveIndex(players, currentIndex);
        PotInfo pot = PotInfo.fromPlayers(players);
        return new GameState(state.getPhase(), state.getBoard(), players, pot, history, state.getDealerPosition(),
                nextPlayer, currentBet, state.getHandNumber(), state.getRoundNumber(), state.getDeck(),
                state.getDeckIndex());
    }

    private GameState settleByFold(GameState state, List<PlayerState> players, List<Action> history) {
        int winnerIndex = -1;
        for (int i = 0; i < players.size(); i++) {
            if (!players.get(i).isFolded()) {
                winnerIndex = i;
                break;
            }
        }

        int potAmount = PotInfo.fromPlayers(players).getTotalPot();
        List<PlayerState> settledPlayers = new ArrayList<PlayerState>(players);
        PlayerState winner = settledPlayers.get(winnerIndex);
        settledPlayers.set(winnerIndex, winner.withChips(winner.getChips() + potAmount));

        return new GameState(GamePhase.SETTLED, state.getBoard(), settledPlayers, PotInfo.empty(), history,
                state.getDealerPosition(), -1, 0, state.getHandNumber(), state.getRoundNumber(), state.getDeck(),
                state.getDeckIndex());
    }

    private GameState advanceStreet(GameState state, List<PlayerState> players, List<Action> history) {
        GamePhase nextPhase = nextPhase(state.getPhase());
        if (nextPhase == GamePhase.SHOWDOWN) {
            return settleShowdown(state, players, history);
        }

        List<Card> board = new ArrayList<Card>(state.getBoard());
        int deckIndex = state.getDeckIndex();
        if (nextPhase == GamePhase.FLOP) {
            board.add(state.getDeck().get(deckIndex++));
            board.add(state.getDeck().get(deckIndex++));
            board.add(state.getDeck().get(deckIndex++));
        } else if (nextPhase == GamePhase.TURN || nextPhase == GamePhase.RIVER) {
            board.add(state.getDeck().get(deckIndex++));
        }

        List<PlayerState> resetPlayers = resetForNewRound(players);
        int currentPlayer = nextActiveIndex(resetPlayers, state.getDealerPosition());
        PotInfo pot = PotInfo.fromPlayers(resetPlayers);

        // If no player can act (e.g. all-in), keep advancing until settled
        if (currentPlayer < 0) {
            GameState intermediate = new GameState(nextPhase, board, resetPlayers, pot, history,
                    state.getDealerPosition(), -1, 0, state.getHandNumber(), state.getRoundNumber() + 1,
                    state.getDeck(), deckIndex);
            return advanceStreet(intermediate, resetPlayers, history);
        }

        return new GameState(nextPhase, board, resetPlayers, pot, history, state.getDealerPosition(), currentPlayer, 0,
                state.getHandNumber(), state.getRoundNumber() + 1, state.getDeck(), deckIndex);
    }

    private GameState settleShowdown(GameState state, List<PlayerState> players, List<Action> history) {
        PotInfo pot = PotInfo.fromPlayers(players);
        Map<String, Integer> winnings = new LinkedHashMap<String, Integer>();
        for (PlayerState player : players) {
            winnings.put(player.getSeatId(), 0);
        }

        for (PotSlice slice : buildSlices(pot)) {
            List<PlayerState> eligible = new ArrayList<PlayerState>();
            for (PlayerState player : players) {
                if (!player.isFolded() && slice.getEligibleSeatIds().contains(player.getSeatId())) {
                    eligible.add(player);
                }
            }
            if (eligible.isEmpty()) {
                continue;
            }

            Map<PlayerState, HandValue> values = new HashMap<PlayerState, HandValue>();
            HandValue best = null;
            for (PlayerState player : eligible) {
                List<Card> allCards = new ArrayList<Card>(state.getBoard());
                allCards.addAll(player.getHoleCards());
                HandValue value = handEvaluator.evaluateBest(allCards);
                values.put(player, value);
                if (best == null || value.compareTo(best) > 0) {
                    best = value;
                }
            }

            List<PlayerState> winners = new ArrayList<PlayerState>();
            for (PlayerState player : eligible) {
                if (values.get(player).compareTo(best) == 0) {
                    winners.add(player);
                }
            }

            int amount = slice.getAmount();
            int share = amount / winners.size();
            int remainder = amount % winners.size();
            for (int i = 0; i < winners.size(); i++) {
                PlayerState winner = winners.get(i);
                int award = share + (i < remainder ? 1 : 0);
                winnings.put(winner.getSeatId(), winnings.get(winner.getSeatId()) + award);
            }
        }

        List<PlayerState> settledPlayers = new ArrayList<PlayerState>();
        for (PlayerState player : players) {
            settledPlayers.add(player.withChips(player.getChips() + winnings.get(player.getSeatId())));
        }

        return new GameState(GamePhase.SETTLED, state.getBoard(), settledPlayers, PotInfo.empty(), history,
                state.getDealerPosition(), -1, 0, state.getHandNumber(), state.getRoundNumber(), state.getDeck(),
                state.getDeckIndex());
    }

    private List<PotSlice> buildSlices(PotInfo pot) {
        List<PotSlice> slices = new ArrayList<PotSlice>();
        if (pot.getMainPot() > 0) {
            slices.add(pot.getMainPotSlice());
        }
        slices.addAll(pot.getSidePots());
        return slices;
    }

    private PlayerState postBlind(PlayerState player, int blindAmount) {
        return player.commit(blindAmount, blindAmount);
    }

    private PlayerState commitToTarget(PlayerState player, int targetRoundContribution) {
        int toPay = Math.max(0, targetRoundContribution - player.getRoundContribution());
        return player.commit(toPay, targetRoundContribution);
    }

    private List<PlayerState> resetForNewRound(List<PlayerState> players) {
        List<PlayerState> reset = new ArrayList<PlayerState>();
        for (PlayerState player : players) {
            reset.add(player.rebuild(player.getChips(), player.getHoleCards(), player.isFolded(), player.isAllIn(), 0,
                    player.getTotalContribution(), false));
        }
        return reset;
    }

    private List<PlayerState> resetActedFlags(List<PlayerState> players, int actorIndex) {
        List<PlayerState> reset = new ArrayList<PlayerState>();
        for (int i = 0; i < players.size(); i++) {
            PlayerState player = players.get(i);
            if (i == actorIndex) {
                reset.add(player.withActedThisRound(true));
            } else if (!player.isFolded() && !player.isAllIn()) {
                reset.add(player.withActedThisRound(false));
            } else {
                reset.add(player);
            }
        }
        return reset;
    }

    private boolean isBettingRoundComplete(List<PlayerState> players, int currentBet) {
        for (PlayerState player : players) {
            if (player.isFolded() || player.isAllIn()) {
                continue;
            }
            if (!player.isActedThisRound() || player.getRoundContribution() != currentBet) {
                return false;
            }
        }
        return true;
    }

    private int countPlayersStillIn(List<PlayerState> players) {
        int count = 0;
        for (PlayerState player : players) {
            if (!player.isFolded()) {
                count++;
            }
        }
        return count;
    }

    private int nextActiveIndex(List<PlayerState> players, int startIndex) {
        int size = players.size();
        for (int offset = 1; offset <= size; offset++) {
            int index = (startIndex + offset) % size;
            PlayerState player = players.get(index);
            if (!player.isFolded() && !player.isAllIn()) {
                return index;
            }
        }
        return -1;
    }

    private int normalizePosition(int position, int size) {
        int normalized = position % size;
        return normalized < 0 ? normalized + size : normalized;
    }

    private GamePhase nextPhase(GamePhase phase) {
        switch (phase) {
            case PRE_FLOP:
                return GamePhase.FLOP;
            case FLOP:
                return GamePhase.TURN;
            case TURN:
                return GamePhase.RIVER;
            case RIVER:
                return GamePhase.SHOWDOWN;
            default:
                return GamePhase.SHOWDOWN;
        }
    }
}