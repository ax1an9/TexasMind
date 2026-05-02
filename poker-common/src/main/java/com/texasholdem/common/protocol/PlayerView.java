package com.texasholdem.common.protocol;

import java.util.List;

public class PlayerView {
    private String seatId;
    private int chips;
    private boolean folded;
    private boolean allIn;
    private int roundContribution;
    private int totalContribution;
    private List<CardView> holeCards; // null for other players, populated for self
    private String handRank; // e.g. "FULL_HOUSE", shown at showdown
    private String handRankDisplay; // e.g. "Full House", human-readable
    private String agentType; // "simple", "react", or null for humans

    public PlayerView() {}

    public String getSeatId() { return seatId; }
    public void setSeatId(String seatId) { this.seatId = seatId; }
    public int getChips() { return chips; }
    public void setChips(int chips) { this.chips = chips; }
    public boolean isFolded() { return folded; }
    public void setFolded(boolean folded) { this.folded = folded; }
    public boolean isAllIn() { return allIn; }
    public void setAllIn(boolean allIn) { this.allIn = allIn; }
    public int getRoundContribution() { return roundContribution; }
    public void setRoundContribution(int roundContribution) { this.roundContribution = roundContribution; }
    public int getTotalContribution() { return totalContribution; }
    public void setTotalContribution(int totalContribution) { this.totalContribution = totalContribution; }
    public List<CardView> getHoleCards() { return holeCards; }
    public void setHoleCards(List<CardView> holeCards) { this.holeCards = holeCards; }
    public String getHandRank() { return handRank; }
    public void setHandRank(String handRank) { this.handRank = handRank; }
    public String getHandRankDisplay() { return handRankDisplay; }
    public void setHandRankDisplay(String handRankDisplay) { this.handRankDisplay = handRankDisplay; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
}
