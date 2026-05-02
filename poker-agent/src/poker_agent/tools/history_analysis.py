from __future__ import annotations

import json

from .base import PokerTool


class HistoryAnalysisTool(PokerTool):
    @property
    def name(self) -> str:
        return "history_analysis"

    @property
    def description(self) -> str:
        return "Summarize the recent action history for the current hand."

    def run(self, **kwargs: object) -> str:
        history = kwargs.get("history", [])
        total_actions = len(history) if isinstance(history, list) else 0
        last_action = history[-1] if total_actions else {}
        payload = {
            "total_actions": total_actions,
            "last_action": last_action,
            "summary": "short" if total_actions <= 3 else "medium" if total_actions <= 8 else "long",
        }
        return json.dumps(payload, ensure_ascii=False)
