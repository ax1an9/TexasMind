package com.texasholdem.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GameState {
    private final GamePhase phase;
    private final List<Card> board;
    private final List<PlayerState> players;
    private final PotInfo pot;
    private final List<Action> actionHistory;
    private final int dealerPosition;
    private final int currentPlayerPosition;
    private final int currentBet;
    private final int handNumber;
    private final int roundNumber;
    private final List<Card> deck;
    private final int deckIndex;

    public GameState(GamePhase phase, List<Card> board, List<PlayerState> players, PotInfo pot,
            List<Action> actionHistory, int dealerPosition, int currentPlayerPosition, int currentBet,
            int handNumber, int roundNumber, List<Card> deck, int deckIndex) {
        this.phase = phase;
        this.board = Collections.unmodifiableList(new ArrayList<Card>(board));
        this.players = Collections.unmodifiableList(new ArrayList<PlayerState>(players));
        this.pot = pot;
        this.actionHistory = Collections.unmodifiableList(new ArrayList<Action>(actionHistory));
        this.dealerPosition = dealerPosition;
        this.currentPlayerPosition = currentPlayerPosition;
        this.currentBet = currentBet;
        this.handNumber = handNumber;
        this.roundNumber = roundNumber;
        this.deck = Collections.unmodifiableList(new ArrayList<Card>(deck));
        this.deckIndex = deckIndex;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public List<Card> getBoard() {
        return board;
    }

    public List<PlayerState> getPlayers() {
        return players;
    }

    public PotInfo getPot() {
        return pot;
    }

    public List<Action> getActionHistory() {
        return actionHistory;
    }

    public int getDealerPosition() {
        return dealerPosition;
    }

    public int getCurrentPlayerPosition() {
        return currentPlayerPosition;
    }

    public int getCurrentBet() {
        return currentBet;
    }

    public int getHandNumber() {
        return handNumber;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public List<Card> getDeck() {
        return deck;
    }

    public int getDeckIndex() {
        return deckIndex;
    }

    public PlayerState getCurrentPlayer() {
        if (currentPlayerPosition < 0 || currentPlayerPosition >= players.size()) {
            return null;
        }
        return players.get(currentPlayerPosition);
    }
}