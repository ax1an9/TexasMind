# Poker React Agent - 设计规格

## 概述

基于 LangChain ReAct 模式的德州扑克 AI Agent，支持玩家对弈和决策顾问两种模式。通过 gRPC 与现有 Java 引擎通信，利用 LLM 推理能力进行扑克决策。

## 目标

1. 使用 LangChain ReAct Agent 实现扑克决策
2. 通过 gRPC 与 Java 引擎通信，接收游戏状态（含合法行动列表）
3. 提供可插拔的工具系统（手牌评估、底池赔率、对手建模等）
4. 支持分层记忆系统（当前局/对手画像/跨局会话）
5. 所有功能模块可配置开启/关闭，减少耦合
6. 支持多种 LLM 后端（OpenAI、Anthropic、本地模型等）

## 架构

### 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Python Agent Service                         │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   PokerReactAgent                         │  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐  │  │
│  │  │ Prompt      │ │ ReAct       │ │ Output Parser       │  │  │
│  │  │ Template    │ │ Agent       │ │ (Action Mapping)    │  │  │
│  │  │ (Dynamic)   │ │ Executor    │ │                     │  │  │
│  │  └─────────────┘ └─────────────┘ └─────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   Tools Registry                          │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │  │
│  │  │ Hand     │ │ Pot      │ │ Opponent │ │ History  │     │  │
│  │  │ Eval     │ │ Odds     │ │ Modeling │ │ Analysis │     │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘     │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   Memory Manager                          │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐   │  │
│  │  │ Hand Memory  │ │ Opponent     │ │ Session          │   │  │
│  │  │ (当前牌局)    │ │ Memory       │ │ Memory           │   │  │
│  │  │              │ │ (对手画像)    │ │ (跨局)           │   │  │
│  │  └──────────────┘ └──────────────┘ └──────────────────┘   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   gRPC Client                             │  │
│  │  - 连接 Java Poker Server                                 │  │
│  │  - 接收 GameState（含合法行动列表）                        │  │
│  │  - 发送 Action 选择                                       │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │ gRPC
                    ┌─────────┴─────────┐
                    │   Java Engine     │
                    │   :9090           │
                    └───────────────────┘
```

### 核心模块

1. **PokerReactAgent**：主 Agent 类，组合 Prompt、ReAct Executor、Output Parser
2. **Tools Registry**：可插拔的工具注册系统，每个 Tool 独立模块
3. **Memory Manager**：分层记忆（当前局/对手画像/跨局会话）
4. **gRPC Client**：与 Java 引擎通信，接收 GameState + 合法行动，发送 Action

### 关键设计决策

- LLM 后端通过配置文件指定，不硬编码任何特定模型
- 所有功能模块（Tools、Memory、Prompt 模板）可独立开启/关闭
- Agent 角色（玩家/顾问）通过 System Prompt 切换，代码逻辑相同

## gRPC 协议设计

### Proto 定义

```protobuf
syntax = "proto3";
package poker_agent;

service PokerAgent {
    rpc Register(RegisterRequest) returns (RegisterResponse);
    rpc MakeDecision(DecisionRequest) returns (ActionResponse);
    rpc Ping(PingRequest) returns (PingResponse);
}

message RegisterRequest {
    string agent_id = 1;
    string agent_name = 2;
    string agent_type = 3;  // "builtin", "react", "custom"
}

message RegisterResponse {
    bool success = 1;
    string message = 2;
}

message DecisionRequest {
    string game_id = 1;
    GameStateView game_state = 2;
    repeated LegalAction legal_actions = 3;
    TimingInfo timing = 4;
}

message GameStateView {
    GamePhase phase = 1;
    repeated Card board = 2;
    PlayerView self = 3;
    repeated PlayerSummary opponents = 4;
    PotInfo pot = 5;
    repeated ActionEntry action_history = 6;
    int32 dealer_position = 7;
    int32 current_bet = 8;
}

message PlayerView {
    string seat_id = 1;
    int32 chips = 2;
    repeated Card hole_cards = 3;  // 只有自己的手牌
    int32 round_contribution = 4;
    bool is_all_in = 5;
    bool is_folded = 6;
}

