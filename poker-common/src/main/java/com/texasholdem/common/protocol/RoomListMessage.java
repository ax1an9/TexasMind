package com.texasholdem.common.protocol;

import java.util.List;

public class RoomListMessage extends ServerMessage {
    private List<RoomInfo> rooms;

    public RoomListMessage() {
        super("ROOM_LIST");
    }

    public RoomListMessage(List<RoomInfo> rooms) {
        super("ROOM_LIST");
        this.rooms = rooms;
    }

    public List<RoomInfo> getRooms() { return rooms; }
    public void setRooms(List<RoomInfo> rooms) { this.rooms = rooms; }
}
