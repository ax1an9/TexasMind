package com.texasholdem.common.protocol;

public class RoomCreatedMessage extends ServerMessage {
    private String roomId;
    private String roomName;
    private String hostId;
    private java.util.List<RoomStateMessage.PlayerSlot> players;
    private boolean canStart;
    private int maxPlayers;

    public RoomCreatedMessage() {
        super("ROOM_CREATED");
    }

    public RoomCreatedMessage(String roomId, String roomName) {
        super("ROOM_CREATED");
        this.roomId = roomId;
        this.roomName = roomName;
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public java.util.List<RoomStateMessage.PlayerSlot> getPlayers() { return players; }
    public void setPlayers(java.util.List<RoomStateMessage.PlayerSlot> players) { this.players = players; }
    public boolean isCanStart() { return canStart; }
    public void setCanStart(boolean canStart) { this.canStart = canStart; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
}
