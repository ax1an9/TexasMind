package com.texasholdem.server.session;

public class PlayerConnection {
    private final String userId;
    private final String seatId;
    private final boolean aiAgent;
    private final String agentType; // "simple", "react", or null for humans

    public PlayerConnection(String userId, String seatId, boolean aiAgent) {
        this(userId, seatId, aiAgent, null);
    }

    public PlayerConnection(String userId, String seatId, boolean aiAgent, String agentType) {
        this.userId = userId;
        this.seatId = seatId;
        this.aiAgent = aiAgent;
        this.agentType = agentType;
    }

    public String getUserId() { return userId; }
    public String getSeatId() { return seatId; }
    public boolean isAiAgent() { return aiAgent; }
    public String getAgentType() { return agentType; }
}
