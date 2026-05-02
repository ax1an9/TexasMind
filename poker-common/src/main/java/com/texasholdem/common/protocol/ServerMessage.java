package com.texasholdem.common.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GameStateMessage.class, name = "GAME_STATE"),
    @JsonSubTypes.Type(value = ActionRequiredMessage.class, name = "ACTION_REQUIRED"),
    @JsonSubTypes.Type(value = HintResultMessage.class, name = "HINT_RESULT"),
    @JsonSubTypes.Type(value = GameResultMessage.class, name = "GAME_RESULT"),
    @JsonSubTypes.Type(value = RoomListMessage.class, name = "ROOM_LIST"),
    @JsonSubTypes.Type(value = RoomCreatedMessage.class, name = "ROOM_CREATED"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR")
})
public abstract class ServerMessage {
    private String type;

    protected ServerMessage() {}

    protected ServerMessage(String type) {
        this.type = type;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
