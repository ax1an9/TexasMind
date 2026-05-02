# Poker-Agent 房间对局集成设计

**版本**: 1.0  
**日期**: 2026-05-03  
**作者**: Architecture Team  

---

## 1. 概述

本文档设计如何将 `poker-agent` (Python LangChain ReAct Agent) 接入 `poker-server` 的房间对局系统。

**目标**:
- poker-agent 作为虚拟玩家加入房间，与人类玩家和本地 AI 对局
- 通过 gRPC 通信实现 Java 和 Python 的实时决策交互
- 支持灵活的 agent 策略切换（本地 SimpleHoldemAgent 或远程 Python agent）
- 优雅降级：Python agent 不可用时，自动回退到本地 agent

---

## 2. 系统架构

### 2.1 整体流程图

```
┌────────────────────────────────────────────────────────────────────┐
│                         Poker Server (Java)                        │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                      GameRoom                               │  │
│  │  ┌──────────────────────────────────────────────────────┐   │  │
│  │  │ Players:                                             │   │  │
│  │  │  - user1 (human)                                    │   │  │
│  │  │  - bot_1 (local AI)                                 │   │  │
│  │  │  - poker_agent_1 (remote Python agent)             │   │  │
│  │  └──────────────────────────────────────────────────────┘   │  │
│  │                                                              │  │
│  │  ┌──────────────────────────────────────────────────────┐   │  │
│  │  │ handleCurrentPlayer()                               │   │  │
│  │  │  ├─ if aiAgent → aiExecutor.submit()               │   │  │
│  │  │  │  └─ aiAgent.decide(state, self)                │   │  │
│  │  │  │     └─ GrpcAgentBridge                          │   │  │
│  │  │  │        ├─ gameStateToProto()                    │   │  │
│  │  │  │        ├─ gRPC.MakeDecision(DecisionRequest)    │   │  │
│  │  │  │        └─ protoToJavaAction()                   │   │  │
│  │  │  └─ if human → send ActionRequired message         │   │  │
│  │  └──────────────────────────────────────────────────────┘   │  │
│  │                                                              │  │
│  │ BuiltinAgent interface ←─ AgentFactory                      │  │
│  │  ├─ SimpleHoldemAgent (local)                              │  │
│  │  └─ GrpcAgentBridge (remote Python)                        │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                              ↑ gRPC (port 9090)                    │
└──────────────────────────────┼─────────────────────────────────────┘
                               │
                     ┌─────────┴──────────┐
                     │                    │
        ┌────────────▼─────────────┐  ┌──▼───────────────────────┐
        │  Poker-Agent gRPC Server │  │  Fallback: SimpleAgent   │
        │  (Python)                │  │  (timeout, error)        │
        │                          │  │                          │
        │  PokerReactAgent         │  └──────────────────────────┘
        │   ├─ Tools              │
        │   ├─ Memory             │
        │   └─ LLM                │
        │                          │
        └────────────────────────┘
```

### 2.2 关键组件

#### Java 侧

| 组件 | 职责 | 位置 |
|------|------|------|
| **AgentFactory** | 根据配置创建 agent 实例 | `poker-server/config/` |
| **GrpcAgentBridge** | 实现 BuiltinAgent，调用 Python gRPC | `poker-server/ai/` |
| **ProtoAdapter** | Java ↔ Proto 转换 | `poker-server/grpc/` |
| **GrpcAgentConfig** | agent 配置类 | `poker-server/config/` |

#### Python 侧

| 组件 | 职责 | 位置 |
|------|------|------|
| **GrpcServer** | 实现 PokerAgent 服务 | `poker-agent/src/poker_agent/grpc/` |
| **ProtoAdapter** | Proto ↔ Python object 转换 | `poker-agent/src/poker_agent/grpc/` |
| **AgentRegistry** | agent 实例生命周期管理 | `poker-agent/src/poker_agent/grpc/` |

---

## 3. 核心设计方案

### 3.1 玩家类型与 Agent 模型