message PlayerSummary {
    string seat_id = 1;
    int32 chips = 2;
    int32 round_contribution = 3;
    bool is_all_in = 4;
    bool is_folded = 5;
    // 注意：没有 hole_cards
}

message LegalAction {
    ActionType type = 1;
    int32 min_amount = 2;
    int32 max_amount = 3;
    bool is_available = 4;
}

message ActionResponse {
    ActionType action_type = 1;
    int32 amount = 2;
    string reasoning = 3;
}

message TimingInfo {
    int64 decision_deadline_ms = 1;
    int32 max_think_time_ms = 2;
}

enum GamePhase {
    PRE_FLOP = 0;
    FLOP = 1;
    TURN = 2;
    RIVER = 3;
    SHOWDOWN = 4;
    SETTLED = 5;
}

enum ActionType {
    FOLD = 0;
    CHECK = 1;
    CALL = 2;
    BET = 3;
    RAISE = 4;
    ALL_IN = 5;
}

message Card {
    Rank rank = 1;
    Suit suit = 2;
}

enum Rank {
    TWO = 0;
    THREE = 1;
    FOUR = 2;
    FIVE = 3;
    SIX = 4;
    SEVEN = 5;
    EIGHT = 6;
    NINE = 7;
    TEN = 8;
    JACK = 9;
    QUEEN = 10;
    KING = 11;
    ACE = 12;
}

enum Suit {
    HEARTS = 0;
    DIAMONDS = 1;
    CLUBS = 2;
    SPADES = 3;
}

message PotInfo {
    int32 total_pot = 1;
    int32 main_pot = 2;
    repeated PotSlice side_pots = 3;
}

message PotSlice {
    int32 amount = 1;
    repeated string eligible_seat_ids = 2;
}

message ActionEntry {
    string seat_id = 1;
    ActionType action_type = 2;
    int32 amount = 3;
    GamePhase phase = 4;
}

message PingRequest {}

message PingResponse {
    bool success = 1;
    string server_version = 2;
}
```

### 合法行动过滤流程

```
Java Engine                    Python Agent
     │                              │
     │  DecisionRequest             │
     │  (GameState + LegalActions)  │
     │ ────────────────────────────>│
     │                              │
     │                   ReAct Agent 推理
     │                   只从 legal_actions 中选择
     │                              │
     │  ActionResponse              │
     │  (ActionType + Amount)       │
     │ <────────────────────────────│
     │                              │
     │  验证 action ∈ legal_actions │
     │  applyAction(state, action)  │
```

## ReAct Agent 核心设计

### Agent 主类

```python
class PokerReactAgent:
    """德州扑克 ReAct Agent，支持玩家和顾问两种模式"""

    def __init__(self, config: AgentConfig):
        self.config = config
        self.llm = self._create_llm()
        self.tools = self._load_tools()
        self.memory = self._create_memory()
        self.prompt_builder = PromptBuilder(config)
        self.agent_executor = self._create_executor()

    async def decide(self, decision_request: DecisionRequest) -> ActionResponse:
        """玩家模式：做出决策"""
        prompt = self.prompt_builder.build_player_prompt(
            game_state=decision_request.game_state,
            legal_actions=decision_request.legal_actions,
            memory_context=self.memory.get_context()
        )

        result = await self.agent_executor.ainvoke({
            "input": prompt,
            "chat_history": self.memory.get_chat_history()
        })

        action = self.output_parser.parse(result["output"], decision_request.legal_actions)
        self.memory.add_decision(decision_request.game_state, action, result)

        return action

    async def advise(self, decision_request: DecisionRequest) -> HintResult:
        """顾问模式：提供建议"""
        prompt = self.prompt_builder.build_advisor_prompt(
            game_state=decision_request.game_state,
            legal_actions=decision_request.legal_actions,
            memory_context=self.memory.get_context()
        )

        result = await self.agent_executor.ainvoke({
            "input": prompt,
            "chat_history": self.memory.get_chat_history()
        })

        return self._parse_hint(result)
```

### ReAct Prompt 模板

```
你是一名专业的德州扑克玩家。

