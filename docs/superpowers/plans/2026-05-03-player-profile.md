# Player Profile System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a historical player profile system that tracks poker statistics (VPIP, PFR, 3Bet, AF, WTSD, W$SD, Fold to Cbet) computed after each hand, with in-game HUD and profile page.

**Architecture:** Stats are computed post-hand in `GameRoom.handleSettled()`, stored in MongoDB via Spring Data, and queried via STOMP WebSocket. Frontend adds a HUD overlay on the Player component and a new Profile page.

**Tech Stack:** Java 8, Spring Boot 2.7.18, Spring Data MongoDB, Lombok, React 19, Vite 8, CSS Modules, STOMP over SockJS

---

## File Structure

### Backend (poker-server + poker-common)

| File | Responsibility |
|------|---------------|
| `pom.xml` (parent) | Add Lombok dependency |
| `poker-common/pom.xml` | Add Lombok dependency |
| `poker-server/pom.xml` | Add `spring-boot-starter-data-mongodb` + Lombok |
| `poker-server/application.properties` | MongoDB connection URI |
| `docker-compose.yml` | Add MongoDB service |
| `stats/StatEntry.java` | Embedded stat value object (Lombok @Data) |
| `stats/PlayerProfile.java` | MongoDB document (Lombok @Data) |
| `stats/PlayerProfileRepository.java` | Spring Data MongoDB repository |
| `stats/PlayerStatsService.java` | Core stats computation and persistence |
| `stats/StatsCalculator.java` | Pure logic for computing stats from a hand |
| `protocol/PlayerStatsMessage.java` | WebSocket message DTO (Lombok @Data) |
| `protocol/PlayerStyleMessage.java` | WebSocket message DTO (Lombok @Data) |
| `protocol/PlayerStatsRequest.java` | Request DTO (Lombok @Data) |
| `ServerMessage.java` | Register new message subtypes |
| `handler/ClientMessageHandler.java` | Add STOMP mappings for stats queries |
| `room/GameRoom.java` | Inject PlayerStatsService, call in handleSettled() |
| `room/RoomManager.java` | Pass PlayerStatsService to GameRoom |

### Frontend (poker-web)

| File | Responsibility |
|------|---------------|
| `src/components/PlayerHUD.jsx` | Compact stats overlay on player seat |
| `src/components/PlayerHUD.module.css` | HUD styles |
| `src/pages/Profile.jsx` | Full player profile page |
| `src/pages/Profile.module.css` | Profile page styles |
| `src/hooks/usePlayerStats.js` | Custom hook for stats subscription |
| `src/utils/playerStyle.js` | Style classification logic |
| `src/components/Player.jsx` | Add HUD integration |
| `src/App.jsx` | Add profile page state |

---

## Phase 1: Infrastructure (Lombok + MongoDB)

### Task 1: Add Lombok and MongoDB Dependencies

**Files:**
- Modify: `pom.xml` (parent)
- Modify: `poker-common/pom.xml`
- Modify: `poker-server/pom.xml`
- Modify: `poker-server/src/main/resources/application.properties`
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add Lombok to parent pom.xml**

Add inside `<properties>`:
```xml
<lombok.version>1.18.30</lombok.version>
```

Add inside `<dependencyManagement><dependencies>`:
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>${lombok.version}</version>
    <scope>provided</scope>
</dependency>
```

Add inside `<build><pluginManagement><plugins>` (after maven-compiler-plugin):
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <source>${maven.compiler.source}</source>
        <target>${maven.compiler.target}</target>
        <encoding>${project.build.sourceEncoding}</encoding>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

- [ ] **Step 2: Add Lombok to poker-common/pom.xml**

Add inside `<dependencies>`:
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

- [ ] **Step 3: Add Lombok and MongoDB to poker-server/pom.xml**

Add inside `<dependencies>`:
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

- [ ] **Step 4: Configure MongoDB in application.properties**

Replace `poker-server/src/main/resources/application.properties`:
```properties
server.port=8080
spring.data.mongodb.uri=mongodb://localhost:27017/poker
```

- [ ] **Step 5: Add MongoDB to docker-compose.yml**

Add this service block before `agent:`:
```yaml
  mongo:
    image: mongo:7
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
```

Add to the `volumes:` section:
```yaml
  mongo-data:
```

- [ ] **Step 6: Verify compilation**

Run: `cd /mnt/d/workspace/texas && mvn compile -pl poker-server -am`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add pom.xml poker-common/pom.xml poker-server/pom.xml poker-server/src/main/resources/application.properties docker-compose.yml
git commit -m "infra: add Lombok and MongoDB dependencies"
```

---

## Phase 2: Backend Core (Entities + Service)

### Task 2: Create PlayerProfile Entity and Repository

**Files:**
- Create: `poker-server/src/main/java/com/texasholdem/server/stats/StatEntry.java`
- Create: `poker-server/src/main/java/com/texasholdem/server/stats/PlayerProfile.java`
- Create: `poker-server/src/main/java/com/texasholdem/server/stats/PlayerProfileRepository.java`

- [ ] **Step 1: Create StatEntry.java**

```java
package com.texasholdem.server.stats;

import lombok.Data;

@Data
public class StatEntry {
    private double value = 0.0;
    private int opportunities = 0;
    private int count = 0;

    public void increment() {
        this.count++;
        this.opportunities++;
        this.value = (double) count / opportunities;
    }

    public void addOpportunity() {
        this.opportunities++;
        this.value = opportunities > 0 ? (double) count / opportunities : 0.0;
    }
}
```

