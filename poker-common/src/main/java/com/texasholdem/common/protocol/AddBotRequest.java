package com.texasholdem.common.protocol;

public class AddBotRequest {
    private String roomId;
    private String agentType; // "simple" or "react"

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
}
