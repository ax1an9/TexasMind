# Poker-Agent 房间集成 - 快速实现指南

**TL;DR**: 让 poker-agent 作为虚拟玩家加入房间对局，通过 gRPC 实时决策。

---

## 核心思路（3 个关键改动）

### 1️⃣ Java: 新建 GrpcAgentBridge (实现 BuiltinAgent 接口)

**目的**: 将 gRPC 调用包装成 Java 的 BuiltinAgent

```java
public class GrpcAgentBridge implements BuiltinAgent {
    private final PokerAgentStub stub;
    private final BuiltinAgent fallback = new SimpleHoldemAgent();
    private final int timeoutMs;
    
    @Override
    public Action decide(GameState state, PlayerState self) {
        try {
            // 1. 构建 DecisionRequest (状态 + 合法行动)
            DecisionRequest req = ProtoAdapter.toDecisionRequest(state, self);
            
            // 2. 远程调用 Python agent (带超时)
            ActionResponse resp = stub
                .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                .makeDecision(req);
            
            // 3. 转换回 Java Action
            return ProtoAdapter.toJavaAction(resp, state, self);
            
        } catch (Exception e) {
            log.warn("gRPC failed, fallback to local", e);
            return fallback.decide(state, self);  // 降级
        }
    }
}
```

**集成到现有流程**: 在 `GameRoom.handleCurrentPlayer()` 中，`aiAgent.decide()` 会自动调用 GrpcAgentBridge

### 2️⃣ Python: 实现 gRPC Server (PokerAgent 服务)

**目的**: 接收 DecisionRequest，用 PokerReactAgent 返回 ActionResponse

```python
class PokerAgentService(poker_agent_pb2_grpc.PokerAgentServicer):
    
    async def MakeDecision(self, request, context):
        """Called by Java GameRoom when it's AI's turn"""
        try:
            game_id = request.game_id
            
            # 1. 转换 proto 为 Python 对象
            decision_req = ProtoAdapter.to_decision_request(request)
            
            # 2. 调用 agent 决策
            agent = AgentRegistry.get_or_create(game_id)
            action_resp = await agent.decide(decision_req)
            
            # 3. 转换回 proto
            return ProtoAdapter.to_action_response(action_resp)
            
        except Exception as e:
            logger.error(f"Decision failed: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            return poker_agent_pb2.ActionResponse()
```

**启动**: `python -m poker_agent.grpc.server --port 9090`

### 3️⃣ Java: 在 RoomManager 中使用工厂创建 agent

**目的**: 根据配置选择使用 GRPC 还是本地 agent

```java
@Component
public class RoomManager {
    private final BuiltinAgent aiAgent;
    
    public RoomManager(GrpcAgentConfig config) {
        // 根据配置创建 agent
        if ("GRPC".equals(config.getStrategy())) {
            this.aiAgent = new GrpcAgentBridge(
                createGrpcStub(config),
                new SimpleHoldemAgent()  // 降级
            );
        } else {
            this.aiAgent = new SimpleHoldemAgent();
        }
    }
    
    public GameRoom createRoom(...) {
        return new GameRoom(..., aiAgent);
    }
}
```

---

## 完整文件结构

### Java 文件新增清单

```
poker-server/
├── src/main/java/com/texasholdem/server/
│   ├── config/
│   │   ├── GrpcAgentConfig.java           ← 配置类
│   │   └── AppConfig.java                 ← 修改：创建 aiAgent bean
│   ├── ai/
│   │   └── GrpcAgentBridge.java           ← 核心：gRPC agent 实现
│   └── grpc/
│       ├── PokerAgentClient.java          ← gRPC stub 管理
│       └── ProtoAdapter.java              ← 数据转换
└── src/main/resources/
    └── application.yaml                   ← 新增 poker.agent 配置
```

### Python 文件新增清单

```
poker-agent/
├── src/poker_agent/grpc/
│   ├── server.py                           ← gRPC 服务启动
│   ├── service.py                          ← PokerAgentService 实现
│   ├── adapter.py                          ← Proto <-> Python 转换
│   └── registry.py                         ← Agent 实例管理
└── scripts/
    └── run_agent_server.py                 ← 启动脚本
```

---

## 关键代码片段

### ProtoAdapter: 核心数据转换

