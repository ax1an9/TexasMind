from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from ..config.schema import MemoryConfig
from ..models import ActionResponse, GameStateView, PlayerSummary
from .hand_memory import HandMemory
from .opponent_memory import OpponentMemory
from .session_memory import SessionMemory


@dataclass
class MemoryManager:
    config: MemoryConfig
    hand_memory: HandMemory | None = None
    opponent_memory: OpponentMemory | None = None
    session_memory: SessionMemory | None = None

    def __post_init__(self) -> None:
        if self.config.hand_memory:
            self.hand_memory = HandMemory()
        if self.config.opponent_memory:
            self.opponent_memory = OpponentMemory()
        if self.config.session_memory:
            self.session_memory = SessionMemory()

    def get_context(self) -> str:
        parts: list[str] = []
        if self.hand_memory:
            parts.append(f"当前牌局记忆:\n{self.hand_memory.get_summary()}")
        if self.opponent_memory:
            parts.append(f"对手画像:\n{self.opponent_memory.get_summary()}")
        if self.session_memory:
            parts.append(f"会话记忆:\n{self.session_memory.get_summary()}")
        return "\n\n".join(parts)

    def record_decision(self, game_state: GameStateView, action: ActionResponse, reasoning: str) -> None:
        if self.hand_memory:
            self.hand_memory.add_decision(game_state, action, reasoning)

    def record_opponents(self, opponents: list[PlayerSummary]) -> None:
        if not self.opponent_memory:
            return
        for opponent in opponents:
            self.opponent_memory.update_from_player(opponent)

    def new_hand(self) -> None:
        if self.hand_memory:
            self.hand_memory.clear()
        if self.session_memory:
            self.session_memory.record_hand()

    # ------------------------------------------------------------------
    # Persistence
    # ------------------------------------------------------------------

    def save(self) -> None:
        if self.config.persistence != "file":
            return
        path = Path(self.config.file_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "hand_memory": self.hand_memory.to_dict() if self.hand_memory else None,
            "opponent_memory": self.opponent_memory.to_dict() if self.opponent_memory else None,
            "session_memory": self.session_memory.to_dict() if self.session_memory else None,
        }
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    def load(self) -> None:
        if self.config.persistence != "file":
            return
        path = Path(self.config.file_path)
        if not path.exists():
            return
        data = json.loads(path.read_text(encoding="utf-8"))
        if self.hand_memory and data.get("hand_memory") is not None:
            self.hand_memory = HandMemory.from_dict(data["hand_memory"])
        if self.opponent_memory and data.get("opponent_memory") is not None:
            self.opponent_memory = OpponentMemory.from_dict(data["opponent_memory"])
        if self.session_memory and data.get("session_memory") is not None:
            self.session_memory = SessionMemory.from_dict(data["session_memory"])
