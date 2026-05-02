package com.texasholdem.common.protocol;

import java.util.List;

public class RoomStateMessage extends ServerMessage {
    private String roomId;
    private String roomName;
    private String hostId;
    private List<PlayerSlot> players;
    private boolean canStart;
    private int maxPlayers;

    public RoomStateMessage() {
        super("ROOM_STATE");
    }

    public RoomStateMessage(String roomId, String roomName, String hostId,
                            List<PlayerSlot> players, boolean canStart, int maxPlayers) {
        super("ROOM_STATE");
        this.roomId = roomId;
        this.roomName = roomName;
        this.hostId = hostId;
        this.players = players;
        this.canStart = canStart;
        this.maxPlayers = maxPlayers;
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public List<PlayerSlot> getPlayers() { return players; }
    public void setPlayers(List<PlayerSlot> players) { this.players = players; }
    public boolean isCanStart() { return canStart; }
    public void setCanStart(boolean canStart) { this.canStart = canStart; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public static class PlayerSlot {
        private String seatId;
        private boolean ready;
        private boolean ai;
        private boolean host;
        private String agentType; // "simple", "react", or null

        public PlayerSlot() {}

        public PlayerSlot(String seatId, boolean ready, boolean ai, boolean host) {
            this(seatId, ready, ai, host, null);
        }

        public PlayerSlot(String seatId, boolean ready, boolean ai, boolean host, String agentType) {
            this.seatId = seatId;
            this.ready = ready;
            this.ai = ai;
            this.host = host;
            this.agentType = agentType;
        }

        public String getSeatId() { return seatId; }
        public void setSeatId(String seatId) { this.seatId = seatId; }
        public boolean isReady() { return ready; }
        public void setReady(boolean ready) { this.ready = ready; }
        public boolean isAi() { return ai; }
        public void setAi(boolean ai) { this.ai = ai; }
        public boolean isHost() { return host; }
        public void setHost(boolean host) { this.host = host; }
        public String getAgentType() { return agentType; }
        public void setAgentType(String agentType) { this.agentType = agentType; }
    }
}