- [ ] **Step 2: Create PlayerProfile.java**

```java
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
```

- [ ] **Step 3: Create PlayerProfileRepository.java**

```java
package com.texasholdem.server.stats;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerProfileRepository extends MongoRepository<PlayerProfile, String> {
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd /mnt/d/workspace/texas && mvn compile -pl poker-server -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add poker-server/src/main/java/com/texasholdem/server/stats/
git commit -m "feat: add PlayerProfile entity with Lombok and MongoDB repository"
```

---

### Task 3: Create StatsCalculator (Pure Logic)

**Files:**
- Create: `poker-server/src/main/java/com/texasholdem/server/stats/StatsCalculator.java`

- [ ] **Step 1: Create StatsCalculator.java**

```java
package com.texasholdem.server.stats;

import com.texasholdem.core.model.*;
import lombok.Data;

import java.util.List;

public class StatsCalculator {

    @Data
    public static class HandStatsUpdate {
        private boolean vpip = false;
        private boolean pfr = false;
        private boolean threeBetOpportunity = false;
        private boolean threeBet = false;
        private int aggressiveActions = 0;
        private int postFlopActions = 0;
        private boolean reachedShowdown = false;
        private boolean wonAtShowdown = false;
        private boolean facesCbet = false;
        private boolean foldedToCbet = false;
    }

    public static HandStatsUpdate analyzeHand(GameState state, String playerId) {
        HandStatsUpdate update = new HandStatsUpdate();
        List<Action> actions = state.getActionHistory();

        update.setVpip(hasVoluntaryEntry(actions, playerId));
        update.setPfr(hasPreflopRaise(actions, playerId));
        analyzeThreeBet(actions, playerId, update);
        analyzeAggression(actions, playerId, update);

        PlayerState player = findPlayer(state, playerId);
        boolean playerFolded = player != null && player.isFolded();
        boolean isShowdown = state.getPhase() == GamePhase.SETTLED && !playerFolded;
        update.setReachedShowdown(isShowdown && hasMultiplePlayersAtShowdown(state));

        if (update.isReachedShowdown()) {
            update.setWonAtShowdown(isWinner(state, playerId));
        }

        analyzeFoldToCbet(actions, playerId, update);
        return update;
    }

    private static boolean hasVoluntaryEntry(List<Action> actions, String playerId) {
        for (Action a : actions) {
            if (!a.getPlayerId().equals(playerId)) continue;
            return a.getType() == ActionType.CALL || a.getType() == ActionType.RAISE
                    || a.getType() == ActionType.BET || a.getType() == ActionType.ALL_IN;
        }
        return false;
    }

    private static boolean hasPreflopRaise(List<Action> actions, String playerId) {
        for (Action a : actions) {
            if (!a.getPlayerId().equals(playerId)) continue;
            if (a.getType() == ActionType.RAISE || a.getType() == ActionType.BET) return true;
            return false;
        }
        return false;
    }

    private static void analyzeThreeBet(List<Action> actions, String playerId, HandStatsUpdate update) {
        boolean playerFacesOpenRaise = false;
        for (Action a : actions) {
            if ((a.getType() == ActionType.RAISE || a.getType() == ActionType.BET)
                    && !a.getPlayerId().equals(playerId)) {
                playerFacesOpenRaise = true;
            }
            if (playerFacesOpenRaise && a.getPlayerId().equals(playerId)) {
                update.setThreeBetOpportunity(true);
                if (a.getType() == ActionType.RAISE || a.getType() == ActionType.BET) {
                    update.setThreeBet(true);
                }
                return;
            }
        }
    }

    private static void analyzeAggression(List<Action> actions, String playerId, HandStatsUpdate update) {
        for (Action a : actions) {
            if (!a.getPlayerId().equals(playerId)) continue;
            if (a.getType() == ActionType.RAISE || a.getType() == ActionType.BET
                    || a.getType() == ActionType.ALL_IN) {
                update.setAggressiveActions(update.getAggressiveActions() + 1);
            }
            if (a.getType() != ActionType.FOLD) {
                update.setPostFlopActions(update.getPostFlopActions() + 1);
            }
        }
    }

    private static void analyzeFoldToCbet(List<Action> actions, String playerId, HandStatsUpdate update) {
        boolean preflopPhase = true;
        String preflopRaiser = null;

        for (int i = 0; i < actions.size(); i++) {
            Action a = actions.get(i);
            if (preflopPhase) {
                if (a.getType() == ActionType.RAISE || a.getType() == ActionType.BET) {
                    preflopRaiser = a.getPlayerId();
                }
                if (i > 0 && a.getPlayerId().equals(actions.get(0).getPlayerId())
                        && !a.getPlayerId().equals(actions.get(i - 1).getPlayerId())) {
                    preflopPhase = false;
                }
            }
            if (!preflopPhase && preflopRaiser != null && !preflopRaiser.equals(playerId)) {
                if ((a.getType() == ActionType.BET || a.getType() == ActionType.RAISE)
                        && a.getPlayerId().equals(preflopRaiser)) {
                    update.setFacesCbet(true);
                }
                if (update.isFacesCbet() && a.getPlayerId().equals(playerId)
                        && a.getType() == ActionType.FOLD) {
                    update.setFoldedToCbet(true);
                    return;
                }
            }
        }
    }

    private static PlayerState findPlayer(GameState state, String playerId) {
        for (PlayerState p : state.getPlayers()) {
            if (p.getSeatId().equals(playerId)) return p;
        }
        return null;
    }

    private static boolean hasMultiplePlayersAtShowdown(GameState state) {
        int active = 0;
        for (PlayerState p : state.getPlayers()) {
            if (!p.isFolded()) active++;
        }
        return active >= 2;
    }

    private static boolean isWinner(GameState state, String playerId) {
        PlayerState player = findPlayer(state, playerId);
        if (player == null || player.isFolded()) return false;
        int nonFolded = 0;
        for (PlayerState p : state.getPlayers()) {
            if (!p.isFolded()) nonFolded++;
        }
        return nonFolded == 1;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /mnt/d/workspace/texas && mvn compile -pl poker-server -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/main/java/com/texasholdem/server/stats/StatsCalculator.java
git commit -m "feat: add StatsCalculator for post-hand stats computation"
```

