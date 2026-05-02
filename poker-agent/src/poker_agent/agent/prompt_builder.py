from __future__ import annotations

from ..config.schema import AgentConfig
from ..models import GameStateView, LegalAction


class PromptBuilder:
    def __init__(self, config: AgentConfig) -> None:
        self.config = config

    def build_player_prompt(
        self,
        game_state: GameStateView,
        legal_actions: list[LegalAction],
        memory_context: str = "",
    ) -> str:
        return self._build_prompt("player", game_state, legal_actions, memory_context)

    def build_advisor_prompt(
        self,
        game_state: GameStateView,
        legal_actions: list[LegalAction],
        memory_context: str = "",
    ) -> str:
        return self._build_prompt("advisor", game_state, legal_actions, memory_context)

    def _build_prompt(
        self,
        role: str,
        game_state: GameStateView,
        legal_actions: list[LegalAction],
        memory_context: str,
    ) -> str:
        action_lines = [
            f"- {action.type.value} (min={action.min_amount}, max={action.max_amount}, available={action.is_available})"
            for action in legal_actions
        ]
        board = ", ".join(f"{card.rank.value}-{card.suit.value}" for card in game_state.board) or "none"
        hole_cards = ", ".join(f"{card.rank.value}-{card.suit.value}" for card in game_state.self.hole_cards) or "hidden"
        opponents = ", ".join(
            f"{opponent.seat_id}(chips={opponent.chips}, folded={opponent.is_folded}, all_in={opponent.is_all_in})"
            for opponent in game_state.opponents
        ) or "none"
        memory_block = memory_context.strip() or "no memory context"

        return "\n".join(
            [
                f"You are a professional Texas Hold'em {role} agent.",
                "",
                f"Phase: {game_state.phase.value}",
                f"Board: {board}",
                f"Hole cards: {hole_cards}",
                f"Chips: {game_state.self.chips}",
                f"Current bet: {game_state.current_bet}",
                f"Pot total: {game_state.pot.total_pot}",
                f"Opponents: {opponents}",
                "",
                "Legal actions:",
                *action_lines,
                "",
                "Memory context:",
                memory_block,
                "",
                "Respond in the following format:",
                "Thought: concise reasoning",
                "Final Answer: ACTION [AMOUNT]",
                "",
                "Only choose from the legal actions list.",
                "BET / RAISE / ALL_IN amounts must respect min and max bounds.",
                "CHECK does not need an amount.",
            ]
        )