#### 在房间中的玩家表示

```java
// Java 侧
public class PlayerConnection {
    private String userId;           // 唯一标识
    private String displayName;      // 显示名称
    private boolean isAiAgent;       // 是否为 AI
    // 对于远程 agent，userId 格式：agent_{agent_id}
    // 例如：agent_poker_react_1
}
```

#### Agent 加入房间流程

1. **本地 Agent** (SimpleHoldemAgent)
   - 形式：自动添加（`/room/add-bot`）
   - PlayerId: `Bot_N`

2. **远程 Agent** (Python PokerReactAgent)
   - 形式：通过 gRPC 通信，表现为虚拟玩家
   - PlayerId: 可配置，推荐 `agent_react_{session_id}`
   - 启动时自动加入所有活跃房间（或指定房间）

### 3.2 gRPC 通信协议

使用现有 [poker_agent.proto](../../../poker-agent/proto/poker_agent.proto)

**关键 RPC 调用**：

```protobuf
service PokerAgent {
    rpc MakeDecision(DecisionRequest) returns (ActionResponse);
}
```

**DecisionRequest 构造**：

```java
DecisionRequest req = DecisionRequest.newBuilder()
    .setGameId(roomId + "_" + handNumber)
    .setGameState(gameStateToProto(currentGameState, self))
    .addAllLegalActions(legalActionsToProto(currentGameState.getLegalActions()))
    .setTiming(TimingInfo.newBuilder()
        .setDecisionDeadlineMs(System.currentTimeMillis() + 5000)
        .setMaxThinkTimeMs(4000)
        .build())
    .build();

ActionResponse resp = grpcStub.makeDecision(req);
```

**ActionResponse 解析**：

```java
Action action = protoToJavaAction(resp, currentGameState, self);
```

### 3.3 Agent 策略配置

#### Java 配置文件示例

```yaml
# application.yaml
poker:
  agent:
    strategy: GRPC                          # BUILTIN | GRPC | HYBRID
    grpc:
      enabled: true
      server_url: localhost:9090
      timeout_ms: 5000
      channel:
        keep_alive_time: 30
        keep_alive_timeout: 5
        idle_timeout: 300
    fallback:
      strategy: BUILTIN                    # 超时/错误时降级
      builtin_type: SIMPLE_HOLDEM
```

#### Agent 选择逻辑

```java
public class AgentFactory {
    public BuiltinAgent createAgent(GrpcAgentConfig config) {
        if ("BUILTIN".equals(config.getStrategy())) {
            return new SimpleHoldemAgent();
        }
        
        if ("GRPC".equals(config.getStrategy())) {
            BuiltinAgent fallback = new SimpleHoldemAgent();
            return new GrpcAgentBridge(
                config.getGrpc(),
                fallback
            );
        }
        
        // HYBRID: 根据当前可用性选择
        return new HybridAgentBridge(
            new GrpcAgentBridge(config.getGrpc(), null),
            new SimpleHoldemAgent(),
            config.getHybridThreshold()
        );
    }
}
```

### 3.4 超时与错误处理

#### GrpcAgentBridge 实现

```java
public class GrpcAgentBridge implements BuiltinAgent {
    private final PokerAgentStub grpcStub;
    private final BuiltinAgent fallback;
    private final int timeoutMs;
    
    @Override
    public Action decide(GameState state, PlayerState self) {
        try {
            DecisionRequest req = buildRequest(state, self);
            ActionResponse resp = grpcStub
                .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                .makeDecision(req);
            
            return protoToAction(resp, state, self);
        } catch (StatusRuntimeException | TimeoutException e) {
            log.warn("gRPC agent failed, falling back to local", e);
            return fallback.decide(state, self);
        }
    }
}
```

#### 故障场景处理

| 故障 | 处理方式 |
|------|--------|
| Python Server 未启动 | 启动时检测，日志警告，使用 fallback |
| 单次决策超时 | catch TimeoutException，使用 fallback |
| 决策返回无效 | 验证 proto message，invalid 时使用 fallback |
| 网络连接断开 | gRPC 自动重连，间歇性故障自动切换 fallback |

