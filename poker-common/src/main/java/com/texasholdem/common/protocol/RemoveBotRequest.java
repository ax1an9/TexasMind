package com.texasholdem.common.protocol;

public class RemoveBotRequest {
    private String roomId;
    private String botId;

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getBotId() { return botId; }
    public void setBotId(String botId) { this.botId = botId; }
}
