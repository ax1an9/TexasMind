from __future__ import annotations

from dataclasses import dataclass, field

from ..models import ActionResponse, GameStateView


@dataclass
class HandMemory:
    snapshots: list[str] = field(default_factory=list)
    decisions: list[str] = field(default_factory=list)

    def record_state(self, game_state: GameStateView) -> None:
        self.snapshots.append(f"{game_state.phase.value}:{game_state.pot.total_pot}:{game_state.current_bet}")

    def add_decision(self, game_state: GameStateView, action: ActionResponse, reasoning: str) -> None:
        self.record_state(game_state)
        self.decisions.append(f"{action.action_type.value}:{action.amount}:{reasoning}")

    def get_summary(self) -> str:
        if not self.snapshots:
            return "no hand memory"
        return " | ".join(self.snapshots[-5:])

    def clear(self) -> None:
        self.snapshots.clear()
        self.decisions.clear()

    def to_dict(self) -> dict[str, object]:
        return {
            "snapshots": list(self.snapshots),
            "decisions": list(self.decisions),
        }

    @classmethod
    def from_dict(cls, data: dict[str, object]) -> HandMemory:
        return cls(
            snapshots=list(data.get("snapshots", [])),  # type: ignore[arg-type]
            decisions=list(data.get("decisions", [])),  # type: ignore[arg-type]
        )
