package com.texasholdem.server.replay;

public class ActionRecord {
    private String seatId;
    private String actionType;
    private int amount;
    private String phase;
    private long timestampMs;

    public String getSeatId() { return seatId; }
    public void setSeatId(String seatId) { this.seatId = seatId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public long getTimestampMs() { return timestampMs; }
    public void setTimestampMs(long timestampMs) { this.timestampMs = timestampMs; }
}
