from __future__ import annotations

import json
from collections import Counter

from ..models import Card
from .base import PokerTool


class HandEvaluationTool(PokerTool):
    @property
    def name(self) -> str:
        return "hand_evaluation"

    @property
    def description(self) -> str:
        return "Estimate hand strength and a coarse win-rate signal from hole cards and board cards."

    def run(self, **kwargs: object) -> str:
        hole_cards = [Card.model_validate(item) for item in kwargs.get("hole_cards", [])]
        board = [Card.model_validate(item) for item in kwargs.get("board", [])]
        strength = self._estimate_strength(hole_cards, board)
        payload = {
            "strength": strength,
            "estimated_win_rate": round(min(0.95, 0.2 + strength * 0.7), 3),
            "summary": self._summary(strength),
        }
        return json.dumps(payload, ensure_ascii=False)

    def _estimate_strength(self, hole_cards: list[Card], board: list[Card]) -> float:
        ranks = [card.rank.value for card in hole_cards + board]
        if not ranks:
            return 0.0

        count = Counter(ranks)
        pair_bonus = max(count.values()) if count else 1
        suited_bonus = 0.12 if len({card.suit for card in hole_cards}) == 1 and len(hole_cards) >= 2 else 0.0
        board_bonus = min(len(board) * 0.05, 0.2)
        rank_bonus = sum(self._rank_score(rank) for rank in ranks[:2]) / 20.0
        return min(1.0, 0.2 + (pair_bonus - 1) * 0.25 + suited_bonus + board_bonus + rank_bonus)

    def _rank_score(self, rank: str) -> int:
        order = {
            "TWO": 2,
            "THREE": 3,
            "FOUR": 4,
            "FIVE": 5,
            "SIX": 6,
            "SEVEN": 7,
            "EIGHT": 8,
            "NINE": 9,
            "TEN": 10,
            "JACK": 11,
            "QUEEN": 12,
            "KING": 13,
            "ACE": 14,
        }
        return order.get(rank, 0)

    def _summary(self, strength: float) -> str:
        if strength >= 0.8:
            return "premium"
        if strength >= 0.6:
            return "strong"
        if strength >= 0.4:
            return "playable"
        return "weak"