---

### Task 4: Create PlayerStatsService

**Files:**
- Create: `poker-server/src/main/java/com/texasholdem/server/stats/PlayerStatsService.java`

- [ ] **Step 1: Create PlayerStatsService.java**

```java
package com.texasholdem.server.stats;

import com.texasholdem.core.model.*;
import com.texasholdem.server.session.PlayerConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class PlayerStatsService {
    private final PlayerProfileRepository repository;

    public PlayerStatsService(PlayerProfileRepository repository) {
        this.repository = repository;
    }

    public void processHand(String sessionId, int handNumber, GameState state,
                            Map<String, PlayerConnection> connections) {
        for (PlayerState player : state.getPlayers()) {
            String playerId = player.getSeatId();
            PlayerConnection conn = connections.get(playerId);
            if (conn != null && conn.isAiAgent()) continue;

            PlayerProfile profile = repository.findById(playerId).orElseGet(() -> {
                PlayerProfile p = new PlayerProfile();
                p.setId(playerId);
                p.setDisplayName(playerId);
                p.setCreatedAt(Instant.now().toString());
                return p;
            });

            StatsCalculator.HandStatsUpdate update = StatsCalculator.analyzeHand(state, playerId);
            String handId = sessionId + "_" + handNumber;

            applyUpdate(profile.getAllTime(), update, handId);
            applyUpdate(profile.getRecent(), update, handId);
            applyUpdate(profile.getCurrentSession(), update, handId);

            profile.setUpdatedAt(Instant.now().toString());
            repository.save(profile);
        }
    }

    private void applyUpdate(PlayerProfile.StatsWindow window,
                             StatsCalculator.HandStatsUpdate update, String handId) {
        window.setHandsPlayed(window.getHandsPlayed() + 1);

        // VPIP
        if (update.isVpip()) window.getVpip().increment();
        else window.getVpip().addOpportunity();

        // PFR
        if (update.isPfr()) window.getPfr().increment();
        else window.getPfr().addOpportunity();

        // 3Bet
        if (update.isThreeBetOpportunity()) {
            if (update.isThreeBet()) window.getThreeBet().increment();
            else window.getThreeBet().addOpportunity();
        }

        // AF
        if (update.getPostFlopActions() > 0) {
            window.setAggressiveActions(window.getAggressiveActions() + update.getAggressiveActions());
            StatEntry af = window.getAf();
            af.setCount(window.getAggressiveActions());
            af.setOpportunities(af.getOpportunities() + update.getPostFlopActions());
            af.setValue(af.getOpportunities() > 0
                    ? (double) af.getCount() / af.getOpportunities() : 0.0);
        }

        // WTSD
        if (update.isReachedShowdown()) window.getWtsd().increment();
        else window.getWtsd().addOpportunity();

        // W$SD
        if (update.isReachedShowdown()) {
            if (update.isWonAtShowdown()) window.getWsd().increment();
            else window.getWsd().addOpportunity();
        }

        // Fold to Cbet
        if (update.isFacesCbet()) {
            if (update.isFoldedToCbet()) window.getFoldToCbet().increment();
            else window.getFoldToCbet().addOpportunity();
        }

        window.getHandIds().add(handId);
        if (window.getWindowSize() > 0 && window.getHandIds().size() > window.getWindowSize()) {
            window.getHandIds().remove(0);
        }
    }

    public PlayerProfile getProfile(String playerId) {
        return repository.findById(playerId).orElse(null);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /mnt/d/workspace/texas && mvn compile -pl poker-server -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/main/java/com/texasholdem/server/stats/PlayerStatsService.java
git commit -m "feat: add PlayerStatsService for post-hand stats processing"
```

---

### Task 5: Integrate PlayerStatsService into GameRoom

**Files:**
- Modify: `poker-server/src/main/java/com/texasholdem/server/room/RoomManager.java:25-32`
- Modify: `poker-server/src/main/java/com/texasholdem/server/room/GameRoom.java:50-61,312-325`

- [ ] **Step 1: Add PlayerStatsService to RoomManager**

In `RoomManager.java`, add import and field, update constructor:

```java
import com.texasholdem.server.stats.PlayerStatsService;
```

