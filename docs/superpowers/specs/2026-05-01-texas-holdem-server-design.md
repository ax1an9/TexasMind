# Texas Hold'em Poker Server - Design Specification

## Overview

A modular monolith poker server supporting No-Limit Texas Hold'em with AI agent extensibility, human/AI mixed gameplay, multi-user rooms, real-time AI hints, and standardized game data for replay.

## Goals

1. Single-player mode with human or AI opponents
2. Extensible agent framework supporting external Python agents (ReAct, etc.)
3. Real-time AI hints and probability analysis during gameplay
4. Standardized game data format for replay and analysis
5. Multi-user room system with WebSocket communication

## Architecture

### Approach: Modular Monolith + gRPC Agent Bridge

Single JVM process with clean module boundaries. gRPC for agent communication (language-agnostic, efficient). Event-driven game engine.

```
┌─────────────────────────────────────────────┐
│              Poker Server (JVM)              │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐ │
│  │ Game     │ │ User     │ │ WebSocket    │ │
│  │ Engine   │ │ Module   │ │ Gateway      │ │
│  └────┬─────┘ └──────────┘ └──────────────┘ │
│       │                                      │
│  ┌────┴─────┐ ┌──────────┐ ┌──────────────┐ │
│  │ Action   │ │ AI Hint  │ │ Replay       │ │
│  │ Handler  │ │ Service  │ │ Recorder     │ │
│  └────┬─────┘ └──────────┘ └──────────────┘ │
│       │                                      │
│  ┌────┴─────────────────────────────────┐    │
│  │       Agent Bridge (gRPC Server)     │    │
│  └────┬─────────────────────────────────┘    │
└───────┼──────────────────────────────────────┘
        │ gRPC
  ┌─────┴──────┐
  │ Python     │  (ReAct, Custom, etc.)
  │ Agent      │
  └────────────┘
```

## Module Structure

```
poker-server/
├── poker-core/          # Core game engine (pure logic, no IO)
│   ├── game/            # Game lifecycle management
│   ├── model/           # Data models (Card, Hand, Player, GameState)
│   ├── rule/            # Rule engine (hand comparison, betting rules, pot distribution)
│   └── eval/            # Hand evaluation (hand strength, win rate calculation)
│
├── poker-ai/            # AI hints and probability analysis
│   ├── hint/            # Real-time hint service (suggested actions, win rate)
│   ├── prob/            # Probability calculator (Monte Carlo simulation)
│   └── strategy/        # Built-in basic strategies (reference for AI agents)
│
├── poker-agent/         # Agent bridge layer
│   ├── bridge/          # gRPC Server, receives external agent connections
│   ├── registry/        # Agent registration and discovery
│   └── adapter/         # Protocol adapter (converts external agent decisions to game Actions)
│
├── poker-server/        # Main server module
│   ├── ws/              # WebSocket gateway (client connection management)
│   ├── session/         # Session management (game state synchronization)
│   ├── room/            # Room management (create/join/leave)
│   └── replay/          # Game recording and standardized data output
│
├── poker-user/          # User module
│   ├── auth/            # Authentication (JWT)
│   └── profile/         # User info, statistics
│
└── poker-common/        # Common module
    ├── proto/           # gRPC Proto definitions
    ├── event/           # Event system (game event pub/sub)
    └── config/          # Configuration management
```

**Design principles**:
- `poker-core` is a pure functional module with no IO dependencies, independently testable
- Modules decoupled via event system; game engine publishes events, other modules subscribe
- Each module has clear API boundaries

## Game Engine Core (poker-core)

### State Machine

A hand lifecycle driven by finite state machine:

```
WAITING → PRE_FLOP → FLOP → TURN → RIVER → SHOWDOWN → SETTLED
   ↑                                                      │
   └──────────────────────────────────────────────────────┘
                    (next hand)
```

### Core Data Models

```java
// Card and hand rank
public class Card {
    private final Rank rank; // 2-10, J, Q, K, A
    private final Suit suit; // HEARTS, DIAMONDS, CLUBS, SPADES
}

public enum HandRank {
    HIGH_CARD, ONE_PAIR, TWO_PAIR, THREE_OF_KIND,
    STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_KIND,
    STRAIGHT_FLUSH, ROYAL_FLUSH
}

// Unified player abstraction (human or agent)
public interface Seat {
    String getId();
    int getChips();
    boolean isHuman();
}

// Immutable game state, new state produced per action
public class GameState {
    private final GamePhase phase;
    private final List<Card> board;           // Community cards
    private final List<PlayerState> players;  // Each player's state
    private final PotInfo pot;                // Pot information
    private final List<Action> actionHistory; // Action history
    private final int dealerPosition;
}

// Action hierarchy
public abstract class Action {
    private final String playerId;
    private final ActionType type;
    private final int amount;
}

public enum ActionType {
    FOLD, CHECK, CALL, BET, RAISE, ALL_IN
}
```

**Key decisions**:
- `GameState` is immutable; each action produces a new state for replay and rollback
- `Action` uses inheritance hierarchy (Java 8 compatible, no sealed classes)
- Player unified as `Seat` interface, no distinction between human and agent

## Agent Bridge and Extensibility

### gRPC Protocol

```protobuf
service PokerAgent {
    rpc Register(RegisterRequest) returns (RegisterResponse);
    rpc MakeDecision(stream GameState) returns (stream Action);
    rpc Ping(PingRequest) returns (PingResponse);
}

message RegisterRequest {
    string agent_id = 1;
    string agent_name = 2;
    string agent_type = 3;  // "builtin", "react", "custom"
    AgentCapability capabilities = 4;
}

message GameState {
    string game_id = 1;
    GamePhase phase = 2;
    repeated Card board = 3;
    PlayerView player_view = 4;  // Only info visible to this agent
    repeated ActionEntry action_history = 5;
    PotInfo pot = 6;
    TimingInfo timing = 7;  // Decision time limit
}
```

