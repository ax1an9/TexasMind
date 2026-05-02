# Poker-Agent 房间集成 - 代码模板

本文件包含核心类的代码框架，可直接用于实现。

---

## Java 侧

### 1. GrpcAgentConfig.java

```java
package com.texasholdem.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "poker.agent")
public class GrpcAgentConfig {
    
    private String strategy = "BUILTIN"; // BUILTIN, GRPC, HYBRID
    
    private GrpcSettings grpc = new GrpcSettings();
    private FallbackSettings fallback = new FallbackSettings();
    
    @Data
    public static class GrpcSettings {
        private boolean enabled = true;
        private String serverUrl = "localhost:9090";
        private int timeoutMs = 4000;
        private int maxRetries = 2;
        private int keepAliveMs = 30000;
    }
    
    @Data
    public static class FallbackSettings {
        private String strategy = "BUILTIN";
        private String builtinType = "SIMPLE_HOLDEM";
    }
}
```

### 2. GrpcAgentBridge.java

```java
package com.texasholdem.server.ai;

import com.texasholdem.common.protocol.*;
import com.texasholdem.core.model.*;
import com.texasholdem.server.config.GrpcAgentConfig;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcAgentBridge implements BuiltinAgent {
    private static final Logger log = LoggerFactory.getLogger(GrpcAgentBridge.class);
    
    private final ManagedChannel channel;
    private final PokerAgentGrpc.PokerAgentBlockingStub stub;
    private final BuiltinAgent fallback;
    private final GrpcAgentConfig.GrpcSettings grpcConfig;
    
    public GrpcAgentBridge(GrpcAgentConfig.GrpcSettings config, BuiltinAgent fallback) {
        this.grpcConfig = config;
        this.fallback = fallback != null ? fallback : new SimpleHoldemAgent();
        
        // 构建 gRPC 通道
        String[] parts = config.getServerUrl().split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9090;
        
        this.channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .keepAliveWithoutCalls(true)
            .keepAliveTime(config.getKeepAliveMs(), TimeUnit.MILLISECONDS)
            .build();
        
        this.stub = PokerAgentGrpc.newBlockingStub(channel);
        
        log.info("gRPC Agent Bridge initialized: {}:{}", host, port);
    }
    
    @Override
    public Action decide(GameState state, PlayerState self) {
        try {
            // 1. 构建 DecisionRequest
            DecisionRequest request = ProtoAdapter.toDecisionRequest(
                state, 
                self, 
                state.getLegalActions()
            );
            
            // 2. 调用 gRPC (带超时)
            ActionResponse response = stub
                .withDeadlineAfter(grpcConfig.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .makeDecision(request);
            
            // 3. 转换回 Java Action
            Action action = ProtoAdapter.toJavaAction(response, state, self);
            
            log.debug("gRPC decision: player={}, action={}, amount={}", 
                self.getSeatId(), response.getActionType(), response.getAmount());
            
            return action;
            
        } catch (StatusRuntimeException e) {
            log.warn("gRPC agent failed (status={}), falling back to local", 
                e.getStatus().getCode(), e);
            return fallback.decide(state, self);
            
        } catch (Exception e) {
            log.error("gRPC agent error, falling back to local", e);
            return fallback.decide(state, self);
        }
    }
    
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.warn("Channel shutdown interrupted");
            }
        }
    }
}
```

### 3. ProtoAdapter.java (核心数据转换)

