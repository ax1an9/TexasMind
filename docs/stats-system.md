# Poker Player Statistics System

This document explains how player statistics are calculated, stored, and displayed in the poker application.

---

## Metrics Overview

The system tracks **7 poker statistics** plus a **player style classification**. Each stat uses an "opportunity counting" model: `value = count / opportunities`.

| Metric | Full Name | Calculation |
|--------|-----------|-------------|
| **VPIP** | Voluntarily Put $ In Pot | hands where player voluntarily entered (CALL/RAISE/BET/ALL_IN) / total hands |
| **PFR** | Pre-Flop Raise | hands where player raised or bet preflop / total hands |
| **3Bet** | Three-Bet Rate | times player re-raised facing an open raise / times facing an open raise |
| **AF** | Aggression Factor | aggressive actions (RAISE/BET/ALL_IN) / total post-flop actions |
| **WTSD** | Went to Showdown | hands reaching showdown (2+ players remained) / hands played |
| **W$SD** | Won $ at Showdown | showdowns won / showdowns reached |
| **Fold to Cbet** | Fold to Continuation Bet | times folded facing a continuation bet / times facing a continuation bet |

### Privacy Model

- **Public** (visible to all players): VPIP, PFR, handsPlayed
- **Private** (visible only to self): 3Bet, AF, WTSD, W$SD, Fold to Cbet

### Time Windows

Stats are maintained in three parallel windows:

| Window | Description |
|--------|-------------|
| All-time | Complete history since first hand |
| Recent | Rolling window of last 500 hands |
| Session | Stats for the current game session only |

---

## Style Classification

Derived from VPIP and PFR ratios:

**Tightness** (based on VPIP):
- VPIP < 14% -> Tight
- 14% <= VPIP < 23% -> Medium
- VPIP >= 23% -> Loose

**Aggressiveness** (based on PFR/VPIP ratio):
- PFR/VPIP > 0.75 -> Aggressive
- PFR/VPIP <= 0.75 -> Passive

**Combined styles:**

| | Aggressive | Passive |
|---|---|---|
| **Tight** | TAG (Tight-Aggressive) | Rock (Tight-Passive) |
| **Medium** | Balanced-Aggressive | Balanced-Passive |
| **Loose** | LAG (Loose-Aggressive) | Calling Station (Loose-Passive) |

Confidence = min(1.0, handsPlayed / 100.0) -- classification becomes more reliable with more data.

---

## Data Flow

```
Hand Completes
    |
    v
GameRoom.handleSettled()
    |
    v
PlayerStatsService.processHand()
    |-- For each human player (skips AI agents):
    |       |
    |       +-- Load/create PlayerProfile from MongoDB
    |       |
    |       +-- StatsCalculator.analyzeHand() -- pure logic, no side effects
    |       |       |
    |       |       +-- hasVoluntaryEntry()     -> VPIP flag
    |       |       +-- hasPreflopRaise()       -> PFR flag
    |       |       +-- analyzeThreeBet()       -> 3Bet flags
    |       |       +-- analyzeAggression()     -> aggressive/postflop counts
    |       |       +-- showdown detection      -> WTSD/W$SD flags
    |       |       +-- analyzeFoldToCbet()     -> fold-to-cbet flags
    |       |
    |       +-- applyUpdate() to all 3 windows (allTime, recent, session)
    |       +-- Save to MongoDB
    |
    v
Stats persisted in MongoDB (player_profiles collection)
    |
    v
Client requests stats via STOMP WebSocket
    |
    v
ClientMessageHandler builds response:
    |-- publicStats:  { vpip, pfr, handsPlayed }
    |-- privateStats: { threeBet, af, wtsd, wsd, foldToCbet }
    |
    v
Frontend displays:
    |-- PlayerHUD (in-game overlay): VPIP%, PFR%, hands for all; private stats for self
    |-- Profile page: full stats grid, style card, time window tabs
```

---

## Code Locations

### Backend -- Stats Calculation

