from __future__ import annotations

from dataclasses import dataclass, field

from ..models import ActionEntry, PlayerSummary


@dataclass
class OpponentMemory:
    action_counts: dict[str, int] = field(default_factory=dict)
    fold_counts: dict[str, int] = field(default_factory=dict)

    def update_from_action(self, seat_id: str, action: ActionEntry) -> None:
        key = f"{seat_id}:{action.action_type.value}"
        self.action_counts[key] = self.action_counts.get(key, 0) + 1
        if action.action_type.value == "FOLD":
            self.fold_counts[seat_id] = self.fold_counts.get(seat_id, 0) + 1

    def update_from_player(self, player: PlayerSummary) -> None:
        self.fold_counts.setdefault(player.seat_id, 0)

    def get_summary(self) -> str:
        if not self.action_counts:
            return "no opponent profile"
        top_entries = sorted(self.action_counts.items(), key=lambda item: item[1], reverse=True)[:3]
        return ", ".join(f"{key}={count}" for key, count in top_entries)

    def to_dict(self) -> dict[str, object]:
        return {
            "action_counts": dict(self.action_counts),
            "fold_counts": dict(self.fold_counts),
        }

    @classmethod
    def from_dict(cls, data: dict[str, object]) -> OpponentMemory:
        return cls(
            action_counts=dict(data.get("action_counts", {})),  # type: ignore[arg-type]
            fold_counts=dict(data.get("fold_counts", {})),  # type: ignore[arg-type]
        )
