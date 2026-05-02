from __future__ import annotations

import json

from .base import PokerTool


class PotOddsTool(PokerTool):
    @property
    def name(self) -> str:
        return "pot_odds"

    @property
    def description(self) -> str:
        return "Calculate pot odds and a simple call threshold."

    def run(self, **kwargs: object) -> str:
        pot = float(kwargs.get("pot", 0) or 0)
        to_call = float(kwargs.get("to_call", 0) or 0)
        if to_call <= 0:
            payload = {"pot_odds": 0.0, "required_equity": 0.0, "summary": "no_call_needed"}
            return json.dumps(payload, ensure_ascii=False)

        required_equity = to_call / (pot + to_call)
        payload = {
            "pot_odds": round(pot / to_call if to_call else 0.0, 3),
            "required_equity": round(required_equity, 3),
            "summary": "favorable" if required_equity <= 0.33 else "neutral" if required_equity <= 0.5 else "poor",
        }
        return json.dumps(payload, ensure_ascii=False)
