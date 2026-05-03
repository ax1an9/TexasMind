# In-Game Hint System

This document explains how the in-game poker hints are calculated, displayed, and how the different view modes work.

---

## Overview

The hint system provides real-time decision assistance during poker gameplay. When a player requests a hint (manually or automatically on phase change), the backend analyzes the current game state and returns:

- **Suggested action**: FOLD, CALL, CHECK, or RAISE
- **Hand strength**: A 0-100% score representing how strong the player's hand is
- **Pot odds**: The percentage of equity needed to break even on a call
- **Reasoning**: A human-readable explanation of the recommendation

---

## View Modes

The hint panel offers three display modes, selectable via a toggle. The selection persists across sessions.

| Mode | Target Audience | Content |
|------|----------------|---------|
| **简洁 (Simple)** | Complete beginners | Action icon + one plain sentence, no numbers |
| **标准 (Standard)** | Most players | Action label + strength/odds progress bars + reasoning |
| **详细 (Detailed)** | Advanced players | Full factor breakdown with individual contributions + pot odds math |

---

## Hand Strength Calculation

Hand strength is a heuristic score from 0.0 to 1.0. Two different algorithms are used depending on the game phase.

### Pre-Flop (only 2 hole cards)

A formula based on card properties:

| Factor | Condition | Bonus |
|--------|-----------|-------|
| **Base** | Always | `0.12 + (highCard - 2) / 12 × 0.2` (scales with high card rank) |
| **Pair** | Both cards same rank | +0.45 |
| **Suited** | Both cards same suit | +0.05 |
| **Connected** | Cards adjacent (gap=1) | +0.08 |
| **One-gapper** | Gap of 2 | +0.04 |
| **High card** | High card ≥ Jack (11) | +0.08 |

Result is clamped to [0.0, 1.0].

**Example**: Pocket Aces (A♠ A♥) = 0.12 + 0.2 + 0.45 + 0.08 = 0.85

### Post-Flop (5+ known cards)

Uses `HandEvaluator.evaluateBest()` to find the best 5-card hand from all known cards, then maps the hand rank to a score:

| Hand Rank | Score |
|-----------|-------|
| High Card | 0.12 |
| One Pair | 0.30 |
| Two Pair | 0.44 |
| Three of a Kind | 0.60 |
| Straight | 0.74 |
| Flush | 0.82 |
| Full House | 0.91 |
| Four of a Kind | 0.97 |
| Straight Flush / Royal Flush | 1.00 |

---

## Pot Odds Calculation

```
potOdds = toCall / (totalPot + toCall)
```

Where:
- `toCall` = currentBet - player's round contribution (chips needed to stay in)
- `totalPot` = current total pot size

**Interpretation**: This is the minimum equity (win probability) needed to make a call break-even. If hand strength > pot odds, calling is profitable in the long run.

**Example**: Pot is $100, you need to call $25 → potOdds = 25/(100+25) = 20%. You need at least 20% equity to call.

---

## Decision Logic

The hint advisor uses this decision tree:

```
Is there a bet to call? (toCall == 0?)
├── YES (no bet facing):
│   ├── strength >= 0.75 → RAISE ("牌力很强，建议加注获取价值")
│   ├── strength >= 0.50 → CHECK ("牌力中等，过牌看看下一张牌")
│   └── strength < 0.50  → CHECK ("牌力较弱，过牌争取免费看牌")
│
└── NO (facing a bet):
    ├── strength < potOdds × 0.7 → FOLD ("牌力不足以支撑底池赔率")
    ├── strength >= 0.75         → RAISE ("牌力很强，加注获取价值")
    ├── strength >= potOdds      → CALL ("底池赔率合适，跟注有利可图")
    └── strength < potOdds       → CALL ("边缘局面，赔率接近时可以跟注")
```

**Key thresholds**:
- **RAISE**: strength ≥ 0.75 (strong hand)
- **FOLD**: strength < potOdds × 0.7 (hand far too weak for the price)
- **CALL**: strength ≥ potOdds (mathematically profitable)
- **CHECK**: default when no bet to face and hand isn't strong enough to raise

---

## Data Flow

```
Player clicks "获取提示" or phase changes to FLOP/TURN/RIVER
    │
    ▼
Frontend sends STOMP message to /app/game/hint
    │
    ▼
ClientMessageHandler.requestHint()
    │
    ▼
GameRoom.getHint(userId)
    │
    ▼
HintAdvisor.analyze(gameState, playerState)
    ├── estimateStrength() → StrengthEstimate(strength, factors, handRankName)
    ├── compute potOdds
    └── apply decision tree → HintResult (9 fields)
    │
    ▼
ClientMessageHandler maps HintResult → HintResultMessage
    │
    ▼
BroadcastService sends to /user/queue/hint-result
    │
    ▼
Frontend HintPanel receives and renders based on viewMode:
    ├── SimpleView: icon + action + simpleReasoning
    ├── StandardView: action + strength bar + odds bar + reasoning
    └── DetailedView: action + hand rank + factor breakdown + odds math + reasoning
```

---

## Strength Factors (Detailed View)

In detailed mode, the hint panel shows a breakdown of what contributed to the hand strength score.

### Pre-Flop Factors

Each bonus from the pre-flop formula is shown as a separate row:

- **基础牌力**: Base score from high card rank
- **对子加成**: +0.45 for pocket pairs
- **同花加成**: +0.05 for suited cards
- **连牌加成**: +0.08 for connected cards (gap=1)
- **邻牌加成**: +0.04 for one-gappers (gap=2)
- **高牌加成**: +0.08 when high card is Jack or above

### Post-Flop Factors

A single factor showing:
- **牌型评估**: The hand rank score with the Chinese hand rank name

---

## Code Locations

### Backend

| File | Purpose | Key Lines |
|------|---------|-----------|
| `poker-ai/.../HintAdvisor.java` | Core hint calculation | L16-52: `analyze()` decision tree |
| | | L58-104: `estimateStrength()` with factor building |
| | | L106-115: `score()` HandRank→strength mapping |
| | | L117-130: `handRankChinese()` Chinese name mapping |
| `poker-ai/.../HintResult.java` | Hint result data model | L4-9: fields (9 total) |
| | | L47-60: `StrengthFactor` inner class |
| `poker-server/.../ClientMessageHandler.java` | WebSocket handler | L153-175: `requestHint()` maps to protocol DTO |
| `poker-server/.../GameRoom.java` | Room-level hint access | L302-314: `getHint()` acquires lock, calls advisor |

### Protocol

| File | Purpose |
|------|---------|
| `poker-common/.../HintResultMessage.java` | Wire format with all 10 fields + `StrengthFactorDto` |
| `poker-common/.../HintRequest.java` | Client request (roomId) |

### Frontend

| File | Purpose |
|------|---------|
| `poker-web/src/components/HintPanel.jsx` | Main component with 3 view modes |
| `poker-web/src/components/HintPanel.module.css` | Styles for all modes |
| `poker-web/src/pages/Game.jsx` | L32-34: auto-hint on phase change; L97-99: manual hint request |

### Core Hand Evaluation

| File | Purpose |
|------|---------|
| `poker-core/.../eval/HandEvaluator.java` | 5-card hand evaluation (C(n,5) enumeration) |
| `poker-core/.../eval/HandValue.java` | Hand rank + tiebreakers |
| `poker-core/.../model/HandRank.java` | Enum: HIGH_CARD through ROYAL_FLUSH |