Add field after `replayRecorder`:
```java
private final PlayerStatsService statsService;
```

Update constructor to accept and pass it:
```java
public RoomManager(BroadcastService broadcastService, ReplayRecorder replayRecorder,
                   PlayerStatsService statsService,
                   BuiltinAgent simpleAgent,
                   @Autowired(required = false) @Qualifier("grpcAgent") BuiltinAgent grpcAgent) {
    this.broadcastService = broadcastService;
    this.replayRecorder = replayRecorder;
    this.statsService = statsService;
    this.simpleAgent = simpleAgent;
    this.grpcAgent = grpcAgent;
}
```

Update `createRoom` to pass statsService:
```java
GameRoom room = new GameRoom(roomId, name, config, broadcastService, replayRecorder,
        statsService, simpleAgent, grpcAgent);
```

- [ ] **Step 2: Add PlayerStatsService to GameRoom**

In `GameRoom.java`, add import:
```java
import com.texasholdem.server.stats.PlayerStatsService;
```

Add field after `replayRecorder`:
```java
private final PlayerStatsService statsService;
```

Update constructor signature (add `PlayerStatsService statsService` after `ReplayRecorder replayRecorder`):
```java
public GameRoom(String roomId, String name, GameConfig gameConfig,
                BroadcastService broadcastService, ReplayRecorder replayRecorder,
                PlayerStatsService statsService,
                BuiltinAgent simpleAgent, BuiltinAgent grpcAgent) {
    this.roomId = roomId;
    this.name = name;
    this.gameConfig = gameConfig;
    this.broadcastService = broadcastService;
    this.replayRecorder = replayRecorder;
    this.statsService = statsService;
    this.simpleAgent = simpleAgent;
    this.grpcAgent = grpcAgent;
    this.aiExecutor = Executors.newCachedThreadPool();
}
```

In `handleSettled()`, after line 324 (`replayRecorder.onHandCompleted(...)`), add:
```java
if (statsService != null) {
    try {
        statsService.processHand(sessionId, handNumber, currentGameState, connections);
    } catch (Exception e) {
        log.warn("Failed to process player stats: {}", e.getMessage());
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd /mnt/d/workspace/texas && mvn compile -pl poker-server -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add poker-server/src/main/java/com/texasholdem/server/room/RoomManager.java poker-server/src/main/java/com/texasholdem/server/room/GameRoom.java
git commit -m "feat: integrate PlayerStatsService into GameRoom.handleSettled()"
```

---

## Phase 3: STOMP API Layer

### Task 6: Create Stats Message DTOs and Register in ServerMessage

**Files:**
- Create: `poker-common/src/main/java/com/texasholdem/common/protocol/PlayerStatsMessage.java`
- Create: `poker-common/src/main/java/com/texasholdem/common/protocol/PlayerStyleMessage.java`
- Create: `poker-common/src/main/java/com/texasholdem/common/protocol/PlayerStatsRequest.java`
- Modify: `poker-common/src/main/java/com/texasholdem/common/protocol/ServerMessage.java:7-15`

- [ ] **Step 1: Create PlayerStatsMessage.java**

```java
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
```

- [ ] **Step 2: Create PlayerStyleMessage.java**

```java
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
```

- [ ] **Step 3: Create PlayerStatsRequest.java**

```java
package com.texasholdem.common.protocol;

import lombok.Data;

@Data
public class PlayerStatsRequest {
    private String playerId;
}
```

- [ ] **Step 4: Register new message types in ServerMessage.java**

Update the `@JsonSubTypes` annotation to include the new types:

```java
@JsonSubTypes({
    @JsonSubTypes.Type(value = GameStateMessage.class, name = "GAME_STATE"),
    @JsonSubTypes.Type(value = ActionRequiredMessage.class, name = "ACTION_REQUIRED"),
    @JsonSubTypes.Type(value = HintResultMessage.class, name = "HINT_RESULT"),
    @JsonSubTypes.Type(value = GameResultMessage.class, name = "GAME_RESULT"),
    @JsonSubTypes.Type(value = RoomListMessage.class, name = "ROOM_LIST"),
    @JsonSubTypes.Type(value = RoomCreatedMessage.class, name = "ROOM_CREATED"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR"),
    @JsonSubTypes.Type(value = PlayerStatsMessage.class, name = "PLAYER_STATS"),
    @JsonSubTypes.Type(value = PlayerStyleMessage.class, name = "PLAYER_STYLE")
})
```

- [ ] **Step 5: Verify compilation**

Run: `cd /mnt/d/workspace/texas && mvn compile -pl poker-common,poker-server -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add poker-common/src/main/java/com/texasholdem/common/protocol/
git commit -m "feat: add player stats message DTOs with Lombok and register in ServerMessage"
```

---

### Task 7: Add STOMP Mappings for Stats Queries

**Files:**
- Modify: `poker-server/src/main/java/com/texasholdem/server/handler/ClientMessageHandler.java`

- [ ] **Step 1: Add stats query mappings to ClientMessageHandler**

Add imports:
```java
import com.texasholdem.server.stats.PlayerProfile;
import com.texasholdem.server.stats.PlayerStatsService;
import java.util.HashMap;
import java.util.Map;
```

