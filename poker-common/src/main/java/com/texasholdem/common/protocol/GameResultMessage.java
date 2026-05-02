package com.texasholdem.common.protocol;

import java.util.Map;

public class GameResultMessage extends ServerMessage {
    private String gameId;
    private String winnerSeatId;
    private int potAmount;
    private String handRank;
    private Map<String, Integer> chipChanges;

    public GameResultMessage() {
        super("GAME_RESULT");
    }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public String getWinnerSeatId() { return winnerSeatId; }
    public void setWinnerSeatId(String winnerSeatId) { this.winnerSeatId = winnerSeatId; }
    public int getPotAmount() { return potAmount; }
    public void setPotAmount(int potAmount) { this.potAmount = potAmount; }
    public String getHandRank() { return handRank; }
    public void setHandRank(String handRank) { this.handRank = handRank; }
    public Map<String, Integer> getChipChanges() { return chipChanges; }
    public void setChipChanges(Map<String, Integer> chipChanges) { this.chipChanges = chipChanges; }
}
