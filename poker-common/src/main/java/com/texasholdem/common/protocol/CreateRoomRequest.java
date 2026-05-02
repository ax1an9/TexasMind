package com.texasholdem.common.protocol;

public class CreateRoomRequest {
    private String roomName;
    private int maxPlayers = 6;
    private int smallBlind = 1;
    private int bigBlind = 2;
    private int startingChips = 200;

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public int getSmallBlind() { return smallBlind; }
    public void setSmallBlind(int smallBlind) { this.smallBlind = smallBlind; }
    public int getBigBlind() { return bigBlind; }
    public void setBigBlind(int bigBlind) { this.bigBlind = bigBlind; }
    public int getStartingChips() { return startingChips; }
    public void setStartingChips(int startingChips) { this.startingChips = startingChips; }
}