```java
package com.texasholdem.server.grpc;

import com.texasholdem.common.protocol.*;
import com.texasholdem.core.model.*;
import java.util.stream.Collectors;

public class ProtoAdapter {
    
    /**
     * Java GameState → Proto DecisionRequest
     */
    public static DecisionRequest toDecisionRequest(
        GameState state, 
        PlayerState self,
        List<Action> legalActions
    ) {
        return DecisionRequest.newBuilder()
            .setGameId(state.getGameId())
            .setGameState(toGameStateView(state, self))
            .addAllLegalActions(legalActions.stream()
                .map(ProtoAdapter::actionToLegalAction)
                .collect(Collectors.toList()))
            .setTiming(TimingInfo.newBuilder()
                .setDecisionDeadlineMs(System.currentTimeMillis() + 4000)
                .setMaxThinkTimeMs(3500)
                .build())
            .build();
    }
    
    /**
     * 构建 GameStateView proto
     */
    private static GameStateView toGameStateView(GameState state, PlayerState self) {
        return GameStateView.newBuilder()
            .setPhase(GamePhase.valueOf(state.getPhase().name()))
            .addAllBoard(state.getBoard().stream()
                .map(ProtoAdapter::cardToProto)
                .collect(Collectors.toList()))
            .setSelf(toPlayerView(self, state))
            .addAllOpponents(state.getPlayers().stream()
                .filter(p -> !p.getSeatId().equals(self.getSeatId()))
                .map(ProtoAdapter::toPlayerSummary)
                .collect(Collectors.toList()))
            .setPot(toPotInfo(state.getPot()))
            .addAllActionHistory(state.getActionHistory().stream()
                .map(ProtoAdapter::toActionEntry)
                .collect(Collectors.toList()))
            .setDealerPosition(state.getDealerPosition())
            .setCurrentBet(state.getCurrentBet())
            .build();
    }
    
    /**
     * Proto ActionResponse → Java Action
     */
    public static Action toJavaAction(
        ActionResponse response, 
        GameState state, 
        PlayerState self
    ) {
        String seatId = self.getSeatId();
        
        return switch (response.getActionType()) {
            case FOLD -> new FoldAction(seatId);
            case CHECK -> new CheckAction(seatId);
            case CALL -> new CallAction(seatId);
            case BET -> new BetAction(seatId, response.getAmount());
            case RAISE -> new RaiseAction(seatId, response.getAmount());
            case ALL_IN -> new AllInAction(seatId);
            default -> new FoldAction(seatId); // 默认 fold
        };
    }
    
    // 辅助方法...
    private static Card cardToProto(Card card) {
        return Card.newBuilder()
            .setRank(Rank.valueOf(card.getRank().name()))
            .setSuit(Suit.valueOf(card.getSuit().name()))
            .build();
    }
    
    private static PlayerView toPlayerView(PlayerState player, GameState state) {
        return PlayerView.newBuilder()
            .setSeatId(player.getSeatId())
            .setChips(player.getChips())
            .addAllHoleCards(player.getHoleCards().stream()
                .map(ProtoAdapter::cardToProto)
                .collect(Collectors.toList()))
            .setRoundContribution(player.getRoundContribution())
            .setIsAllIn(player.isAllIn())
            .setIsFolded(player.isFolded())
            .build();
    }
    
    // ... 其他转换方法
}
```

### 4. AppConfig.java (改动)

```java
package com.texasholdem.server.config;

import com.texasholdem.ai.BuiltinAgent;
import com.texasholdem.ai.SimpleHoldemAgent;
import com.texasholdem.server.ai.GrpcAgentBridge;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    
    @Bean
    public BuiltinAgent builtinAgent(GrpcAgentConfig config) {
        String strategy = config.getStrategy();
        log.info("Creating BuiltinAgent with strategy: {}", strategy);
        
        if ("GRPC".equalsIgnoreCase(strategy)) {
            try {
                BuiltinAgent fallback = new SimpleHoldemAgent();
                return new GrpcAgentBridge(config.getGrpc(), fallback);
            } catch (Exception e) {
                log.error("Failed to create gRPC agent, falling back to local", e);
                return new SimpleHoldemAgent();
            }
        }
        
        return new SimpleHoldemAgent();
    }
}
```

---

## Python 侧

### 1. server.py (gRPC 服务启动)

```python
# poker-agent/src/poker_agent/grpc/server.py

import asyncio
import argparse
import logging
from concurrent import futures
import grpc

from .service import PokerAgentService
from ..config.loader import load_config
from pathlib import Path

logger = logging.getLogger(__name__)


async def serve(config_path: str, port: int, max_agents: int):
    """启动 gRPC 服务器"""
    config = load_config(Path(config_path))
    
    # 创建服务
    servicer = PokerAgentService(config, max_agents=max_agents)
    
    # 创建 gRPC 服务器
    server = grpc.aio.server(
        futures.ThreadPoolExecutor(max_workers=10)
    )
    
    # 注册服务
    from . import poker_agent_pb2_grpc
    poker_agent_pb2_grpc.add_PokerAgentServicer_to_server(
        servicer, server
    )
    
    # 绑定端口
    server.add_insecure_port(f'[::]:{port}')
    
    logger.info(f"gRPC Server listening on port {port}")
    logger.info(f"Config: {config}")
    
    await server.start()
    
    try:
        await server.wait_for_termination()
    except KeyboardInterrupt:
        logger.info("Server shutting down...")
        await server.stop(0)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Run Poker Agent gRPC Server")
    parser.add_argument("--port", type=int, default=9090, help="gRPC server port")
    parser.add_argument("--config", default="config/default.yaml", help="Config file path")
    parser.add_argument("--max-agents", type=int, default=10, help="Max concurrent agents")
    
    args = parser.parse_args(argv)
    
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )
    
    asyncio.run(serve(args.config, args.port, args.max_agents))


if __name__ == "__main__":
    main()
```

### 2. service.py (核心 RPC 实现)

