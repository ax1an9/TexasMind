# Texas Hold'em Server API

## 连接

STOMP over WebSocket (SockJS)

```
ws://localhost:8080/ws/poker
```

连接时通过 header 传递用户身份:

```javascript
const client = new StompJs.Client({
  brokerURL: 'ws://localhost:8080/ws/poker',
  connectHeaders: { userId: 'player1' },
  // ...
});
```

## 客户端 → 服务端

### 创建房间

```
/app/room/create
```

```json
{
  "roomName": "我的房间",
  "maxPlayers": 6,
  "smallBlind": 1,
  "bigBlind": 2,
  "startingChips": 200
}
```

### 加入房间

```
/app/room/join
```

```json
{
  "roomId": "room_1",
  "seatPreference": -1
}
```

### 离开房间

```
/app/room/leave
```

```json
{
  "roomId": "room_1"
}
```

### 开始游戏

```
/app/room/start
```

```json
{
  "roomId": "room_1"
}
```

### 玩家操作

```
/app/game/action
```

```json
{
  "roomId": "room_1",
  "action": "RAISE",
  "amount": 20
}
```

action 可选值: `FOLD`, `CHECK`, `CALL`, `BET`, `RAISE`, `ALL_IN`

- FOLD / CHECK / CALL / ALL_IN: amount 可省略或为 0
- BET / RAISE: amount 为必填

### 请求 AI 提示

```
/app/game/hint
```

```json
{
  "roomId": "room_1"
}
```

## 服务端 → 客户端

### 房间列表 (广播)

```
/topic/lobby
```

```json
{
  "type": "ROOM_LIST",
  "rooms": [
    {
      "roomId": "room_1",
      "name": "我的房间",
      "currentPlayers": 2,
      "maxPlayers": 6,
      "status": "WAITING"
    }
  ]
}
```

status: `WAITING` | `PLAYING` | `CLOSED`

### 游戏状态 (私信，含手牌)

```
/user/{userId}/queue/game-state
```

```json
{
  "type": "GAME_STATE",
  "gameId": "room_1_1",
  "roomId": "room_1",
  "phase": "PRE_FLOP",
  "board": [],
  "currentPlayerId": "player1",
  "currentBet": 2,
  "pot": {
    "totalPot": 3,
    "mainPot": 3,
    "sidePots": []
  },
  "players": [
    {
      "seatId": "player1",
      "chips": 198,
      "folded": false,
      "allIn": false,
      "roundContribution": 1,
      "totalContribution": 1,
      "holeCards": [
        { "rank": "A", "suit": "HEARTS" },
        { "rank": "K", "suit": "DIAMONDS" }
      ]
    },
    {
      "seatId": "player2",
      "chips": 198,
      "folded": false,
      "allIn": false,
      "roundContribution": 2,
      "totalContribution": 2,
      "holeCards": null
    }
  ],
  "actionHistory": []
}
```

**信息隔离规则:**
- `holeCards`: 仅显示当前查看者自己的手牌，其他玩家为 null
- 摊牌阶段 (SHOWDOWN / SETTLED): 所有玩家手牌公开
- `board`: 公共牌始终可见
- 其他字段 (chips, folded, allIn, contributions): 始终可见

**phase 枚举:** `WAITING` | `PRE_FLOP` | `FLOP` | `TURN` | `RIVER` | `SHOWDOWN` | `SETTLED`

### 轮到你行动 (私信)

```
/user/{userId}/queue/action-required
```

```json
{
  "type": "ACTION_REQUIRED",
  "gameId": "room_1_1",
  "seatId": "player1",
  "timeLimitMs": 30000
}
```

### AI 提示结果 (私信)

```
/user/{userId}/queue/hint-result
```

```json
{
  "type": "HINT_RESULT",
  "gameId": "room_1",
  "suggestedAction": "CALL",
  "handStrength": 0.44,
  "potOdds": 0.25,
  "reasoning": "Hand strength: 44%, Pot odds: 25%. Favorable pot odds, call is profitable."
}
```

**handStrength:** 0.0 ~ 1.0，基于手牌等级映射:
| 牌型 | 强度 |
|------|------|
| HIGH_CARD | 0.12 |
| ONE_PAIR | 0.30 |
| TWO_PAIR | 0.44 |
| THREE_OF_A_KIND | 0.60 |
| STRAIGHT | 0.74 |
| FLUSH | 0.82 |
| FULL_HOUSE | 0.91 |
| FOUR_OF_A_KIND | 0.97 |
| STRAIGHT_FLUSH / ROYAL_FLUSH | 1.0 |

### 游戏结果 (广播)

```
/topic/room/{roomId}
```

```json
{
  "type": "GAME_RESULT",
  "gameId": "room_1_1",
  "winnerSeatId": "player1",
  "potAmount": 40,
  "handRank": null,
  "chipChanges": {
    "player1": 20,
    "player2": -20
  }
}
```

### 错误消息 (私信)

```
/user/{userId}/queue/error
```

```json
{
  "type": "ERROR",
  "message": "Cannot join room",
  "errorCode": "JOIN_FAILED"
}
```

常见 errorCode:
- `ROOM_NOT_FOUND` - 房间不存在
- `JOIN_FAILED` - 加入失败 (满员/已在游戏中)
- `INVALID_ACTION` - 非法操作 (不是你的回合 / 下注金额不合法)

## 典型流程

```
1. 连接 WebSocket (带 userId header)
2. 订阅 /topic/lobby 获取房间列表
3. /app/room/create 创建房间 → 收到房间列表更新
4. 另一玩家订阅 /topic/lobby → /app/room/join 加入
5. /app/room/start 开始游戏
6. 收到 /user/queue/game-state (含手牌)
7. 收到 /user/queue/action-required (轮到你)
8. /app/game/action 出牌
9. 重复 6-8 直到 SETTLED
10. 收到 /topic/room/{roomId} 的 GAME_RESULT
11. 可继续 /app/room/start 开始下一局
```

## 回放数据

每局结束后自动保存到:

```
data/replays/{sessionId}/
  hand-001.json
  hand-002.json
  session.json
```