Add field and update constructor:
```java
private final PlayerStatsService statsService;

public ClientMessageHandler(RoomManager roomManager, BroadcastService broadcastService,
                            PlayerStatsService statsService) {
    this.roomManager = roomManager;
    this.broadcastService = broadcastService;
    this.statsService = statsService;
}
```

Add these methods:
```java
@MessageMapping("/player/stats")
public void requestPlayerStats(PlayerStatsRequest request, Principal principal) {
    String userId = principal.getName();
    String targetId = request.getPlayerId() != null ? request.getPlayerId() : userId;
    PlayerProfile profile = statsService.getProfile(targetId);
    if (profile == null) {
        broadcastService.sendToUser(userId, "/queue/error",
                new ErrorMessage("Player not found", "PLAYER_NOT_FOUND"));
        return;
    }

    PlayerStatsMessage msg = new PlayerStatsMessage();
    msg.setPlayerId(profile.getId());
    msg.setDisplayName(profile.getDisplayName());
    msg.setAllTimeHands(profile.getAllTime().getHandsPlayed());
    msg.setRecentHands(profile.getRecent().getHandsPlayed());
    msg.setSessionHands(profile.getCurrentSession().getHandsPlayed());

    Map<String, Double> publicStats = new HashMap<>();
    publicStats.put("vpip", profile.getAllTime().getVpip().getValue());
    publicStats.put("pfr", profile.getAllTime().getPfr().getValue());
    publicStats.put("handsPlayed", (double) profile.getAllTime().getHandsPlayed());
    msg.setPublicStats(publicStats);

    Map<String, Double> privateStats = new HashMap<>();
    privateStats.put("threeBet", profile.getAllTime().getThreeBet().getValue());
    privateStats.put("af", profile.getAllTime().getAf().getValue());
    privateStats.put("wtsd", profile.getAllTime().getWtsd().getValue());
    privateStats.put("wsd", profile.getAllTime().getWsd().getValue());
    privateStats.put("foldToCbet", profile.getAllTime().getFoldToCbet().getValue());
    msg.setPrivateStats(privateStats);

    broadcastService.sendToUser(userId, "/queue/player-stats", msg);
}

@MessageMapping("/player/style")
public void requestPlayerStyle(PlayerStatsRequest request, Principal principal) {
    String userId = principal.getName();
    String targetId = request.getPlayerId() != null ? request.getPlayerId() : userId;
    PlayerProfile profile = statsService.getProfile(targetId);
    if (profile == null) return;

    double vpip = profile.getAllTime().getVpip().getValue();
    double pfr = profile.getAllTime().getPfr().getValue();

    PlayerStyleMessage msg = new PlayerStyleMessage();
    msg.setPlayerId(targetId);

    String tightness = vpip < 0.14 ? "tight" : vpip < 0.23 ? "medium" : "loose";
    double pfrVpipRatio = vpip > 0 ? pfr / vpip : 0;
    String aggressiveness = pfrVpipRatio > 0.75 ? "aggressive" : "passive";

    msg.setTightness(tightness);
    msg.setAggressiveness(aggressiveness);
    msg.setPrimaryStyle(classifyStyle(tightness, aggressiveness));
    msg.setConfidence(Math.min(1.0, profile.getAllTime().getHandsPlayed() / 100.0));

    broadcastService.sendToUser(userId, "/queue/player-style", msg);
}

private String classifyStyle(String tightness, String aggressiveness) {
    if ("tight".equals(tightness) && "aggressive".equals(aggressiveness)) return "TAG";
    if ("tight".equals(tightness) && "passive".equals(aggressiveness)) return "Rock";
    if ("loose".equals(tightness) && "aggressive".equals(aggressiveness)) return "LAG";
    if ("loose".equals(tightness) && "passive".equals(aggressiveness)) return "Calling Station";
    return "Unknown";
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /mnt/d/workspace/texas && mvn compile -pl poker-server -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add poker-server/src/main/java/com/texasholdem/server/handler/ClientMessageHandler.java
git commit -m "feat: add STOMP mappings for player stats and style queries"
```

---

## Phase 4: Frontend - Player HUD

### Task 8: Create usePlayerStats Hook and PlayerHUD Component

**Files:**
- Create: `poker-web/src/hooks/usePlayerStats.js`
- Create: `poker-web/src/components/PlayerHUD.jsx`
- Create: `poker-web/src/components/PlayerHUD.module.css`

- [ ] **Step 1: Create usePlayerStats.js**

```javascript
import { useState, useEffect, useCallback } from 'react';
import { useWebSocket } from '../context/WebSocketContext';

export function usePlayerStats(playerId) {
  const { subscribe, send } = useWebSocket();
  const [stats, setStats] = useState(null);
  const [style, setStyle] = useState(null);

  const fetchStats = useCallback(() => {
    if (!playerId) return;
    send('/app/player/stats', { playerId });
    send('/app/player/style', { playerId });
  }, [playerId, send]);

  useEffect(() => {
    if (!playerId) return;

    const unsub1 = subscribe('/user/queue/player-stats', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.playerId === playerId) setStats(data);
    });

    const unsub2 = subscribe('/user/queue/player-style', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.playerId === playerId) setStyle(data);
    });

    fetchStats();
    return () => { unsub1(); unsub2(); };
  }, [playerId, subscribe, fetchStats]);

  return { stats, style, refresh: fetchStats };
}
```

- [ ] **Step 2: Create PlayerHUD.module.css**