当前牌局状态：
- 阶段：{phase}
- 公共牌：{board}
- 你的手牌：{hole_cards}
- 你的筹码：{chips}
- 当前底池：{pot}
- 当前需要跟注：{to_call}
- 对手信息：{opponents}

合法行动列表：
{legal_actions}

{memory_context}

请按照以下格式思考和行动：

Thought: 分析当前局面，考虑手牌强度、底池赔率、对手可能的手牌范围
Action: 选择一个工具来获取更多信息（或直接给出最终答案）
Action Input: 工具的输入参数
Observation: 工具返回的结果
... (可以重复多次)
Thought: 综合所有信息，做出最终决策
Final Answer: [行动类型] [金额]

重要提示：
1. 你只能从合法行动列表中选择
2. BET/RAISE 的金额必须在 min_amount 和 max_amount 之间
3. 如果选择 CHECK，不需要金额
```

### Output Parser（合法行动映射）

```python
class LegalActionParser:
    """确保 Agent 输出映射到合法行动"""

    def parse(self, output: str, legal_actions: List[LegalAction]) -> ActionResponse:
        # 1. 解析 Final Answer
        action_type, amount = self._extract_action(output)

        # 2. 在合法行动列表中查找匹配项
        matched = self._find_matching_action(action_type, amount, legal_actions)

        if matched is None:
            # 3. 如果不合法，选择最接近的合法行动
            matched = self._find_closest_action(action_type, amount, legal_actions)
            logger.warning(f"Agent 选择了不合法行动，已调整: {action_type} {amount} -> {matched}")

        return ActionResponse(
            action_type=matched.type,
            amount=matched.adjusted_amount,
            reasoning=self._extract_reasoning(output)
        )
```

## 工具系统设计

### 可插拔工具架构

```python
class PokerTool(ABC):
    """扑克工具基类"""

    @property
    @abstractmethod
    def name(self) -> str:
        pass

    @property
    @abstractmethod
    def description(self) -> str:
        pass

    @abstractmethod
    def run(self, **kwargs) -> str:
        pass

class ToolRegistry:
    """工具注册表，支持动态加载"""

    def __init__(self):
        self._tools: Dict[str, PokerTool] = {}

    def register(self, tool: PokerTool):
        self._tools[tool.name] = tool

    def get_enabled_tools(self, config: AgentConfig) -> List[BaseTool]:
        tools = []
        for name, tool in self._tools.items():
            if config.is_tool_enabled(name):
                tools.append(tool.to_langchain_tool())
        return tools
```

### 内置工具

1. **HandEvaluationTool**：评估当前手牌强度，返回手牌等级和胜率估计
2. **PotOddsTool**：计算底池赔率和期望值
3. **OpponentModelingTool**：分析对手的历史行为模式
4. **HistoryAnalysisTool**：分析当前牌局的行动历史

## 记忆系统设计

### 分层记忆架构

```python
class MemoryManager:
    """分层记忆管理器"""

    def __init__(self, config: MemoryConfig):
        self.config = config
        self.hand_memory = HandMemory() if config.enable_hand_memory else None
        self.opponent_memory = OpponentMemory() if config.enable_opponent_memory else None
        self.session_memory = SessionMemory() if config.enable_session_memory else None

    def get_context(self) -> str:
        """获取当前记忆上下文，用于 Prompt"""
        context_parts = []

        if self.hand_memory:
            context_parts.append("当前牌局记忆:\n" + self.hand_memory.get_summary())

        if self.opponent_memory:
            context_parts.append("对手画像:\n" + self.opponent_memory.get_summary())

        if self.session_memory:
            context_parts.append("会话记忆:\n" + self.session_memory.get_summary())

        return "\n\n".join(context_parts)
