package com.texasholdem.common.protocol;

public class PlayerActionRequest {
    private String roomId;
    private String action;
    private int amount;

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}
