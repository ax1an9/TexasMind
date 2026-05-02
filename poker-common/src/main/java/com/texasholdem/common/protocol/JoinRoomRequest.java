package com.texasholdem.common.protocol;

public class JoinRoomRequest {
    private String roomId;
    private int seatPreference = -1;

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public int getSeatPreference() { return seatPreference; }
    public void setSeatPreference(int seatPreference) { this.seatPreference = seatPreference; }
}
