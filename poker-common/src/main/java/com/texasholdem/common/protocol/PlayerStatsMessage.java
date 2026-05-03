package com.texasholdem.common.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class PlayerStatsMessage extends ServerMessage {
    private String playerId;
    private String displayName;
    private int allTimeHands;
    private int recentHands;
    private int sessionHands;
    private Map<String, Double> publicStats;
    private Map<String, Double> privateStats;
}
