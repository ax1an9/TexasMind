# Poker-Agent 房间对局集成 - 设计方案总结

---

## 📋 执行摘要

### 目标
让 poker-agent (Python LangChain ReAct Agent) 作为虚拟玩家加入 poker-server 的房间对局，通过 gRPC 进行实时决策交互。

### 核心方案：gRPC 实时决策 + 自动降级

```
GameRoom (Java)
    ↓
AI轮到行动
    ↓
GrpcAgentBridge.decide()
    ├─ 构建 DecisionRequest proto
    ├─ 调用 Python gRPC (4s超时)
    ├─ 返回 ActionResponse
    └─ 异常 → 降级到 SimpleHoldemAgent
    ↓
applyAction() + 广播
```

### 投入产出

| 指标 | 值 |
|------|-----|
| 开发工作量 | ~75 小时（1.5 人周） |
| 核心文件数 | 5 个 (Java) + 4 个 (Python) |
| 代码行数 | ~800 行 |
| 部署复杂度 | 中等（gRPC + 配置） |
| 性能目标 | P95 < 1.5s，P99 < 2.5s |
| AI 智能度 | 大幅提升（使用 LLM） |

---

## 🎯 设计要点（快速理解）

### 1️⃣ 接入方式：gRPC 桥接

**不修改现有游戏逻辑**，只在 `BuiltinAgent` 层添加中间件：

```
BuiltinAgent (接口)
    ├─ SimpleHoldemAgent (原有本地 agent)
    └─ GrpcAgentBridge (新增 gRPC 桥接) ← 添加这里
```

**关键改动点**:
- Java: RoomManager 使用工厂创建 agent
- Python: 新增 gRPC server 实现 PokerAgent 服务

### 2️⃣ 数据流转

```
GameState (Java)
    ↓ ProtoAdapter.toDecisionRequest()
DecisionRequest (proto)
    ↓ gRPC.MakeDecision()
PokerReactAgent.decide() (Python)
    ↓ ProtoAdapter.toActionResponse()
ActionResponse (proto)
    ↓ ProtoAdapter.toJavaAction()
Action (Java)
```

### 3️⃣ 容错机制

```
if (gRPC 决策成功):
    return gRPC 结果
elif (gRPC 超时 > 4s):
    return SimpleHoldemAgent.decide()
elif (网络错误):
    return SimpleHoldemAgent.decide()
else:
    return SimpleHoldemAgent.decide()
```

---

## 📁 文件清单与改动范围

### Java 侧 - 新增 4 个文件

| 文件 | 行数 | 职责 |
|------|-----|------|
| `GrpcAgentConfig.java` | 50 | 配置管理 |
| `GrpcAgentBridge.java` | 80 | gRPC 客户端 + 降级 |
| `ProtoAdapter.java` | 200 | 数据双向转换 |
| `PokerAgentClient.java` | 40 | gRPC stub 管理 |

### Java 侧 - 修改 2 个文件

| 文件 | 修改点 |
|------|-------|
| `AppConfig.java` | +15 行：createAgent() 工厂逻辑 |
| `pom.xml` | +3 行：grpc-stub 依赖 |

### Python 侧 - 新增 4 个文件

| 文件 | 行数 | 职责 |
|------|-----|------|
| `server.py` | 60 | gRPC 服务启动 |
| `service.py` | 80 | PokerAgent RPC 实现 |
| `adapter.py` | 150 | Proto ↔ Python 转换 |
| `registry.py` | 70 | Agent 实例管理 |

### Python 侧 - 修改 1 个文件

| 文件 | 修改点 |
|------|-------|
| `pyproject.toml` | +1 行：grpcio 依赖 |

### 配置文件 - 新增 1 个文件

| 文件 | 说明 |
|------|------|
| `application.yaml` | poker.agent.* 配置块 |

---

## 🔧 三步快速实现

### 第一步：Java 核心类（~200 行代码，1 天）

