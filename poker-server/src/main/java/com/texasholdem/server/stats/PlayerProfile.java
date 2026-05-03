package com.texasholdem.server.stats;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "player_profiles")
@Data
public class PlayerProfile {
    @Id
    private String id;
    private String displayName;
    private String createdAt;
    private String updatedAt;
    private StatsWindow allTime = new StatsWindow();
    private StatsWindow recent = new StatsWindow();
    private StatsWindow currentSession = new StatsWindow();

    {
        recent.setWindowSize(500);
    }

    @Data
    public static class StatsWindow {
        private int windowSize = 0;
        private int handsPlayed = 0;
        private List<String> handIds = new ArrayList<>();
        private StatEntry vpip = new StatEntry();
        private StatEntry pfr = new StatEntry();
        private StatEntry threeBet = new StatEntry();
        private StatEntry af = new StatEntry();
        private StatEntry wtsd = new StatEntry();
        private StatEntry wsd = new StatEntry();
        private StatEntry foldToCbet = new StatEntry();
        private int aggressiveActions = 0;
    }
}