---

## 4. 实现步骤

### Phase 1: Java 侧核心类（3 天）

1. **创建 GrpcAgentConfig**
   - 配置类，包含 strategy、server_url、timeout 等参数

2. **创建 GrpcProtoAdapter**
   - GameState → DecisionRequest
   - ActionResponse → Action

3. **创建 GrpcAgentBridge**
   - 实现 BuiltinAgent 接口
   - 集成 gRPC stub
   - 实现超时/降级逻辑

4. **创建 AgentFactory**
   - 根据配置创建 agent 实例

5. **修改 RoomManager**
   - 使用 AgentFactory 创建 agent

### Phase 2: Python 侧 gRPC 服务（3 天）

1. **创建 GrpcServerImpl**
   - 实现 PokerAgent.MakeDecision RPC
   - 并发处理、超时控制

2. **创建 ProtoAdapter**
   - Proto → PokerReactAgent 输入
   - ActionResponse ← agent 输出

3. **创建 AgentRegistry**
   - agent 实例管理、生命周期

4. **启动脚本**
   - `run_agent_server.py` 启动 gRPC server

### Phase 3: 集成测试（2 天）

1. **单元测试**
   - Java Adapter 转换正确性
   - Python Adapter 转换正确性

2. **集成测试**
   - 本地多线程模拟
   - Java ↔ Python gRPC 通信
   - 超时/降级场景

3. **性能测试**
   - 决策延迟
   - 并发 agent 数量

---

## 5. 数据格式规范

### 5.1 GameState → DecisionRequest 转换

```java
// 完整的转换映射
GameStateView proto = GameStateView.newBuilder()
    .setPhase(GamePhase.valueOf(state.getPhase().name()))
    .addAllBoard(state.getBoard().stream()
        .map(ProtoAdapter::cardToProto)
        .collect(toList()))
    .setSelf(PlayerView.newBuilder()
        .setSeatId(self.getSeatId())
        .setChips(self.getChips())
        .addAllHoleCards(self.getHoleCards().stream()
            .map(ProtoAdapter::cardToProto)
            .collect(toList()))
        .setRoundContribution(self.getRoundContribution())
        .setIsAllIn(self.isAllIn())
        .setIsFolded(self.isFolded())
        .build())
    .addAllOpponents(state.getPlayers().stream()
        .filter(p -> !p.getSeatId().equals(self.getSeatId()))
        .map(ProtoAdapter::playerToOpponentProto)
        .collect(toList()))
    // ... pot, action_history, etc.
    .build();
```

### 5.2 Action 类型映射

```java
// Java Action → Proto ActionType
Map<Class<?>, ActionType> actionMap = Map.ofEntries(
    Map.entry(FoldAction.class, ActionType.FOLD),
    Map.entry(CheckAction.class, ActionType.CHECK),
    Map.entry(CallAction.class, ActionType.CALL),
    Map.entry(BetAction.class, ActionType.BET),
    Map.entry(RaiseAction.class, ActionType.RAISE),
    Map.entry(AllInAction.class, ActionType.ALL_IN)
);

// Proto ActionResponse → Java Action
Action javaAction = switch (response.getActionType()) {
    case FOLD -> new FoldAction(seatId);
    case CHECK -> new CheckAction(seatId);
    case CALL -> new CallAction(seatId);
    case BET -> new BetAction(seatId, response.getAmount());
    case RAISE -> new RaiseAction(seatId, response.getAmount());
    case ALL_IN -> new AllInAction(seatId);
    default -> throw new IllegalArgumentException();
};
```

---

## 6. 配置示例

### Java 启动配置

```yaml
# application-prod.yaml
poker:
  agent:
    strategy: GRPC
    grpc:
      enabled: true
      server_url: localhost:9090
      timeout_ms: 4000
    fallback:
      strategy: BUILTIN
      builtin_type: SIMPLE_HOLDEM
```