1. **复制代码模板** (见 [code-templates.md](2026-05-03-code-templates.md))
   - GrpcAgentConfig.java
   - GrpcAgentBridge.java
   - ProtoAdapter.java

2. **修改 AppConfig.java**
   ```java
   @Bean
   public BuiltinAgent builtinAgent(GrpcAgentConfig config) {
       if ("GRPC".equals(config.getStrategy())) {
           return new GrpcAgentBridge(config.getGrpc(), new SimpleHoldemAgent());
       }
       return new SimpleHoldemAgent();
   }
   ```

3. **添加 Maven 依赖**
   ```xml
   <dependency>
       <groupId>io.grpc</groupId>
       <artifactId>grpc-stub</artifactId>
       <version>1.60.0</version>
   </dependency>
   ```

### 第二步：Python gRPC Server（~200 行代码，1 天）

1. **复制代码模板**
   - server.py
   - service.py
   - adapter.py
   - registry.py

2. **启动脚本**
   ```bash
   uv run python -m poker_agent.grpc.server --port 9090 --config config/default.yaml
   ```

3. **健康检查**
   ```python
   grpcurl -plaintext localhost:9090 list
   ```

### 第三步：集成测试（~100 行代码，0.5 天）

1. **本地端到端测试**
   ```bash
   # 终端 1: Java server
   mvn spring-boot:run -Dspring-boot.run.arguments="--poker.agent.strategy=GRPC"
   
   # 终端 2: Python gRPC server
   uv run python -m poker_agent.grpc.server
   
   # 终端 3: 前端客户端
   # 打开浏览器，创建房间，添加 AI bot
   # 验证 AI 加入并做出决策
   ```

2. **验证点**
   - ✅ gRPC 连接建立成功
   - ✅ AI 玩家加入房间
   - ✅ 轮到 AI 时，3s 内返回决策
   - ✅ AI 决策合理（不总是 fold）
   - ✅ Python server 宕机时，自动降级

---

## 📊 架构决策树

```
当前系统需要加入智能 AI 对手吗？
├─ 不需要 → 保持原有 SimpleHoldemAgent ✓
├─ 需要，且性能要求不高 → 采用 gRPC 方案 (推荐) ✓
│  └─ 用户反馈好 → 继续优化 gRPC 性能
│     └─ 2-3 月后数据充分 → 迁移到离线策略 (可选)
└─ 需要，但对延迟极敏感 → 直接用离线策略
   └─ 需要大量训练数据，等待 gRPC 积累
```

---

## 🚀 实施时间表

| 周 | 里程碑 | 工作 |
|----|-------|------|
| 第1周 | **设计完成** ✓ | 架构设计、代码审查 |
| 第2周 | **Java 实现** | GrpcAgentBridge、Adapter、Config |
| 第3周 | **Python 实现** | gRPC server、service、adapter |
| 第4周 | **集成测试** | E2E 测试、性能优化、压测 |
| 第5周 | **灰度上线** | 10% 流量 → 50% → 100% |
| 第6-8周 | **数据积累** | 收集 10K+ 对局数据 |

---

## 🔍 验收标准

### 功能验收

- [ ] AI 玩家可以加入房间（显示为"poker_agent_1"）
- [ ] 轮到 AI 时，在 4s 内返回有效决策
- [ ] Python server 离线时，自动降级到本地 agent
- [ ] AI 决策符合扑克逻辑（不随意全 in）
- [ ] 支持多个 AI 对手同时在线

### 性能验收

- [ ] 决策延迟 P95 < 1.5s
- [ ] 决策延迟 P99 < 2.5s
- [ ] 并发房间数 > 50
- [ ] gRPC 连接建立时间 < 100ms

### 可靠性验收

- [ ] Python server 宕机时，游戏继续进行
- [ ] 网络抖动时，自动重连
- [ ] 决策失败率 < 1%

---

## ⚠️ 风险与缓解

