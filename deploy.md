# 本地部署指南

## 前置依赖

| 组件 | 版本要求 |
|------|---------|
| JDK | 1.8+ |
| Maven | 3.8+ |
| Python | 3.11+ |
| [uv](https://docs.astral.sh/uv/) | 最新版 |
| Node.js | 20+ |

---

## 1. 构建 Java 服务端

```bash
cd /mnt/d/workspace/texas
mvn clean install -DskipTests
```

## 2. 启动 Python Agent (gRPC, 端口 9090)

```bash
cd /mnt/d/workspace/texas/poker-agent

# 安装依赖（首次，uv 自动创建虚拟环境）
uv sync

# 启动 agent gRPC 服务器
uv run python -m poker_agent.grpc.server --config config/default.yaml --bind 0.0.0.0:9090
```

启动后日志应显示：
```
INFO: Starting PokerAgent gRPC server on 0.0.0.0:9090
```

## 3. 启动 Java 后端 (gRPC 模式)

```bash
cd /mnt/d/workspace/texas

# 使用 gRPC agent 模式启动
mvn spring-boot:run -pl poker-server -Dspring-boot.run.arguments='--agent.type=grpc --agent.grpc.host=localhost --agent.grpc.port=9090'
```

或使用 simple 模式（不需要 Python agent）：

```bash
mvn spring-boot:run -pl poker-server
```

服务端口: `8080`

## 4. 启动前端

```bash
cd /mnt/d/workspace/texas/poker-web
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`

---

## 一键启动脚本

保存为 `start.sh`：

```bash
#!/bin/bash
set -e

PROJECT_DIR="/mnt/d/workspace/texas"
AGENT_DIR="$PROJECT_DIR/poker-agent"

echo "=== 1. 启动 Python Agent (端口 9090) ==="
cd "$AGENT_DIR"
uv run python -m poker_agent.grpc.server --config config/default.yaml --bind 0.0.0.0:9090 &
AGENT_PID=$!
echo "Agent PID: $AGENT_PID"
sleep 2

echo "=== 2. 启动 Java 后端 (端口 8080) ==="
cd "$PROJECT_DIR"
mvn spring-boot:run -pl poker-server -Dspring-boot.run.arguments='--agent.type=grpc --agent.grpc.host=localhost --agent.grpc.port=9090' &
SERVER_PID=$!
echo "Server PID: $SERVER_PID"
sleep 5

echo "=== 3. 启动前端 (端口 5173) ==="
cd "$PROJECT_DIR/poker-web"
npm run dev &
WEB_PID=$!
echo "Web PID: $WEB_PID"

echo ""
echo "=== 所有服务已启动 ==="
echo "  前端:      http://localhost:5173"
echo "  后端 API:  http://localhost:8080"
echo "  Agent:     localhost:9090 (gRPC)"
echo ""
echo "按 Ctrl+C 停止所有服务"

trap "kill $AGENT_PID $SERVER_PID $WEB_PID 2>/dev/null; exit" SIGINT SIGTERM
wait
```

停止所有服务：

```bash
# 停止 Python Agent
kill $AGENT_PID

# 停止 Java 后端
kill $SERVER_PID

# 停止前端
kill $WEB_PID
```

---

## 验证部署

### 检查 Agent 是否就绪

```bash
# 使用 Python 客户端测试
cd /mnt/d/workspace/texas/poker-agent
uv run python -c "
import asyncio, grpc
from poker_agent.grpc.generated import poker_agent_pb2 as pb2
from poker_agent.grpc.generated import poker_agent_pb2_grpc as pb2_grpc

async def check():
    channel = grpc.aio.insecure_channel('localhost:9090')
    stub = pb2_grpc.PokerAgentStub(channel)
    resp = await stub.Ping(pb2.PingRequest())
    print(f'Agent OK: {resp.success}, version={resp.server_version}')
    await channel.close()

asyncio.run(check())
"
```

### 检查后端是否就绪

```bash
curl -s http://localhost:8080 | head -5
```

### 完整对局测试

1. 打开 `http://localhost:5173`
2. 创建房间
3. 点击 "添加 Bot" 添加 AI 玩家
4. 点击 "准备" → "开始"
5. 观察 Bot 使用 Python Agent 做决策（日志可见）

---

## 配置说明

### Agent 配置 (config/default.yaml)

```yaml
name: ProPokerAgent
mode: player
llm:
  provider: openai       # openai / anthropic / ollama
  model: gpt-4o-mini
  # api_key: sk-...      # 或设置环境变量 OPENAI_API_KEY
  temperature: 0.7
tools:
  hand_evaluation: { enabled: true }
  pot_odds: { enabled: true }
  opponent_modeling: { enabled: true }
  history_analysis: { enabled: true }
memory:
  persistence: file      # memory / file
  file_path: ./data/memory
```

### Java 后端配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `agent.type` | `simple` | `simple` 使用内置 AI，`grpc` 使用 Python Agent |
| `agent.grpc.host` | `localhost` | Agent gRPC 地址 |
| `agent.grpc.port` | `9090` | Agent gRPC 端口 |
| `agent.grpc.timeout-ms` | `4000` | 决策超时（毫秒），超时自动 fallback |

---

## 常见问题

**Q: Agent 启动报错 `ModuleNotFoundError: No module named 'poker_agent'`**
A: 确保在 `poker-agent` 目录下使用 `uv run python -m poker_agent.grpc.server` 启动，不要直接运行 `python server.py`。

**Q: Java 后端连接 Agent 超时**
A: 确认 Agent 已在 9090 端口启动。检查防火墙。超时后会自动 fallback 到 `SimpleHoldemAgent`。

**Q: 前端无法连接后端**
A: 确认后端在 8080 端口运行。检查 WebSocket 连接地址配置。
