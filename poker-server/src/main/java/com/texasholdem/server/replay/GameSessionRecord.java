package com.texasholdem.server.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameSessionRecord {
    private String sessionId;
    private String roomId;
    private String startTime;
    private String endTime;
    private List<PlayerRecord> players = new ArrayList<>();
    private List<HandRecord> hands = new ArrayList<>();
    private Map<String, Integer> finalChips = new HashMap<>();

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public List<PlayerRecord> getPlayers() { return players; }
    public void setPlayers(List<PlayerRecord> players) { this.players = players; }
    public List<HandRecord> getHands() { return hands; }
    public void setHands(List<HandRecord> hands) { this.hands = hands; }
    public Map<String, Integer> getFinalChips() { return finalChips; }
    public void setFinalChips(Map<String, Integer> finalChips) { this.finalChips = finalChips; }
}
