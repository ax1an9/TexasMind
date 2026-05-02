from __future__ import annotations

from ..models import ActionResponse, ActionType, LegalAction


class ErrorHandler:
    def safe_action(self, legal_actions: list[LegalAction]) -> ActionResponse:
        preferred = [ActionType.CHECK, ActionType.CALL, ActionType.FOLD]
        for action_type in preferred:
            match = self._find_action(action_type, legal_actions)
            if match:
                return ActionResponse(action_type=match.type, amount=self._safe_amount(match))
        if legal_actions:
            first = next((action for action in legal_actions if action.is_available), legal_actions[0])
            return ActionResponse(action_type=first.type, amount=self._safe_amount(first))
        return ActionResponse(action_type=ActionType.FOLD, amount=0, reasoning="no legal action available")

    def _find_action(self, action_type: ActionType, legal_actions: list[LegalAction]) -> LegalAction | None:
        return next((action for action in legal_actions if action.is_available and action.type == action_type), None)

    def _safe_amount(self, legal_action: LegalAction) -> int:
        if legal_action.type in {ActionType.BET, ActionType.RAISE, ActionType.ALL_IN}:
            if legal_action.min_amount > 0:
                return legal_action.min_amount
            return legal_action.max_amount
        return 0