### Python 启动脚本

```bash
# 启动 gRPC server
uv run python -m poker_agent.grpc.server \
    --port 9090 \
    --config config/default.yaml \
    --max_agents 10
```

---

## 7. 监控与日志

### 关键指标

```
- agent.decision.latency_ms: 决策延迟分布
- agent.decision.fallback_count: 降级次数
- agent.grpc.errors: gRPC 错误计数
- agent.grpc.timeout: 超时次数
- room.agent_count: 每个房间中的 agent 数
```

### 日志策略

```java
// Java 侧
log.info("AI Decision: player={}, action={}, latency={}ms", 
    playerId, action, latency);
log.warn("Agent decision fallback", exception);
log.error("gRPC connection failed", exception);

# Python 侧
logger.info(f"MakeDecision: game_id={game_id}, player={seat_id}, decision_time={elapsed}ms")
logger.warning(f"Invalid legal_actions: {legal_actions}")
logger.error(f"Agent error", exc_info=True)
```

---

## 8. 安全性考虑

- **gRPC TLS**: 生产环境启用 TLS 加密
- **认证**: gRPC metadata 传递 agent token
- **限流**: 单 agent 的 QPS 限制
- **沙箱**: Python agent 运行环境隔离

---

## 9. 性能目标

| 指标 | 目标值 |
|------|------|
| 决策延迟 P95 | < 2s |
| 决策延迟 P99 | < 3s |
| gRPC 连接建立时间 | < 100ms |
| 并发房间数 | 100+ |
| 并发 agent 数 | 50+ |

---

## 10. 后续扩展

### 10.1 Agent 热替换

无需重启服务器，动态切换 agent 策略。

### 10.2 Agent 性能评估

记录每个 agent 的胜率、决策延迟等指标，用于策略优化。

### 10.3 Agent 联合训练

使用对局回放数据fine-tune Python LLM agent。

---

## 附录 A: 文件清单

### Java 文件
- `poker-server/src/.../config/GrpcAgentConfig.java`
- `poker-server/src/.../ai/GrpcAgentBridge.java`
- `poker-server/src/.../ai/AgentFactory.java`
- `poker-server/src/.../grpc/ProtoAdapter.java`
- `poker-server/src/.../grpc/PokerAgentClient.java`

### Python 文件
- `poker-agent/src/poker_agent/grpc/server.py`
- `poker-agent/src/poker_agent/grpc/adapter.py`
- `poker-agent/src/poker_agent/grpc/registry.py`
- `poker-agent/scripts/run_agent_server.py`

### 配置文件
- `poker-server/src/.../resources/application.yaml`
- `poker-agent/config/server.yaml`

---

## 附录 B: 时序图

```
Player's Turn (AI Agent)
========================

GameRoom                GrpcAgentBridge         Python gRPC Server
   │                          │                         │
   ├─ handleCurrentPlayer()   │                         │
   │  (detect AI agent)       │                         │
   │                          │                         │
   ├─ aiExecutor.submit()     │                         │
   │                          │                         │
   │                  ┌────── call decide() ─────┐      │
   │                  │                          │      │
   │                  ├─ build DecisionRequest   │      │
   │                  │                          │      │
   │                  ├─ gRPC.MakeDecision() ────────┐  │
   │                  │ (with 4s timeout)            │  │
   │                  │                              ├─ receive request
   │                  │                              │
   │                  │                              ├─ PokerReactAgent.decide()
   │                  │                              │
   │                  │                         ┌────┤ LLM reasoning + tools
   │                  │                         │    │
   │                  │                         │    ├─ return ActionResponse
   │                  │                    ┌────┤    │
   │                  │◄─────────────────────┘   │    │
   │                  │ (response)                    │
   │                  │                              │
   │                  ├─ protoToAction()             │
   │                  │                              │
   │                  └──┬─ return Action ───────────┴──
   │                     │
   │◄────────────────────┘
   │
   ├─ applyAction(aiAction)
   │
   └─ broadcastGameState()
```