```java
// Java
public class ProtoAdapter {
    
    // Java GameState → Proto DecisionRequest
    public static DecisionRequest toDecisionRequest(GameState state, PlayerState self) {
        return DecisionRequest.newBuilder()
            .setGameId(state.getGameId())
            .setGameState(GameStateView.newBuilder()
                .setPhase(GamePhase.valueOf(state.getPhase().name()))
                .setSelf(PlayerView.newBuilder()
                    .setSeatId(self.getSeatId())
                    .setChips(self.getChips())
                    .addAllHoleCards(state.getBoard().stream()
                        .map(ProtoAdapter::cardToProto)
                        .collect(toList()))
                    .build())
                // ... 其他字段
                .build())
            .addAllLegalActions(state.getLegalActions().stream()
                .map(ProtoAdapter::legalActionToProto)
                .collect(toList()))
            .build();
    }
    
    // Proto ActionResponse → Java Action
    public static Action toJavaAction(ActionResponse resp, GameState state, PlayerState self) {
        return switch (resp.getActionType()) {
            case FOLD -> new FoldAction(self.getSeatId());
            case CHECK -> new CheckAction(self.getSeatId());
            case CALL -> new CallAction(self.getSeatId());
            case BET -> new BetAction(self.getSeatId(), resp.getAmount());
            case RAISE -> new RaiseAction(self.getSeatId(), resp.getAmount());
            case ALL_IN -> new AllInAction(self.getSeatId());
        };
    }
}
```

```python
# Python
class ProtoAdapter:
    
    @staticmethod
    def to_decision_request(proto_request: poker_agent_pb2.DecisionRequest) -> DecisionRequest:
        """Proto → DecisionRequest"""
        game_state = GameState(
            phase=GamePhase[proto_request.game_state.phase.name],
            board=[ProtoAdapter.proto_to_card(c) for c in proto_request.game_state.board],
            self=PlayerView(
                seat_id=proto_request.game_state.self.seat_id,
                chips=proto_request.game_state.self.chips,
                hole_cards=[ProtoAdapter.proto_to_card(c) 
                           for c in proto_request.game_state.self.hole_cards],
            ),
            # ... 其他字段
        )
        return DecisionRequest(
            game_id=proto_request.game_id,
            game_state=game_state,
            legal_actions=[ActionType[la.action_type.name] for la in proto_request.legal_actions],
        )
    
    @staticmethod
    def to_action_response(action: ActionResponse) -> poker_agent_pb2.ActionResponse:
        """Python Action → Proto"""
        return poker_agent_pb2.ActionResponse(
            action_type=poker_agent_pb2.ActionType[action.action_type.name],
            amount=action.amount,
            reasoning=action.reasoning,
        )
```

---

## 启动顺序

### 第一次测试

```bash
# 终端1: 启动 poker-agent gRPC server
cd poker-agent
uv run python -m poker_agent.grpc.server --port 9090

# 终端2: 启动 Java poker-server
cd poker-server
mvn spring-boot:run -Dspring-boot.run.arguments="--poker.agent.strategy=GRPC"

# 终端3: 打开前端，创建房间，添加 AI bot
# 应该看到 AI 作为"poker_agent_1"加入房间
```

---

## 配置示例

### application.yaml

```yaml
poker:
  agent:
    strategy: GRPC           # BUILTIN | GRPC | HYBRID
    grpc:
      server_url: localhost:9090
      timeout_ms: 4000      # 决策超时 4 秒
      max_retries: 2
    fallback:
      strategy: BUILTIN     # 超时时降级到本地
```

### Python config/server.yaml

```yaml
grpc:
  host: 0.0.0.0
  port: 9090
  max_agents: 10

agent:
  mode: player              # player | advisor
  model: gpt-4o-mini
  timeout_ms: 3500          # 预留 500ms 网络延迟
```

---

## 测试清单

- [ ] Java GrpcAgentBridge 单元测试（mock gRPC）
- [ ] Python gRPC server 单元测试（mock agent）
- [ ] 集成测试：Java ↔ Python gRPC 通信
- [ ] 超时测试：Python 延迟 > 4s，验证降级
- [ ] 错误测试：Python 抛异常，验证降级
- [ ] 压力测试：10 个 agent 并发决策

---

## 故障排查

| 现象 | 原因 | 解决 |
|------|------|------|
| `Connection refused: 9090` | Python server 未启动 | `uv run python -m poker_agent.grpc.server` |
| `gRPC timeout` | Python agent 决策太慢 | 优化 agent 或增加 timeout_ms |
| `NO_SUCH_METHOD_ERROR` | Proto 版本不匹配 | 重新生成 proto: `uv run python scripts/generate_proto.py` |
| AI 总是 fold | 降级到 SimpleHoldemAgent | 检查 Python logs 中是否有错误 |

---

## 下一步

1. **实现 GrpcAgentBridge** (Java, ~200 行)
2. **实现 gRPC Server** (Python, ~150 行)
3. **集成测试** (~100 行)
4. **性能优化** (决策延迟 < 2s)
5. **监控告警** (gRPC 失败率、决策延迟分布)

