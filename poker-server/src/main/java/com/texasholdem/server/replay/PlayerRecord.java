package com.texasholdem.server.replay;

public class PlayerRecord {
    private String seatId;
    private String playerName;
    private boolean aiAgent;
    private int buyIn;

    public String getSeatId() { return seatId; }
    public void setSeatId(String seatId) { this.seatId = seatId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public boolean isAiAgent() { return aiAgent; }
    public void setAiAgent(boolean aiAgent) { this.aiAgent = aiAgent; }
    public int getBuyIn() { return buyIn; }
    public void setBuyIn(int buyIn) { this.buyIn = buyIn; }
}
