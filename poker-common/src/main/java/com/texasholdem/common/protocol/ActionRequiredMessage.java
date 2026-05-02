package com.texasholdem.common.protocol;

import java.util.List;

public class ActionRequiredMessage extends ServerMessage {
    private String gameId;
    private String seatId;
    private int timeLimitMs;
    private List<String> validActions;
    private int minAmount;
    private int maxAmount;

    public ActionRequiredMessage() {
        super("ACTION_REQUIRED");
    }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public String getSeatId() { return seatId; }
    public void setSeatId(String seatId) { this.seatId = seatId; }
    public int getTimeLimitMs() { return timeLimitMs; }
    public void setTimeLimitMs(int timeLimitMs) { this.timeLimitMs = timeLimitMs; }
    public List<String> getValidActions() { return validActions; }
    public void setValidActions(List<String> validActions) { this.validActions = validActions; }
    public int getMinAmount() { return minAmount; }
    public void setMinAmount(int minAmount) { this.minAmount = minAmount; }
    public int getMaxAmount() { return maxAmount; }
    public void setMaxAmount(int maxAmount) { this.maxAmount = maxAmount; }
}
