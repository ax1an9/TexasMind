package com.texasholdem.common.protocol;

import java.util.List;

public class SessionSummaryMessage extends ServerMessage {
    private String roomId;
    private int totalHands;
    private long durationMs;
    private List<PlayerSummary> players;

    public SessionSummaryMessage() {
        super("SESSION_SUMMARY");
    }

    public SessionSummaryMessage(String roomId, int totalHands, long durationMs, List<PlayerSummary> players) {
        super("SESSION_SUMMARY");
        this.roomId = roomId;
        this.totalHands = totalHands;
        this.durationMs = durationMs;
        this.players = players;
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public int getTotalHands() { return totalHands; }
    public void setTotalHands(int totalHands) { this.totalHands = totalHands; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public List<PlayerSummary> getPlayers() { return players; }
    public void setPlayers(List<PlayerSummary> players) { this.players = players; }

    public static class PlayerSummary {
        private String seatId;
        private int startingChips;
        private int finalChips;
        private int netChange;
        private boolean busted;

        public PlayerSummary() {}

        public PlayerSummary(String seatId, int startingChips, int finalChips, int netChange, boolean busted) {
            this.seatId = seatId;
            this.startingChips = startingChips;
            this.finalChips = finalChips;
            this.netChange = netChange;
            this.busted = busted;
        }

        public String getSeatId() { return seatId; }
        public void setSeatId(String seatId) { this.seatId = seatId; }
        public int getStartingChips() { return startingChips; }
        public void setStartingChips(int startingChips) { this.startingChips = startingChips; }
        public int getFinalChips() { return finalChips; }
        public void setFinalChips(int finalChips) { this.finalChips = finalChips; }
        public int getNetChange() { return netChange; }
        public void setNetChange(int netChange) { this.netChange = netChange; }
        public boolean isBusted() { return busted; }
        public void setBusted(boolean busted) { this.busted = busted; }
    }
}