### Agent Type Hierarchy

```
Agent Interface
├── BuiltinAgent          # Built-in server agents (random, tight-aggressive, etc.)
├── GrpcAgent             # External gRPC agent bridge
└── ProxyAgent            # Proxy (abstracts human players as agent interface)
```

### Python Agent Example

```python
class ReActAgent(PokerAgentServicer):
    def MakeDecision(self, request_iterator, context):
        for game_state in request_iterator:
            thought = self.think(game_state)
            action = self.decide(game_state, thought)
            yield Action(player_id=self.id, action_type=action)
```

### Built-in AI Hints

When a human player requests help, the `poker-ai` module computes in parallel:
- Current hand win rate (Monte Carlo simulation)
- Suggested action (GTO or heuristic-based)
- Opponent range inference

## WebSocket Communication and Multi-user

### Message Protocol (JSON)

```json
// Client → Server
{ "type": "JOIN_ROOM", "roomId": "xxx", "seatPreference": 3 }
{ "type": "PLAYER_ACTION", "gameId": "xxx", "action": "RAISE", "amount": 200 }
{ "type": "REQUEST_HINT", "gameId": "xxx" }

// Server → Client
{ "type": "GAME_STATE", "gameId": "xxx", "state": { ... } }
{ "type": "ACTION_REQUIRED", "gameId": "xxx", "timeLimit": 30000 }
{ "type": "HINT_RESULT", "suggestion": "CALL", "winRate": 0.65, "reasoning": "..." }
{ "type": "GAME_RESULT", "gameId": "xxx", "winner": "player1", "pot": 1500 }
```

### Room System

```
Room
├── Room ID / Name
├── Seat list (max 9 seats)
├── Game config (blind size, action time limit)
├── Game engine instance
└── Spectator list

RoomManager
├── Create room
├── Quick match join
├── Join specific room
└── List rooms
```

### Multi-player Synchronization

- Server is the single source of truth for game state
- Every state change broadcast to all players in the room
- Each player can only see their own hole cards + community cards (information isolation)
- Agents and humans use the same message format (agents via gRPC, humans via WS)

## Game Data Standardization and Replay

### Standardized Game Data Format (MongoDB)

```json
{
  "gameId": "game_20260501_001",
  "roomId": "room_001",
  "timestamp": "2026-05-01T10:30:00Z",
  "config": {
    "smallBlind": 1,
    "bigBlind": 2,
    "maxPlayers": 6
  },
  "players": [
    { "seatId": "p1", "type": "human", "name": "Alice", "buyIn": 200 },
    { "seatId": "p2", "type": "agent", "agentId": "react_v1", "buyIn": 200 }
  ],
  "hands": [
    {
      "handNumber": 1,
      "dealer": "p1",
      "blinds": { "small": "p2", "big": "p3" },
      "holeCards": {
        "p1": ["Ah", "Kd"],
        "p2": ["7c", "7s"]
      },
      "board": ["Jh", "7d", "2c", "Kh", "3s"],
      "actions": [
        { "seat": "p3", "action": "CALL", "amount": 2, "phase": "PRE_FLOP", "timeMs": 1200 },
        { "seat": "p1", "action": "RAISE", "amount": 6, "phase": "PRE_FLOP", "timeMs": 3400 }
      ],
      "pot": { "main": 18, "sidePots": [] },
      "winner": { "seat": "p2", "handRank": "THREE_OF_KIND", "amount": 18 }
    }
  ],
  "summary": {
    "totalHands": 15,
    "duration": "00:45:30",
    "results": { "p1": -50, "p2": 80, "p3": -30 }
  }
}
```

### Replay Features

- Full playback: step-by-step replay of each hand along timeline
- Decision analysis: mark each decision point, show win rate changes
- Agent behavior audit: record agent reasoning process (if agent supports)
- Export formats: JSON (standard), CSV (statistical analysis)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 8 |
| Build | Maven |
| Web Framework | Spring Boot 2.7.x (last version supporting Java 8) |
| WebSocket | Spring WebSocket (STOMP) |
| gRPC | grpc-java + grpc-spring-boot-starter |
| Database | MongoDB (Spring Data MongoDB) |
| Serialization | Jackson (JSON) + Protobuf (gRPC) |
| Event System | Spring ApplicationEvent |
| Testing | JUnit 5 + Mockito |
| Logging | SLF4J + Logback |
| Containerization | Docker + docker-compose |

## Deployment Architecture (Initial)

```
┌─────────────────────────────────────────┐
│            Docker Compose               │
│                                         │
│  ┌──────────────┐  ┌─────────────────┐  │
│  │ poker-server │  │   MongoDB       │  │
│  │  :8080 (WS)  │  │   :27017        │  │
│  │  :9090 (gRPC)│  │                 │  │
│  └──────────────┘  └─────────────────┘  │
│                                         │
│  ┌──────────────┐                       │
│  │ Python Agent │  (optional, external) │
│  │  connects    │                       │
│  │  to :9090    │                       │
│  └──────────────┘                       │
└─────────────────────────────────────────┘
```

## Development Phases

1. **Phase 1**: Core engine + single player vs AI (built-in agents)
2. **Phase 2**: WebSocket multi-user + room system
3. **Phase 3**: gRPC agent bridge + Python agent example
4. **Phase 4**: Replay system + AI hint service

## Out of Scope (for now)

- Tournament system
- Authentication with OAuth/social login
- Real money transactions
- Mobile client
- Chat system