```

### 三层记忆

1. **HandMemory**：当前牌局记忆，每局结束时重置
2. **OpponentMemory**：对手行为画像，跨局持久化
3. **SessionMemory**：会话级记忆，记录跨局统计

## 错误处理与边界情况

### 错误处理策略

1. **LLM 调用失败**：降级到最安全行动（CHECK > CALL > FOLD）
2. **决策超时**：立即返回最安全行动
3. **输出解析失败**：从输出中提取关键词，尝试推断意图
4. **工具执行错误**：返回友好错误信息，继续推理流程
5. **gRPC 连接失败**：自动重连，指数退避

### 重试机制

- Agent 决策支持重试（默认 2 次）
- 验证每次返回的行动是否合法
- 指数退避避免快速失败循环

## 配置系统

### 配置文件格式

```yaml
name: "ProPokerAgent"
mode: player  # player / advisor

llm:
  provider: openai  # openai / anthropic / ollama / custom
  model: gpt-4o
  api_key: ${OPENAI_API_KEY}
  temperature: 0.7
  max_tokens: 1000

tools:
  hand_evaluation:
    enabled: true
    weight: 1.0
  pot_odds:
    enabled: true
    weight: 0.8
  opponent_modeling:
    enabled: true
    weight: 0.9
    params:
      memory_window: 50
  history_analysis:
    enabled: true
    weight: 0.7

memory:
  hand_memory: true
  opponent_memory: true
  session_memory: true
  persistence: file
  file_path: ./data/memory

max_retries: 2
timeout_ms: 10000
```

## 项目结构

```
poker-agent/
├── pyproject.toml
├── config/
│   ├── default.yaml
│   └── example.yaml
├── src/
│   └── poker_agent/
│       ├── __init__.py
│       ├── main.py
│       ├── agent/
│       │   ├── react_agent.py
│       │   ├── prompt_builder.py
│       │   └── output_parser.py
│       ├── tools/
│       │   ├── base.py
│       │   ├── registry.py
│       │   ├── hand_evaluation.py
│       │   ├── pot_odds.py
│       │   ├── opponent_modeling.py
│       │   └── history_analysis.py
│       ├── memory/
│       │   ├── manager.py
│       │   ├── hand_memory.py
│       │   ├── opponent_memory.py
│       │   └── session_memory.py
│       ├── grpc/
│       │   ├── client.py
│       │   ├── generated/
│       │   └── adapter.py
│       ├── config/
│       │   ├── schema.py
│       │   └── loader.py
│       └── error_handling/
│           ├── handler.py
│           └── retry.py
├── proto/
│   └── poker_agent.proto
├── tests/
│   ├── unit/
│   ├── integration/
│   └── e2e/
└── scripts/
    ├── generate_proto.py
    └── run_agent.py
```

## 测试策略

1. **单元测试**：工具、记忆、解析器等独立模块
2. **集成测试**：Agent 完整流程、gRPC 通信
3. **端到端测试**：与 Java 引擎的完整游戏流程

## 实施路线图

### Phase 1: 基础框架（1-2 周）
- 项目脚手架搭建
- gRPC Proto 定义与代码生成
- 基本 gRPC 客户端
- 配置系统
- 基本 Agent 框架（无工具）

### Phase 2: ReAct 核心（1-2 周）
- ReAct Agent 集成
- Prompt 模板系统
- Output Parser（合法行动映射）
- 错误处理与降级策略
- 单元测试

### Phase 3: 工具系统（1 周）
- 工具注册框架
- HandEvaluationTool
- PotOddsTool
- OpponentModelingTool（基础版）
- 工具测试

### Phase 4: 记忆系统（1 周）
- HandMemory
- OpponentMemory
- SessionMemory
- 记忆持久化
- 集成测试

### Phase 5: 集成与优化（1-2 周）
- 与 Java 引擎联调
- 顾问模式实现
- 性能优化（超时控制、缓存）
- 端到端测试
- 文档编写

## 依赖

### 核心依赖
- langchain >= 0.3.0
- langchain-openai >= 0.2.0
- langchain-anthropic >= 0.2.0
- grpcio >= 1.60.0
- grpcio-tools >= 1.60.0
- pyyaml >= 6.0
- pydantic >= 2.0.0

### 可选依赖
- langchain-community（用于 Ollama 等本地模型）

### 开发依赖
- pytest >= 7.0.0
- pytest-asyncio >= 0.21.0
- pytest-mock >= 3.0.0
