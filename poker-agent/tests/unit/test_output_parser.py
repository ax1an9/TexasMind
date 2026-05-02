from poker_agent.agent.output_parser import LegalActionParser
from poker_agent.models import ActionType, LegalAction


def test_parser_maps_valid_raise_amount() -> None:
    parser = LegalActionParser()
    legal_actions = [
        LegalAction(type=ActionType.CALL, is_available=True),
        LegalAction(type=ActionType.RAISE, min_amount=100, max_amount=300, is_available=True),
    ]

    action = parser.parse("Thought: value is strong\nFinal Answer: RAISE 200", legal_actions)

    assert action.action_type == ActionType.RAISE
    assert action.amount == 200


def test_parser_falls_back_to_safe_legal_action() -> None:
    parser = LegalActionParser()
    legal_actions = [LegalAction(type=ActionType.CHECK, is_available=True)]

    action = parser.parse("Final Answer: BET 999", legal_actions)

    assert action.action_type == ActionType.CHECK
    assert action.amount == 0
