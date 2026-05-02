package com.texasholdem.server.replay;

import java.util.List;
import java.util.Map;

public class HandRecord {
    private int handNumber;
    private String dealerSeatId;
    private Map<String, List<String>> holeCards;
    private List<String> board;
    private List<ActionRecord> actions;
    private int totalPot;
    private String winnerSeatId;
    private String winnerHandRank;
    private String startTime;
    private String endTime;

    public int getHandNumber() { return handNumber; }
    public void setHandNumber(int handNumber) { this.handNumber = handNumber; }
    public String getDealerSeatId() { return dealerSeatId; }
    public void setDealerSeatId(String dealerSeatId) { this.dealerSeatId = dealerSeatId; }
    public Map<String, List<String>> getHoleCards() { return holeCards; }
    public void setHoleCards(Map<String, List<String>> holeCards) { this.holeCards = holeCards; }
    public List<String> getBoard() { return board; }
    public void setBoard(List<String> board) { this.board = board; }
    public List<ActionRecord> getActions() { return actions; }
    public void setActions(List<ActionRecord> actions) { this.actions = actions; }
    public int getTotalPot() { return totalPot; }
    public void setTotalPot(int totalPot) { this.totalPot = totalPot; }
    public String getWinnerSeatId() { return winnerSeatId; }
    public void setWinnerSeatId(String winnerSeatId) { this.winnerSeatId = winnerSeatId; }
    public String getWinnerHandRank() { return winnerHandRank; }
    public void setWinnerHandRank(String winnerHandRank) { this.winnerHandRank = winnerHandRank; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}