```css
.hud {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.7);
  background: rgba(0, 0, 0, 0.4);
  border-radius: 4px;
  padding: 4px 6px;
  min-width: 80px;
}

.statRow {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.label { color: rgba(255, 255, 255, 0.5); }
.value { color: #ecf0f1; font-weight: 500; }

.hands {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.4);
  text-align: center;
  margin-top: 2px;
}

.expandBtn {
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  font-size: 10px;
  padding: 2px 0;
  text-align: center;
}
.expandBtn:hover { color: rgba(255, 255, 255, 0.7); }

.privateStats {
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  padding-top: 4px;
  margin-top: 2px;
}
```

- [ ] **Step 3: Create PlayerHUD.jsx**

```jsx
import { useState } from 'react';
import { usePlayerStats } from '../hooks/usePlayerStats';
import styles from './PlayerHUD.module.css';

export default function PlayerHUD({ playerId, isSelf }) {
  const { stats } = usePlayerStats(playerId);
  const [expanded, setExpanded] = useState(false);

  if (!stats || !stats.publicStats) return null;

  const pub = stats.publicStats;
  const priv = stats.privateStats;

  return (
    <div className={styles.hud}>
      <div className={styles.statRow}>
        <span className={styles.label}>VPIP</span>
        <span className={styles.value}>{(pub.vpip * 100).toFixed(0)}%</span>
      </div>
      <div className={styles.statRow}>
        <span className={styles.label}>PFR</span>
        <span className={styles.value}>{(pub.pfr * 100).toFixed(0)}%</span>
      </div>
      <div className={styles.hands}>{stats.allTimeHands} 手</div>

      {isSelf && priv && (
        <>
          <button className={styles.expandBtn} onClick={() => setExpanded(!expanded)}>
            {expanded ? '收起 ▲' : '详细 ▼'}
          </button>
          {expanded && (
            <div className={styles.privateStats}>
              <div className={styles.statRow}>
                <span className={styles.label}>3Bet</span>
                <span className={styles.value}>{(priv.threeBet * 100).toFixed(0)}%</span>
              </div>
              <div className={styles.statRow}>
                <span className={styles.label}>AF</span>
                <span className={styles.value}>{priv.af.toFixed(1)}</span>
              </div>
              <div className={styles.statRow}>
                <span className={styles.label}>WTSD</span>
                <span className={styles.value}>{(priv.wtsd * 100).toFixed(0)}%</span>
              </div>
              <div className={styles.statRow}>
                <span className={styles.label}>W$SD</span>
                <span className={styles.value}>{(priv.wsd * 100).toFixed(0)}%</span>
              </div>
              <div className={styles.statRow}>
                <span className={styles.label}>Fold CB</span>
                <span className={styles.value}>{(priv.foldToCbet * 100).toFixed(0)}%</span>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Commit**

```bash
git add poker-web/src/hooks/usePlayerStats.js poker-web/src/components/PlayerHUD.jsx poker-web/src/components/PlayerHUD.module.css
git commit -m "feat: add PlayerHUD component with public/private stats"
```

---

### Task 9: Integrate HUD into Player Component

**Files:**
- Modify: `poker-web/src/components/Player.jsx`

- [ ] **Step 1: Add PlayerHUD to Player component**

Replace the contents of `Player.jsx`:

```jsx
import Card from './Card';
import PlayerHUD from './PlayerHUD';
import styles from './Player.module.css';

