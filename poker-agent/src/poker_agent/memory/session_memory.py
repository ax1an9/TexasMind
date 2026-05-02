from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class SessionMemory:
    hands_played: int = 0
    hands_won: int = 0
    notes: list[str] = field(default_factory=list)

    def record_hand(self, won: bool | None = None) -> None:
        self.hands_played += 1
        if won:
            self.hands_won += 1

    def add_note(self, note: str) -> None:
        self.notes.append(note)

    def get_summary(self) -> str:
        win_rate = self.hands_won / self.hands_played if self.hands_played else 0.0
        note_text = "; ".join(self.notes[-3:]) if self.notes else "no session notes"
        return f"hands={self.hands_played}, wins={self.hands_won}, win_rate={win_rate:.2f}, notes={note_text}"

    def to_dict(self) -> dict[str, object]:
        return {
            "hands_played": self.hands_played,
            "hands_won": self.hands_won,
            "notes": list(self.notes),
        }

    @classmethod
    def from_dict(cls, data: dict[str, object]) -> SessionMemory:
        return cls(
            hands_played=int(data.get("hands_played", 0)),
            hands_won=int(data.get("hands_won", 0)),
            notes=list(data.get("notes", [])),  # type: ignore[arg-type]
        )
