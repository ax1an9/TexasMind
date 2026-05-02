from __future__ import annotations

import re

from ..models import ActionResponse, ActionType, LegalAction


class LegalActionParser:
    def parse(self, output: str, legal_actions: list[LegalAction]) -> ActionResponse:
        action_type, amount = self._extract_action(output)
        match = self._find_matching_action(action_type, amount, legal_actions)
        if match is None:
            match = self._find_closest_action(action_type, amount, legal_actions)
        reasoning = self._extract_reasoning(output)
        return ActionResponse(action_type=match.type, amount=match_amount(match, amount), reasoning=reasoning)

    def _extract_action(self, output: str) -> tuple[ActionType, int]:
        pattern = re.compile(r"Final Answer:\s*([A-Z_]+)(?:\s+(\d+))?", re.IGNORECASE)
        match = pattern.search(output)
        if not match:
            return ActionType.CHECK, 0
        action_name = match.group(1).upper()
        amount = int(match.group(2) or 0)
        return ActionType[action_name] if action_name in ActionType.__members__ else ActionType.CHECK, amount

    def _extract_reasoning(self, output: str) -> str:
        thought_match = re.search(r"Thought:\s*(.+)", output, re.IGNORECASE | re.DOTALL)
        if thought_match:
            return thought_match.group(1).strip().splitlines()[0]
        return output.strip()[:240]

    def _find_matching_action(
        self,
        action_type: ActionType,
        amount: int,
        legal_actions: list[LegalAction],
    ) -> LegalAction | None:
        for action in legal_actions:
            if not action.is_available or action.type != action_type:
                continue
            if action.type in {ActionType.BET, ActionType.RAISE, ActionType.ALL_IN}:
                if action.min_amount <= amount <= max(action.max_amount, action.min_amount):
                    return action
                continue
            return action
        return None

    def _find_closest_action(
        self,
        action_type: ActionType,
        amount: int,
        legal_actions: list[LegalAction],
    ) -> LegalAction:
        available = [action for action in legal_actions if action.is_available]
        for preferred in (action_type, ActionType.CHECK, ActionType.CALL, ActionType.FOLD):
            match = next((action for action in available if action.type == preferred), None)
            if match:
                return match
        if available:
            return available[0]
        return LegalAction(type=action_type, min_amount=amount, max_amount=amount, is_available=False)


def match_amount(legal_action: LegalAction, requested_amount: int) -> int:
    if legal_action.type in {ActionType.BET, ActionType.RAISE, ActionType.ALL_IN}:
        if requested_amount <= 0:
            return legal_action.min_amount or legal_action.max_amount
        return min(max(requested_amount, legal_action.min_amount), max(legal_action.max_amount, legal_action.min_amount))
    return 0
