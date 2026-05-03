package com.texasholdem.common.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerStyleMessage extends ServerMessage {
    private String playerId;
    private String primaryStyle;
    private String secondaryStyle;
    private double confidence;
    private String tightness;
    private String aggressiveness;
}