```python
# poker-agent/src/poker_agent/grpc/service.py

import logging
from typing import AsyncIterator
import asyncio

from . import poker_agent_pb2, poker_agent_pb2_grpc
from .adapter import ProtoAdapter
from .registry import AgentRegistry
from ..config.schema import AgentConfig
from ..models import DecisionRequest

logger = logging.getLogger(__name__)


class PokerAgentService(poker_agent_pb2_grpc.PokerAgentServicer):
    """Implements the Poker Agent service"""
    
    def __init__(self, config: AgentConfig, max_agents: int = 10):
        self.config = config
        self.registry = AgentRegistry(config, max_agents=max_agents)
    
    async def MakeDecision(self, request: poker_agent_pb2.DecisionRequest, context) -> poker_agent_pb2.ActionResponse:
        """
        Main RPC: receive DecisionRequest from Java, return ActionResponse
        """
        try:
            game_id = request.game_id
            start_time = asyncio.get_event_loop().time()
            
            logger.info(f"MakeDecision: game_id={game_id}, player={request.game_state.self.seat_id}")
            
            # 1. 转换 proto → Python 对象
            decision_request = ProtoAdapter.to_decision_request(request)
            
            # 2. 获取或创建 agent
            agent = self.registry.get_or_create(game_id)
            
            # 3. 调用 agent 决策
            action_response = await agent.decide(decision_request)
            
            # 4. 转换 Python → proto
            proto_response = ProtoAdapter.to_action_response(action_response)
            
            elapsed = (asyncio.get_event_loop().time() - start_time) * 1000
            logger.info(f"Decision made: action={proto_response.action_type}, elapsed={elapsed:.0f}ms")
            
            return proto_response
            
        except Exception as e:
            logger.error(f"Decision failed", exc_info=True)
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            return poker_agent_pb2.ActionResponse()
    
    async def Register(self, request: poker_agent_pb2.RegisterRequest, context) -> poker_agent_pb2.RegisterResponse:
        """可选：agent 注册端点"""
        logger.info(f"Agent registered: {request.agent_id}")
        return poker_agent_pb2.RegisterResponse(success=True, message="OK")
    
    async def Ping(self, request: poker_agent_pb2.PingRequest, context) -> poker_agent_pb2.PingResponse:
        """健康检查"""
        return poker_agent_pb2.PingResponse(
            success=True,
            server_version="0.1.0"
        )
```

### 3. adapter.py (Proto ↔ Python 转换)

```python
# poker-agent/src/poker_agent/grpc/adapter.py

from . import poker_agent_pb2
from ..models import (
    DecisionRequest, ActionResponse, GameState, 
    ActionType, Card, Suit, Rank
)


class ProtoAdapter:
    """Converts between Protocol Buffer and Python models"""
    
    @staticmethod
    def to_decision_request(proto_req: poker_agent_pb2.DecisionRequest) -> DecisionRequest:
        """Proto DecisionRequest → Python DecisionRequest"""
        game_state_view = proto_req.game_state
        
        game_state = GameState(
            phase=GamePhase[game_state_view.phase.name],
            board=[
                ProtoAdapter.proto_to_card(c)
                for c in game_state_view.board
            ],
            self=PlayerView(
                seat_id=game_state_view.self.seat_id,
                chips=game_state_view.self.chips,
                hole_cards=[
                    ProtoAdapter.proto_to_card(c)
                    for c in game_state_view.self.hole_cards
                ],
                round_contribution=game_state_view.self.round_contribution,
                is_all_in=game_state_view.self.is_all_in,
                is_folded=game_state_view.self.is_folded,
            ),
            opponents=[...],  # 类似转换
            pot=...,
            action_history=...,
        )
        
        legal_actions = [
            ActionType[la.action_type.name]
            for la in proto_req.legal_actions
            if la.is_available
        ]
        
        return DecisionRequest(
            game_id=proto_req.game_id,
            game_state=game_state,
            legal_actions=legal_actions,
        )
    
    @staticmethod
    def to_action_response(action: ActionResponse) -> poker_agent_pb2.ActionResponse:
        """Python ActionResponse → Proto ActionResponse"""
        return poker_agent_pb2.ActionResponse(
            action_type=poker_agent_pb2.ActionType[action.action_type.name],
            amount=action.amount,
            reasoning=action.reasoning,
        )
    
    @staticmethod
    def proto_to_card(proto_card: poker_agent_pb2.Card) -> Card:
        return Card(
            rank=Rank[proto_card.rank.name],
            suit=Suit[proto_card.suit.name],
        )
    
    # ... 其他转换方法
```

### 4. registry.py (Agent 生命周期管理)