| File | Purpose | Key Lines |
|------|---------|-----------|
| `poker-server/.../stats/StatsCalculator.java` | Pure-logic hand analysis | L24-44: `analyzeHand()` entry point |
| | | L46-53: `hasVoluntaryEntry()` (VPIP) |
| | | L55-62: `hasPreflopRaise()` (PFR) |
| | | L64-79: `analyzeThreeBet()` |
| | | L81-92: `analyzeAggression()` (AF) |
| | | L94-121: `analyzeFoldToCbet()` |
| `poker-server/.../stats/PlayerStatsService.java` | Orchestrates calculation + persistence | L20-45: `processHand()` |
| | | L47-88: `applyUpdate()` -- increments counters per window |
| `poker-server/.../stats/StatEntry.java` | Single metric value object | L11: `increment()`, L17: `addOpportunity()` |
| `poker-server/.../stats/PlayerProfile.java` | MongoDB document | L10: `@Document(collection="player_profiles")` |
| | | L18-20: three `StatsWindow` instances |
| `poker-server/.../stats/PlayerProfileRepository.java` | Spring Data MongoDB repo | Standard CRUD |

### Backend -- Integration

| File | Purpose | Key Lines |
|------|---------|-----------|
| `poker-server/.../room/GameRoom.java` | Trigger point after hand settles | L330-336: calls `statsService.processHand()` |
| `poker-server/.../room/RoomManager.java` | Dependency injection | L23,28-35: injects `PlayerStatsService` |
| `poker-server/.../handler/ClientMessageHandler.java` | STOMP API endpoints | L171-203: `/player/stats` handler |
| | | L206-228: `/player/style` handler |

### Shared Protocol

| File | Purpose |
|------|---------|
| `poker-common/.../protocol/PlayerStatsMessage.java` | Stats response DTO |
| `poker-common/.../protocol/PlayerStyleMessage.java` | Style response DTO |
| `poker-common/.../protocol/PlayerStatsRequest.java` | Stats request DTO |
| `poker-common/.../protocol/ServerMessage.java` | L15-16: registers PLAYER_STATS, PLAYER_STYLE subtypes |

### Frontend

| File | Purpose |
|------|---------|
| `poker-web/src/components/PlayerHUD.jsx` | In-game HUD overlay |
| `poker-web/src/components/Player.jsx` | L25: integrates PlayerHUD into player seat |
| `poker-web/src/pages/Profile.jsx` | Full profile page with style card + stats grid |
| `poker-web/src/hooks/usePlayerStats.js` | Custom hook: STOMP subscription for stats/style |
| `poker-web/src/utils/playerStyle.js` | `classifyPlayerStyle(vpip, pfr)` -- maps to style name/emoji |
| `poker-web/src/context/WebSocketContext.jsx` | STOMP over SockJS transport |

### AI Agent (Python, in-memory only)

| File | Purpose |
|------|---------|
| `poker-agent/.../memory/session_memory.py` | `hands_played`, `hands_won`, `win_rate` |
| `poker-agent/.../memory/opponent_memory.py` | Per-opponent action/fold counts |
| `poker-agent/.../tools/opponent_modeling.py` | `aggressive_rate` classification |
| `poker-agent/.../tools/hand_evaluation.py` | `estimated_win_rate` from hand strength |

### Design Docs

| File | Purpose |
|------|---------|
| `docs/superpowers/specs/2026-05-03-player-profile-design.md` | Full design spec |
| `docs/superpowers/plans/2026-05-03-player-profile.md` | Implementation plan |

---

## MongoDB Collection

Collection: `player_profiles`

```json
{
  "_id": "player-uuid",
  "displayName": "PlayerName",
  "createdAt": "2026-05-03T10:00:00Z",
  "updatedAt": "2026-05-03T10:30:00Z",
  "allTime": {
    "handsPlayed": 150,
    "handIds": ["hand-1", "hand-2", "..."],
    "vpip": { "value": 0.22, "opportunities": 150, "count": 33 },
    "pfr": { "value": 0.18, "opportunities": 150, "count": 27 },
    "threeBet": { "value": 0.05, "opportunities": 40, "count": 2 },
    "af": { "value": 2.1, "opportunities": 200, "count": 140 },
    "wtsd": { "value": 0.28, "opportunities": 150, "count": 42 },
    "wsd": { "value": 0.55, "opportunities": 42, "count": 23 },
    "foldToCbet": { "value": 0.45, "opportunities": 30, "count": 14 },
    "aggressiveActions": 140
  },
  "recent": { "...same structure, max 500 hands..." },
  "currentSession": { "...same structure, resets per session..." }
}
```
