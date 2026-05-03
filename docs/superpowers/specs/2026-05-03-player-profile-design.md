# Player Profile System Design

## Overview

A historical player profile system that tracks poker statistics based on game records. The system serves three purposes:
1. **In-game HUD** - Show player stats during gameplay
2. **Post-game analysis** - Allow players to review their performance
3. **AI adaptation** - Feed stats to AI agents for strategy adjustment

## Requirements

### Metrics to Track

| Metric | Full Name | Description | Classification |
|--------|-----------|-------------|----------------|
| VPIP | Voluntarily Put $ In Pot | Frequency of voluntarily entering pots | Tight (<14%), Medium (14-23%), Loose (>23%) |
| PFR | Pre-Flop Raise | Frequency of raising before flop | Passive (<<VPIP), Aggressive (≈VPIP) |
| 3Bet | Three-Bet Rate | Frequency of re-raising after an open raise | Low, High |
| AF | Aggression Factor | Ratio of aggressive actions (raise/bet) to passive (call/check) | Passive (<1.5), Aggressive (>2.0) |
| WTSD | Went to Showdown | Frequency of reaching showdown | Tight (<25%), Medium (25-35%), Loose (>35%) |
| W$SD | Won $ at Showdown | Win rate when reaching showdown | Weak (<45%), Average (45-55%), Strong (>55%) |
| Fold to Cbet | Fold to Continuation Bet | Frequency of folding to continuation bets | Tight (>50%), Medium (35-50%), Loose (<35%) |

### Time Windows

- **All-time** - Complete history across all sessions
- **Rolling window** - Last 500 hands (configurable)
- **Current session** - Stats for the active game session

### Privacy Model (Tiered Visibility)

**Public stats (visible to all players):**
- VPIP, PFR, Hands Played

**Private stats (visible only to the player):**
- 3Bet, AF, WTSD, W$SD, Fold to Cbet

## Architecture

### Approach: Post-Hand Batch Computation

Stats are computed after each hand completes by processing the hand's action history. This integrates cleanly with the existing `ReplayRecorder` system.

```
GameRoom.onHandCompleted()
    ↓
ReplayRecorder.onHandCompleted()
    ↓
ReplayStore.saveHand()          PlayerStatsService.processHand()
    ↓                                    ↓
JSON Files                         MongoDB (player_profiles)
    ↓                                    ↓
Replay Feature                   Real-time Stats Query
```

## Data Model

### MongoDB Collections

#### `player_profiles` Collection

```javascript
{
  "_id": "player123",           // Player ID
  "displayName": "PlayerName",
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-15T12:30:00Z",

  // All-time statistics
  "allTime": {
    "handsPlayed": 1500,
    "stats": {
      "vpip": { "value": 0.22, "opportunities": 1500, "count": 330 },
      "pfr": { "value": 0.18, "opportunities": 1500, "count": 270 },
      "threeBet": { "value": 0.08, "opportunities": 400, "count": 32 },
      "af": { "value": 2.1, "opportunities": 800, "aggressiveActions": 1680 },
      "wtsd": { "value": 0.28, "opportunities": 600, "count": 168 },
      "wsd": { "value": 0.52, "opportunities": 168, "count": 87 },
      "foldToCbet": { "value": 0.45, "opportunities": 300, "count": 135 }
    }
  },

  // Rolling window statistics (last 500 hands)
  "recent": {
    "windowSize": 500,
    "handsPlayed": 500,
    "handIds": ["hand_12345", "hand_12344", "..."],  // Circular buffer for eviction
    "stats": { /* Same structure as allTime */ }
  },

  // Current session statistics
  "currentSession": {
    "sessionId": "session_abc",
    "startTime": "2024-01-15T10:00:00Z",
    "handsPlayed": 45,
    "stats": { /* Same structure as allTime */ }
  }
}
```

#### `hand_actions` Collection

```javascript
{
  "_id": "hand_12345",
  "sessionId": "session_abc",
  "roomId": "room_1",
  "handNumber": 42,
  "startTime": "2024-01-15T12:30:00Z",
  "endTime": "2024-01-15T12:31:30Z",

  // Players with positions
  "players": [
    { "seatId": "seat1", "playerId": "player123", "position": "BTN" },
    { "seatId": "seat2", "playerId": "player456", "position": "SB" }
  ],

  // Action sequence
  "actions": [
    { "playerId": "player123", "action": "RAISE", "amount": 100, "phase": "PREFLOP", "timestamp": 1705312200000 },
    { "playerId": "player456", "action": "CALL", "amount": 100, "phase": "PREFLOP", "timestamp": 1705312205000 }
  ],

  // Results
  "board": ["As", "Kd", "7h", "2c", "9s"],
  "pot": 500,
  "winnerId": "player123",
  "winnerHand": "Two Pair"
}
```

