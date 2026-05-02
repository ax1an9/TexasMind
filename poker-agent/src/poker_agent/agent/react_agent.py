from __future__ import annotations

from dataclasses import dataclass
from importlib import import_module
import logging
import os
import time
from typing import Any

from ..config.schema import AgentConfig
from ..error_handling.handler import ErrorHandler
from ..memory.manager import MemoryManager
from ..models import ActionResponse, ActionType, DecisionRequest, HintResult, LegalAction
from ..tools.hand_evaluation import HandEvaluationTool
from ..tools.history_analysis import HistoryAnalysisTool
from ..tools.opponent_modeling import OpponentModelingTool
from ..tools.pot_odds import PotOddsTool
from ..tools.registry import ToolRegistry
from .output_parser import LegalActionParser
from .prompt_builder import PromptBuilder

log = logging.getLogger(__name__)


@dataclass
class PokerReactAgent:
    """LangChain ReAct poker agent with a heuristic fallback."""

    config: AgentConfig

    def __post_init__(self) -> None:
        self.llm = self._create_llm()
        self.tools = self._load_tools()
        self.memory = self._create_memory()
        self.prompt_builder = PromptBuilder(self.config)
        self.output_parser = LegalActionParser()
        self.error_handler = ErrorHandler()
        self.agent_executor = self._create_executor()

    async def decide(self, decision_request: DecisionRequest) -> ActionResponse:
        t0 = time.monotonic()
        game_id = decision_request.game_id
        seat_id = decision_request.game_state.self.seat_id
        phase = decision_request.game_state.phase.value
        hole_cards = [f"{c.rank.value}{c.suit.value[0]}" for c in decision_request.game_state.self.hole_cards]
        board = [f"{c.rank.value}{c.suit.value[0]}" for c in decision_request.game_state.board]
        log.info("[DECIDE] game=%s seat=%s phase=%s hole=%s board=%s", game_id, seat_id, phase, hole_cards, board)

        prompt = self.prompt_builder.build_player_prompt(
            game_state=decision_request.game_state,
            legal_actions=decision_request.legal_actions,
            memory_context=self.memory.get_context(),
        )
        result = await self._execute(prompt, decision_request)
        elapsed_ms = int((time.monotonic() - t0) * 1000)

        action = self.output_parser.parse(result["output"], decision_request.legal_actions)
        action.execution_path = result.get("execution_path", "unknown")
        action.confidence = result.get("confidence", 0.0)
        action.decision_latency_ms = elapsed_ms
        action.raw_output = result.get("output", "")

        log.info(
            "[DECIDE] game=%s seat=%s -> %s %d (path=%s, confidence=%.2f, latency=%dms)",
            game_id, seat_id, action.action_type.value, action.amount,
            action.execution_path, action.confidence, action.decision_latency_ms,
        )
        log.debug("[DECIDE] raw_output:\n%s", action.raw_output)

        self.memory.record_decision(decision_request.game_state, action, result["output"])
        return action

    async def advise(self, decision_request: DecisionRequest) -> HintResult:
        prompt = self.prompt_builder.build_advisor_prompt(
            game_state=decision_request.game_state,
            legal_actions=decision_request.legal_actions,
            memory_context=self.memory.get_context(),
        )
        result = await self._execute(prompt, decision_request)
        action = self.output_parser.parse(result["output"], decision_request.legal_actions)
        return HintResult(
            action_type=action.action_type,
            confidence=result.get("confidence", 0.5),
            reasoning=action.reasoning,
            details={"output": result["output"]},
        )

    def _create_llm(self) -> Any:
        provider = self.config.llm.provider
        api_key = self.config.llm.api_key or os.environ.get("OPENAI_API_KEY") or os.environ.get("ANTHROPIC_API_KEY")
        base_url = self.config.llm.base_url
        if api_key is None:
            log.warning("[LLM] No API key configured, LLM disabled")
            return None
        try:
            if provider == "openai":
                module = import_module("langchain_openai")
                chat_openai = getattr(module, "ChatOpenAI")
                kwargs = dict(
                    model=self.config.llm.model,
                    temperature=self.config.llm.temperature,
                    max_tokens=self.config.llm.max_tokens,
                    api_key=api_key,
                )
                if base_url:
                    kwargs["base_url"] = base_url
                log.info("[LLM] Creating OpenAI client: model=%s, base_url=%s", self.config.llm.model, base_url or "(default)")
                return chat_openai(**kwargs)
            if provider == "anthropic":
                module = import_module("langchain_anthropic")
                chat_anthropic = getattr(module, "ChatAnthropic")
                kwargs = dict(
                    model=self.config.llm.model,
                    temperature=self.config.llm.temperature,
                    max_tokens=self.config.llm.max_tokens,
                    api_key=api_key,
                )
                if base_url:
                    kwargs["base_url"] = base_url
                log.info("[LLM] Creating Anthropic client: model=%s", self.config.llm.model)
                return chat_anthropic(**kwargs)
        except Exception as e:
            log.error("[LLM] Failed to create LLM client: %s", e)
            return None
        log.warning("[LLM] Unknown provider: %s", provider)
        return None

    def _load_tools(self) -> list[Any]:
        registry = ToolRegistry()
        registry.register(HandEvaluationTool())
        registry.register(PotOddsTool())
        registry.register(OpponentModelingTool())
        registry.register(HistoryAnalysisTool())
        return registry.get_enabled_tools(self.config)

    def _create_memory(self) -> MemoryManager:
        return MemoryManager(self.config.memory)

    def _create_executor(self) -> Any:
        if self.llm is None:
            log.info("[EXECUTOR] LLM is None, skipping executor creation")
            return None

        langchain_tools = []
        for tool in self.tools:
            try:
                langchain_tools.append(tool.to_langchain_tool())
            except Exception as e:
                log.warning("[EXECUTOR] Failed to convert tool '%s': %s", tool.name, e)
                return None
        log.info("[EXECUTOR] Loaded %d langchain tools", len(langchain_tools))

        try:
            from langchain.agents import create_agent
            agent = create_agent(self.llm, langchain_tools, system_prompt="You are a Texas Hold'em ReAct agent. Use tools to evaluate hands, calculate pot odds, and analyze opponents. Think step by step, then give your final action.")
            log.info("[EXECUTOR] Agent created successfully via langchain.agents.create_agent")
            return agent
        except Exception as e:
            log.warning("[EXECUTOR] Failed to create agent: %s", e, exc_info=True)
            return None

    def _build_langchain_prompt(self):
        try:
            module = import_module("langchain_core.prompts")
            prompt_template_cls = getattr(module, "PromptTemplate")
        except ImportError:
            return None

        template = """
You are a Texas Hold'em ReAct agent.

Use these tools when useful:
{tools}

Tool names: {tool_names}

Question:
{input}

{agent_scratchpad}
""".strip()
        return prompt_template_cls.from_template(template)

    async def _execute(self, prompt: str, decision_request: DecisionRequest) -> dict[str, Any]:
        if self.agent_executor is None:
            log.info("[EXECUTE] No LLM executor available (llm=%s), using heuristic", "None" if self.llm is None else "ok")
        else:
            try:
                result = await self.agent_executor.ainvoke(
                    {"messages": [("user", prompt)]}
                )
                if isinstance(result, dict):
                    messages = result.get("messages", [])
                    if messages:
                        last_msg = messages[-1]
                        output = str(last_msg.content) if hasattr(last_msg, "content") else str(last_msg)
                    else:
                        output = str(result)
                    log.info("[EXECUTE] LLM path succeeded, output length=%d", len(output))
                    log.debug("[EXECUTE] LLM output:\n%s", output)
                    return {"output": output, "confidence": 0.7, "execution_path": "llm"}
            except Exception as e:
                log.warning("[EXECUTE] LLM path failed, falling back to heuristic: %s", e, exc_info=True)
        result = self._heuristic_execute(decision_request)
        result["execution_path"] = "heuristic"
        return result

    def _heuristic_execute(self, decision_request: DecisionRequest) -> dict[str, Any]:
        state = decision_request.game_state
        legal_actions = decision_request.legal_actions
        evaluation = self._tool_output("hand_evaluation", hole_cards=state.self.hole_cards, board=state.board)
        odds = self._tool_output(
            "pot_odds",
            pot=state.pot.total_pot,
            to_call=max(state.current_bet - state.self.round_contribution, 0),
        )
        strength = float(evaluation.get("strength", 0.0))
        required_equity = float(odds.get("required_equity", 0.0))
        action = self._select_action(strength, required_equity, legal_actions)
        amount = self._select_amount(action, legal_actions, state)
        output = f"Thought: strength={strength:.2f}, required_equity={required_equity:.2f}\nFinal Answer: {action.value} {amount}".strip()
        log.info("[HEURISTIC] strength=%.2f, required_equity=%.2f -> %s %d", strength, required_equity, action.value, amount)
        return {"output": output, "confidence": round(max(strength, 1.0 - required_equity), 2)}

    def _tool_output(self, name: str, **kwargs: object) -> dict[str, Any]:
        try:
            tool = next(tool for tool in self.tools if tool.name == name)
        except StopIteration:
            return {}
        import json

        try:
            return json.loads(tool.run(**kwargs))
        except Exception:
            return {}

    def _select_action(
        self,
        strength: float,
        required_equity: float,
        legal_actions: list[LegalAction],
    ) -> ActionType:
        available = {action.type for action in legal_actions if action.is_available}
        if strength >= 0.75 and ActionType.RAISE in available:
            return ActionType.RAISE
        if strength >= 0.55 and ActionType.BET in available:
            return ActionType.BET
        if required_equity <= 0.35 and ActionType.CALL in available:
            return ActionType.CALL
        if ActionType.CHECK in available:
            return ActionType.CHECK
        if ActionType.CALL in available:
            return ActionType.CALL
        if ActionType.FOLD in available:
            return ActionType.FOLD
        return legal_actions[0].type if legal_actions else ActionType.FOLD

    def _select_amount(self, action: ActionType, legal_actions: list[LegalAction], state: Any) -> int:
        match = next((item for item in legal_actions if item.type == action and item.is_available), None)
        if match is None:
            return 0
        if action in {ActionType.BET, ActionType.RAISE, ActionType.ALL_IN}:
            return match.min_amount or match.max_amount or max(state.current_bet, 1)
        return 0