```python
# poker-agent/src/poker_agent/grpc/registry.py

import logging
from typing import Dict
from datetime import datetime, timedelta

from ..agent.react_agent import PokerReactAgent
from ..config.schema import AgentConfig

logger = logging.getLogger(__name__)


class AgentRegistry:
    """Manages agent instances lifecycle"""
    
    def __init__(self, config: AgentConfig, max_agents: int = 10):
        self.config = config
        self.max_agents = max_agents
        self.agents: Dict[str, PokerReactAgent] = {}
        self.last_used: Dict[str, datetime] = {}
    
    def get_or_create(self, game_id: str) -> PokerReactAgent:
        """Get existing agent or create new one"""
        
        # 清理过期 agent
        self._cleanup_expired()
        
        # 检查是否已存在
        if game_id in self.agents:
            self.last_used[game_id] = datetime.now()
            return self.agents[game_id]
        
        # 检查容量
        if len(self.agents) >= self.max_agents:
            logger.warning(f"Agent registry full ({self.max_agents}), evicting oldest")
            self._evict_oldest()
        
        # 创建新 agent
        logger.info(f"Creating new agent for game: {game_id}")
        agent = PokerReactAgent(self.config)
        self.agents[game_id] = agent
        self.last_used[game_id] = datetime.now()
        
        return agent
    
    def _cleanup_expired(self, ttl_minutes: int = 60):
        """Remove agents that haven't been used in TTL"""
        now = datetime.now()
        expired = [
            game_id for game_id, last_use in self.last_used.items()
            if (now - last_use) > timedelta(minutes=ttl_minutes)
        ]
        
        for game_id in expired:
            logger.info(f"Evicting expired agent: {game_id}")
            self.agents.pop(game_id, None)
            self.last_used.pop(game_id, None)
    
    def _evict_oldest(self):
        """Remove the least recently used agent"""
        oldest_id = min(
            self.last_used.items(),
            key=lambda x: x[1]
        )[0]
        self.agents.pop(oldest_id)
        self.last_used.pop(oldest_id)
```

---

## 启动脚本

### 运行 Python gRPC Server

```bash
#!/bin/bash
# scripts/run_agent_server.sh

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

cd "$PROJECT_ROOT"

# 生成 proto
echo "Generating proto files..."
uv run python scripts/generate_proto.py

# 启动服务器
echo "Starting gRPC server..."
uv run python -m poker_agent.grpc.server \
    --port 9090 \
    --config config/default.yaml \
    --max-agents 10
```

### 测试脚本

```python
# tests/test_grpc_integration.py

import pytest
import grpc
from concurrent import futures
import time

from poker_agent.grpc import server, service
from poker_agent.grpc import poker_agent_pb2, poker_agent_pb2_grpc
from poker_agent.config.loader import load_config
from pathlib import Path


@pytest.fixture
def grpc_channel():
    """创建测试用 gRPC 通道"""
    config = load_config(Path("config/default.yaml"))
    servicer = service.PokerAgentService(config)
    
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=1))
    poker_agent_pb2_grpc.add_PokerAgentServicer_to_server(servicer, server)
    port = server.add_insecure_port('[::]:0')
    server.start()
    
    channel = grpc.aio.secure_channel(f'localhost:{port}', grpc.local_channel_credentials())
    yield channel
    
    channel.close()
    server.stop(0)


async def test_make_decision(grpc_channel):
    """测试决策 RPC"""
    stub = poker_agent_pb2_grpc.PokerAgentStub(grpc_channel)
    
    request = poker_agent_pb2.DecisionRequest(
        game_id="test_game_1",
        # ... 填充 DecisionRequest
    )
    
    response = await stub.MakeDecision(request)
    
    assert response.action_type != poker_agent_pb2.ActionType.FOLD  # 示例
```

---

## 配置文件

### application.yaml

```yaml
poker:
  agent:
    strategy: GRPC
    grpc:
      enabled: true
      serverUrl: localhost:9090
      timeoutMs: 4000
      maxRetries: 2
      keepAliveMs: 30000
    fallback:
      strategy: BUILTIN
      builtinType: SIMPLE_HOLDEM
```

### config/server.yaml

```yaml
grpc:
  host: 0.0.0.0
  port: 9090
  max_agents: 10

agent:
  mode: player
  model: gpt-4o-mini
  timeout_ms: 3500
  
llm:
  provider: openai
  api_key: ${OPENAI_API_KEY}
  temperature: 0.4
```

---

## 总结

这些代码模板涵盖了核心实现所需的所有关键类：

**Java 侧**:
- ✅ GrpcAgentConfig: 配置管理
- ✅ GrpcAgentBridge: gRPC 客户端 + 降级
- ✅ ProtoAdapter: 数据转换
- ✅ AppConfig: Spring 集成

**Python 侧**:
- ✅ server.py: gRPC 服务启动
- ✅ service.py: 核心 RPC 实现
- ✅ adapter.py: Proto 转换
- ✅ registry.py: Agent 生命周期

直接使用这些模板可以加速开发进度。