### Stat Entry Structure

Each stat entry uses opportunity counting for accurate incremental updates:

```javascript
{
  "value": 0.22,           // Computed: count / opportunities
  "opportunities": 1500,   // Times the stat could have been triggered
  "count": 330             // Times the stat was actually triggered
}
```

## Metrics Calculation Logic

| Metric | Formula | Trigger | Condition |
|--------|---------|---------|-----------|
| VPIP | count / opportunities | Hand start | Player voluntarily enters pot (not forced blinds only) |
| PFR | count / opportunities | Hand start | Player has RAISE action in preflop |
| 3Bet | count / opportunities | Someone opens | Player re-raises after facing an open raise preflop |
| AF | aggressive / opportunities | Each action | RAISE/BET = aggressive, CALL/CHECK = passive (post-flop only) |
| WTSD | count / opportunities | Hand end | Player reaches showdown (didn't fold, saw river) |
| W$SD | count / opportunities | Showdown | Player wins pot at showdown |
| Fold to Cbet | count / opportunities | Post-flop | Player folds to a continuation bet |

### Incremental Update Algorithm

```java
void updateStats(String playerId, HandRecord hand) {
    PlayerProfile profile = getOrCreateProfile(playerId);

    for (Window window : [allTime, recent, currentSession]) {
        window.handsPlayed++;

        // VPIP
        if (isVoluntaryEntry(hand, playerId)) {
            window.vpip.count++;
        }
        window.vpip.opportunities++;
        window.vpip.value = window.vpip.count / window.vpip.opportunities;

        // PFR
        if (hasPreflopRaise(hand, playerId)) {
            window.pfr.count++;
        }
        window.pfr.opportunities++;

        // 3Bet
        if (isThreeBet(hand, playerId)) {
            window.threeBet.count++;
        }
        if (facesOpenRaise(hand, playerId)) {
            window.threeBet.opportunities++;
        }

        // AF
        for (Action a : getActions(hand, playerId)) {
            if (isAggressive(a)) window.af.aggressiveActions++;
            if (isPostFlop(a)) window.af.opportunities++;
        }

        // WTSD: player didn't fold and hand reached showdown
        if (!foldedBeforeShowdown(hand, playerId) && isShowdown(hand)) {
            window.wtsd.count++;
        }
        window.wtsd.opportunities++;

        // W$SD: player won at showdown
        if (isShowdown(hand) && isWinner(hand, playerId)) {
            window.wsd.count++;
        }
        if (isShowdown(hand) && !foldedBeforeShowdown(hand, playerId)) {
            window.wsd.opportunities++;
        }

        // Fold to Cbet: player folded facing a continuation bet
        if (facesCbet(hand, playerId) && foldedToCbet(hand, playerId)) {
            window.foldToCbet.count++;
        }
        if (facesCbet(hand, playerId)) {
            window.foldToCbet.opportunities++;
        }
    }

    // Rolling window: evict oldest hands (circular buffer with hand IDs)
    recent.handIds.add(hand.getHandId());
    if (recent.handIds.size() > 500) {
        String oldestHandId = recent.handIds.remove(0);
        revertStatsFromHand(recent, oldestHandId, playerId);
    }

    saveProfile(profile);
}
```

## API Design

### RESTful Endpoints

#### Get Player Stats

```
GET /api/players/{playerId}/stats
```

Response:
```json
{
  "playerId": "player123",
  "displayName": "PlayerName",
  "allTime": { "handsPlayed": 1500, "stats": { ... } },
  "recent": { "windowSize": 500, "handsPlayed": 500, "stats": { ... } },
  "currentSession": { ... }
}
```

#### Get Player Hand History

```
GET /api/players/{playerId}/hands?limit=50&offset=0
```

Response:
```json
{
  "hands": [
    {
      "handId": "hand_12345",
      "timestamp": "2024-01-15T12:30:00Z",
      "position": "BTN",
      "holeCards": ["As", "Kd"],
      "result": "+250",
      "handType": "Two Pair"
    }
  ],
  "total": 1500
}
```

#### Get Player Style

```
GET /api/players/{playerId}/style
```

Response:
```json
{
  "playerId": "player123",
  "style": {
    "primary": "TAG",
    "secondary": "LAG",
    "confidence": 0.85
  },
  "breakdown": {
    "tightness": "tight",
    "aggressiveness": "aggressive"
  }
}
```

### WebSocket (In-Game HUD)

Server pushes stats updates during gameplay:

```json
{
  "type": "PLAYER_STATS_UPDATE",
  "playerId": "player123",
  "publicStats": {
    "vpip": 0.22,
    "pfr": 0.18,
    "handsPlayed": 1500
  },
  "privateStats": {
    "threeBet": 0.08,
    "af": 2.1,
    "wtsd": 0.28,
    "wsd": 0.52,
    "foldToCbet": 0.45
  }
}
```

## Integration with Existing System

### ReplayRecorder Modification

Add `PlayerStatsService` dependency to `ReplayRecorder`:

```java
@Component
public class ReplayRecorder {
    private final ReplayStore replayStore;
    private final PlayerStatsService statsService;  // New

    public void onHandCompleted(String sessionId, int handNumber,
                                 GameState state, Map<String, Integer> finalChips) {
        // ... existing logic ...
        replayStore.saveHand(sessionId, hand);

        // New: trigger stats update
        statsService.processHand(hand, session.getPlayers());
    }
}
```

### New Components

**PlayerStatsService** - Core service for stats computation

```java
@Service
public class PlayerStatsService {
    private final MongoTemplate mongoTemplate;

    public void processHand(HandRecord hand, List<PlayerRecord> players) {
        for (PlayerRecord player : players) {
            if (player.isAiAgent()) continue;

            PlayerProfile profile = getOrCreateProfile(player.getPlayerId());
            updateStats(profile, hand, player.getSeatId());
            updateRecentHands(profile, hand);
            saveProfile(profile);
        }
    }
}
```

**PlayerProfileRepository** - MongoDB repository

```java
@Repository
public interface PlayerProfileRepository extends MongoRepository<PlayerProfile, String> {

    @Query("{ '_id': ?0 }")
    PlayerProfile findByPlayerId(String playerId);

    @Query("{ 'currentSession.sessionId': ?0 }")
    List<PlayerProfile> findBySessionId(String sessionId);
}
```

## Frontend Components

### 1. Player HUD (In-Game)

Compact display next to player avatar in `Player.jsx`:

```
┌─────────────┐
│  Player      │
│  VPIP: 22%  │
│  PFR: 18%   │
│  1500 hands  │
└─────────────┘
```

Public stats always visible. Private stats expandable on click (only for own player).

### 2. Player Profile Page

New route `/player/{playerId}` with full statistics view:

- Style classification (TAG, LAG, Rock, Calling Station)
- All metrics with visualizations
- Recent hands table
- Trend charts

### 3. Style Classification System

```javascript
function classifyPlayerStyle(stats) {
  const { vpip, pfr, af } = stats;

  const tightness = vpip < 0.14 ? 'tight' :
                    vpip < 0.23 ? 'medium' : 'loose';

  const pfrVpipRatio = pfr / vpip;
  const aggressiveness = pfrVpipRatio > 0.75 ? 'aggressive' : 'passive';

  const styleMap = {
    'tight-passive': 'Rock',
    'tight-aggressive': 'TAG',
    'loose-passive': 'Calling Station',
    'loose-aggressive': 'LAG'
  };

  return styleMap[`${tightness}-${aggressiveness}`];
}
```

### 4. WebSocket Subscription

```javascript
useEffect(() => {
  const handleStatsUpdate = (data) => {
    setPlayerStats(prev => ({
      ...prev,
      [data.playerId]: data
    }));
  };

  ws.on('PLAYER_STATS_UPDATE', handleStatsUpdate);
  return () => ws.off('PLAYER_STATS_UPDATE', handleStatsUpdate);
}, []);
```

## Implementation Phases

### Phase 1: Backend Core
- MongoDB setup with Spring Data MongoDB
- PlayerProfile entity and repository
- PlayerStatsService with basic VPIP/PFR calculation
- Integration with ReplayRecorder

### Phase 2: Full Metrics
- Implement all 7 metrics (3Bet, AF, WTSD, W$SD, Fold to Cbet)
- Rolling window implementation
- Session tracking

### Phase 3: API Layer
- REST endpoints for stats, history, style
- WebSocket push for in-game HUD

### Phase 4: Frontend
- Player HUD component
- Player profile page
- Style classification
- Trend visualizations

### Phase 5: AI Integration
- Expose stats to AI agent
- Strategy adjustment based on opponent profiles