| 风险 | 概率 | 缓解 |
|------|------|------|
| Python server 宕机 | 中 | 自动降级 + 监控告警 |
| gRPC 延迟过高 | 低 | 模型优化、本地缓存 |
| Proto 版本冲突 | 低 | CI 自动化生成 proto |
| LLM API 配额 | 中 | 本地模型 fallback |
| 并发数过多 | 低 | 连接池 + 限流 |

---

## 📈 后续优化方向

### 短期（1 个月）
- 性能优化（决策延迟 < 1s）
- 监控告警完善
- 灰度上线验证

### 中期（2-3 个月）
- 数据积累（10K+ 对局）
- 本地模型探索
- A/B 测试框架

### 长期（3-6 个月）
- 迁移到离线策略表（可选）
- Agent 性能排行榜
- 联合训练系统

---

## 📚 关键文档

| 文档 | 用途 |
|------|------|
| [2026-05-03-poker-agent-room-integration.md](2026-05-03-poker-agent-room-integration.md) | **完整设计方案**（技术深度） |
| [2026-05-03-quick-start-guide.md](2026-05-03-quick-start-guide.md) | **快速参考**（实现指南） |
| [2026-05-03-architecture-comparison.md](2026-05-03-architecture-comparison.md) | **方案对比**（架构决策） |
| [2026-05-03-code-templates.md](2026-05-03-code-templates.md) | **代码模板**（直接复用） |
| [2026-05-02-poker-react-agent-design.md](2026-05-02-poker-react-agent-design.md) | **Agent 设计**（背景知识） |
| [2026-05-01-texas-holdem-server-design.md](2026-05-01-texas-holdem-server-design.md) | **系统架构**（全局理解） |

---

## 🎓 学习路径

**如果你是...**

- **架构师**: 先读 [architecture-comparison.md](2026-05-03-architecture-comparison.md)，理解方案选型
- **Java 工程师**: 先读 [quick-start-guide.md](2026-05-03-quick-start-guide.md)，然后参考 [code-templates.md](2026-05-03-code-templates.md)
- **Python 工程师**: 先读 [2026-05-02-poker-react-agent-design.md](2026-05-02-poker-react-agent-design.md)，然后参考 [code-templates.md](2026-05-03-code-templates.md)
- **项目经理**: 读本文 + [architecture-comparison.md](2026-05-03-architecture-comparison.md) 的 ROI 部分
- **QA/测试**: 读 [quick-start-guide.md](2026-05-03-quick-start-guide.md) 的测试清单

---

## ✅ 行动项

### 立即执行
1. [ ] 所有人读本文档 (15 min)
2. [ ] Java 工程师启动第一步 (1 day)
3. [ ] Python 工程师启动第二步 (1 day)

### 本周
1. [ ] 完成 Java 实现 + 单元测试
2. [ ] 完成 Python 实现 + 单元测试
3. [ ] 本地集成测试通过

### 下周
1. [ ] 性能优化（延迟 < 2s）
2. [ ] 监控告警部署
3. [ ] 灰度上线准备

---

## 📞 快速Q&A

**Q: 为什么不直接用 Python 实现游戏引擎？**
A: 现有 Java 引擎已稳定，gRPC 方案最小化改动，风险最低。

**Q: gRPC 会不会有网络延迟问题？**
A: 设计了 4s 超时 + 自动降级，最坏情况下降到本地 agent。

**Q: 可以支持多个 Python agent 吗？**
A: 可以，AgentRegistry 支持多实例管理。

**Q: 离线策略和 gRPC 哪个更好？**
A: gRPC 更灵活（可实时更新），离线更快（< 1ms）。建议先用 gRPC，数据充足后迁移。

**Q: 成本高不高？**
A: gRPC 自建服务器 $10-20/月，LLM API $1-10/月，合计 $15-50/月。

---

## 📞 联系方式

- 架构相关: [@architect]
- Java 实现: [@java-lead]
- Python 实现: [@python-lead]
- 测试验收: [@qa-lead]

---

**版本**: 1.0  
**最后更新**: 2026-05-03  
**状态**: ✅ 设计完成，待实现