export default function Player({ player, isCurrentPlayer, isSelf, showCards }) {
  if (!player) return <div className={styles.empty} />;

  const stateLabel = player.folded ? '已弃牌'
    : player.allIn ? 'ALL IN'
    : isCurrentPlayer ? '思考中...'
    : '';

  return (
    <div className={`${styles.player} ${isCurrentPlayer ? styles.active : ''} ${isSelf ? styles.self : ''}`}>
      <div className={styles.name}>
        {player.seatId}
        {player.agentType && (
          <span className={player.agentType === 'react' ? styles.agentReact : styles.agentSimple}>
            {player.agentType === 'react' ? 'ReAct' : 'Simple'}
          </span>
        )}
      </div>
      <div className={styles.chips}>${player.chips}</div>
      {stateLabel && <div className={styles.state}>{stateLabel}</div>}
      {!player.agentType && <PlayerHUD playerId={player.seatId} isSelf={isSelf} />}
      <div className={styles.cards}>
        {player.holeCards && player.holeCards.length > 0 ? (
          player.holeCards.map((c, i) => (
            <Card key={i} rank={c.rank} suit={c.suit} />
          ))
        ) : (
          <>
            <Card hidden />
            <Card hidden />
          </>
        )}
      </div>
      {player.roundContribution > 0 && (
        <div className={styles.bet}>下注: ${player.roundContribution}</div>
      )}
      {player.handRankDisplay && (
        <div className={styles.handRank}>{player.handRankDisplay}</div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add poker-web/src/components/Player.jsx
git commit -m "feat: integrate PlayerHUD into Player component"
```

---

## Phase 5: Frontend - Profile Page

### Task 10: Create Player Style Utility and Profile Page

**Files:**
- Create: `poker-web/src/utils/playerStyle.js`
- Create: `poker-web/src/pages/Profile.jsx`
- Create: `poker-web/src/pages/Profile.module.css`

- [ ] **Step 1: Create playerStyle.js**

```javascript
export function classifyPlayerStyle(vpip, pfr) {
  const tightness = vpip < 0.14 ? 'tight' : vpip < 0.23 ? 'medium' : 'loose';
  const pfrVpipRatio = vpip > 0 ? pfr / vpip : 0;
  const aggressiveness = pfrVpipRatio > 0.75 ? 'aggressive' : 'passive';

  const styleMap = {
    'tight-passive': { name: 'Rock', emoji: '🪨', desc: '紧而被动，只玩好牌但下注保守' },
    'tight-aggressive': { name: 'TAG', emoji: '🎯', desc: '紧而激进，精选好牌积极下注' },
    'medium-passive': { name: 'Calling Station', emoji: '📞', desc: '中等频率，倾向于跟注' },
    'medium-aggressive': { name: 'TAG', emoji: '🎯', desc: '中等频率，积极下注' },
    'loose-passive': { name: 'Calling Station', emoji: '📞', desc: '松而被动，玩很多牌但很少加注' },
    'loose-aggressive': { name: 'LAG', emoji: '🔥', desc: '松而激进，频繁下注施压' },
  };

  return styleMap[`${tightness}-${aggressiveness}`] || { name: 'Unknown', emoji: '❓', desc: '数据不足' };
}

export function formatPercent(value) {
  return (value * 100).toFixed(1) + '%';
}
```

- [ ] **Step 2: Create Profile.module.css**

```css
.profile {
  min-height: 100vh;
  background: #0f1923;
  color: #ecf0f1;
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.backBtn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #ecf0f1;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
}
.backBtn:hover { background: rgba(255, 255, 255, 0.1); }

.playerName { font-size: 24px; font-weight: 600; }

.styleCard {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
  text-align: center;
}

.styleName { font-size: 32px; font-weight: 700; color: #f0c040; margin: 8px 0; }
.styleDesc { color: rgba(255, 255, 255, 0.6); font-size: 14px; }

.statsGrid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.statCard {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 16px;
  text-align: center;
}

.statLabel { font-size: 12px; color: rgba(255, 255, 255, 0.5); margin-bottom: 4px; }
.statValue { font-size: 24px; font-weight: 600; color: #ecf0f1; }
.statValue.good { color: #2ecc71; }
.statValue.avg { color: #f0c040; }
.statValue.bad { color: #e74c3c; }

.windowTabs { display: flex; gap: 8px; margin-bottom: 16px; }

.tab {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.6);
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.tab.active {
  background: rgba(240, 192, 64, 0.2);
  border-color: #f0c040;
  color: #f0c040;
}

.empty {
  text-align: center;
  color: rgba(255, 255, 255, 0.4);
  padding: 40px;
}
```

- [ ] **Step 3: Create Profile.jsx**

```jsx
import { useState } from 'react';
import { usePlayerStats } from '../hooks/usePlayerStats';
import { classifyPlayerStyle, formatPercent } from '../utils/playerStyle';
import styles from './Profile.module.css';

export default function Profile({ playerId, onBack }) {
  const { stats, style } = usePlayerStats(playerId);
  const [window, setWindow] = useState('allTime');

  if (!stats) {
    return (
      <div className={styles.profile}>
        <div className={styles.header}>
          <button className={styles.backBtn} onClick={onBack}>← 返回</button>
          <span className={styles.playerName}>{playerId}</span>
        </div>
        <div className={styles.empty}>暂无数据</div>
      </div>
    );
  }

  const styleInfo = style ? classifyPlayerStyle(
    stats.publicStats?.vpip || 0,
    stats.publicStats?.pfr || 0
  ) : null;

  const pub = stats.publicStats || {};
  const priv = stats.privateStats || {};

  const statCards = [
    { label: 'VPIP', value: formatPercent(pub.vpip || 0), quality: getVpipQuality(pub.vpip) },
    { label: 'PFR', value: formatPercent(pub.pfr || 0), quality: 'avg' },
    { label: '3Bet', value: formatPercent(priv.threeBet || 0), quality: 'avg' },
    { label: 'AF', value: (priv.af || 0).toFixed(2), quality: getAfQuality(priv.af) },
    { label: 'WTSD', value: formatPercent(priv.wtsd || 0), quality: 'avg' },
    { label: 'W$SD', value: formatPercent(priv.wsd || 0), quality: getWsdQuality(priv.wsd) },
    { label: 'Fold to CB', value: formatPercent(priv.foldToCbet || 0), quality: 'avg' },
    { label: '总手数', value: stats.allTimeHands || 0, quality: 'avg' },
  ];

  return (
    <div className={styles.profile}>
      <div className={styles.header}>
        <button className={styles.backBtn} onClick={onBack}>← 返回</button>
        <span className={styles.playerName}>{stats.displayName || playerId}</span>
      </div>

      {styleInfo && (
        <div className={styles.styleCard}>
          <div style={{ fontSize: 48 }}>{styleInfo.emoji}</div>
          <div className={styles.styleName}>{styleInfo.name}</div>
          <div className={styles.styleDesc}>{styleInfo.desc}</div>
        </div>
      )}

      <div className={styles.windowTabs}>
        {['allTime', 'recent', 'session'].map(w => (
          <button
            key={w}
            className={`${styles.tab} ${window === w ? styles.active : ''}`}
            onClick={() => setWindow(w)}
          >
            {w === 'allTime' ? '全部' : w === 'recent' ? '近500手' : '本场'}
          </button>
        ))}
      </div>

      <div className={styles.statsGrid}>
        {statCards.map(card => (
          <div key={card.label} className={styles.statCard}>
            <div className={styles.statLabel}>{card.label}</div>
            <div className={`${styles.statValue} ${styles[card.quality]}`}>{card.value}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function getVpipQuality(v) { return !v ? 'avg' : v < 0.14 || v > 0.23 ? 'bad' : 'good'; }
function getAfQuality(v) { return !v ? 'avg' : v < 1.5 ? 'bad' : v > 2.0 ? 'good' : 'avg'; }
function getWsdQuality(v) { return !v ? 'avg' : v < 0.45 ? 'bad' : v > 0.55 ? 'good' : 'avg'; }
```

- [ ] **Step 4: Commit**

```bash
git add poker-web/src/utils/playerStyle.js poker-web/src/pages/Profile.jsx poker-web/src/pages/Profile.module.css
git commit -m "feat: add Profile page with style classification and stats display"
```

---

### Task 11: Add Profile Navigation to App

**Files:**
- Modify: `poker-web/src/App.jsx`
- Modify: `poker-web/src/pages/Lobby.jsx`

- [ ] **Step 1: Update App.jsx to add profile page state**

Replace `App.jsx`:

```jsx
import { useState } from 'react';
import { useWebSocket } from './context/WebSocketContext';
import Lobby from './pages/Lobby';
import Game from './pages/Game';
import Profile from './pages/Profile';
import styles from './App.module.css';

export default function App() {
  const { connected, connect, send, userId } = useWebSocket();
  const [inputId, setInputId] = useState('');
  const [currentRoom, setCurrentRoom] = useState(null);
  const [initialRoomState, setInitialRoomState] = useState(null);
  const [viewingProfile, setViewingProfile] = useState(null);

  if (!connected) {
    return (
      <div className={styles.login}>
        <div className={styles.loginCard}>
          <h1 className={styles.loginTitle}>Texas Hold'em</h1>
          <p className={styles.loginSub}>输入昵称加入游戏</p>
          <form onSubmit={(e) => { e.preventDefault(); if (inputId.trim()) connect(inputId.trim()); }}>
            <input
              className={styles.loginInput}
              value={inputId}
              onChange={e => setInputId(e.target.value)}
              placeholder="你的昵称"
              autoFocus
            />
            <button type="submit" className={styles.loginBtn}>连接</button>
          </form>
        </div>
      </div>
    );
  }

  if (viewingProfile) {
    return <Profile playerId={viewingProfile} onBack={() => setViewingProfile(null)} />;
  }

  if (currentRoom) {
    return (
      <Game
        roomId={currentRoom.roomId}
        roomName={currentRoom.roomName}
        initialRoomState={initialRoomState}
        onLeave={() => {
          send('/app/room/leave', { roomId: currentRoom.roomId });
          setCurrentRoom(null);
          setInitialRoomState(null);
        }}
      />
    );
  }

  return <Lobby
    onJoinRoom={(roomId, roomName, roomData) => {
      setCurrentRoom({ roomId, roomName });
      setInitialRoomState(roomData || null);
    }}
    onOpenProfile={(playerId) => setViewingProfile(playerId)}
  />;
}
```

- [ ] **Step 2: Update Lobby.jsx to add profile click**

In `Lobby.jsx`, update the function signature:
```jsx
export default function Lobby({ onJoinRoom, onOpenProfile }) {
```

Update the user display (around line 44) to be clickable:
```jsx
<div className={styles.user} onClick={() => onOpenProfile && onOpenProfile(userId)} style={{ cursor: 'pointer' }}>
  <span className={styles.dot} />
  {userId}
</div>
```

- [ ] **Step 3: Commit**

```bash
git add poker-web/src/App.jsx poker-web/src/pages/Lobby.jsx
git commit -m "feat: add profile page navigation from lobby"
```

---

## Phase 6: Verification

### Task 12: End-to-End Verification

- [ ] **Step 1: Start MongoDB**

Run: `docker compose up -d mongo`
Expected: MongoDB container starts

- [ ] **Step 2: Start backend**

Run: `cd /mnt/d/workspace/texas && mvn spring-boot:run -pl poker-server`
Expected: Starts on port 8080, connects to MongoDB

- [ ] **Step 3: Start frontend**

Run: `cd /mnt/d/workspace/texas/poker-web && npm run dev`
Expected: Dev server starts

- [ ] **Step 4: Manual test**

1. Open `http://localhost:5173`
2. Login, click nickname in lobby → Profile page (should show "暂无数据")
3. Create room, add bot, play 3+ hands
4. Verify HUD appears on human player seats (VPIP, PFR, hands count)
5. Click own HUD "详细" button → private stats expand
6. Return to lobby, click nickname → Profile page shows stats

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: player profile system complete"
```

---

## Deferred (Future Phases)

- Rolling window stat eviction (subtract old hand stats when window exceeds 500)
- Session tracking (reset currentSession on new game session start)
- REST endpoints (design doc proposes REST; STOMP used for consistency)
- WebSocket push of stats after each hand (currently query-only)
- Hand history list page
- Trend charts / visualizations
- AI integration (feed opponent stats to poker-agent for strategy adjustment)
