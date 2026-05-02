package com.texasholdem.common.protocol;

import java.util.List;

public class GameStateMessage extends ServerMessage {
    private String gameId;
    private String roomId;
    private String phase;
    private List<CardView> board;
    private List<PlayerView> players;
    private PotView pot;
    private List<ActionView> actionHistory;
    private String currentPlayerId;
    private int currentBet;

    public GameStateMessage() {
        super("GAME_STATE");
    }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public List<CardView> getBoard() { return board; }
    public void setBoard(List<CardView> board) { this.board = board; }
    public List<PlayerView> getPlayers() { return players; }
    public void setPlayers(List<PlayerView> players) { this.players = players; }
    public PotView getPot() { return pot; }
    public void setPot(PotView pot) { this.pot = pot; }
    public List<ActionView> getActionHistory() { return actionHistory; }
    public void setActionHistory(List<ActionView> actionHistory) { this.actionHistory = actionHistory; }
    public String getCurrentPlayerId() { return currentPlayerId; }
    public void setCurrentPlayerId(String currentPlayerId) { this.currentPlayerId = currentPlayerId; }
    public int getCurrentBet() { return currentBet; }
    public void setCurrentBet(int currentBet) { this.currentBet = currentBet; }
}
