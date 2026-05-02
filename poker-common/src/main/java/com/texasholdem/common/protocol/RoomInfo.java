package com.texasholdem.common.protocol;

public class RoomInfo {
    private String roomId;
    private String name;
    private int currentPlayers;
    private int maxPlayers;
    private String status;

    public RoomInfo() {}

    public RoomInfo(String roomId, String name, int currentPlayers, int maxPlayers, String status) {
        this.roomId = roomId;
        this.name = name;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.status = status;
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getCurrentPlayers() { return currentPlayers; }
    public void setCurrentPlayers(int currentPlayers) { this.currentPlayers = currentPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
