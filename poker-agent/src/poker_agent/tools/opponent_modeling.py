from __future__ import annotations

import json
from collections import Counter

from .base import PokerTool


class OpponentModelingTool(PokerTool):
    @property
    def name(self) -> str:
        return "opponent_modeling"

    @property
    def description(self) -> str:
        return "Infer coarse opponent tendencies from action history."

    def run(self, **kwargs: object) -> str:
        history = kwargs.get("history", [])
        actions = []
        for item in history:
            if isinstance(item, dict):
                actions.append(str(item.get("action_type", "")))
        counts = Counter(actions)
        total = sum(counts.values()) or 1
        aggressive = (counts.get("BET", 0) + counts.get("RAISE", 0) + counts.get("ALL_IN", 0)) / total
        payload = {
            "aggressive_rate": round(aggressive, 3),
            "most_common_action": counts.most_common(1)[0][0] if counts else "UNKNOWN",
            "summary": "aggressive" if aggressive >= 0.5 else "passive" if aggressive <= 0.25 else "balanced",
        }
        return json.dumps(payload, ensure_ascii=False)
