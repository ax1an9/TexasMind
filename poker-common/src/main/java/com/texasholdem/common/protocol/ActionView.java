package com.texasholdem.common.protocol;

public class ActionView {
    private String playerId;
    private String actionType;
    private int amount;
    private String phase;

    public ActionView() {}

    public ActionView(String playerId, String actionType, int amount, String phase) {
        this.playerId = playerId;
        this.actionType = actionType;
        this.amount = amount;
        this.phase = phase;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
}
