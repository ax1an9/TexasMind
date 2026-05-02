from poker_agent.agent.prompt_builder import PromptBuilder
from poker_agent.config.schema import AgentConfig
from poker_agent.models import ActionType, Card, GamePhase, GameStateView, LegalAction, PlayerSummary, PlayerView, PotInfo


def test_prompt_includes_state_and_actions() -> None:
    builder = PromptBuilder(AgentConfig())
    state = GameStateView(
        phase=GamePhase.FLOP,
        board=[Card(rank="ACE", suit="SPADES")],
        self=PlayerView(seat_id="1", chips=1200, hole_cards=[Card(rank="KING", suit="SPADES")]),
        opponents=[PlayerSummary(seat_id="2", chips=1000)],
        pot=PotInfo(total_pot=300, main_pot=300),
        current_bet=50,
    )
    prompt = builder.build_player_prompt(
        game_state=state,
        legal_actions=[LegalAction(type=ActionType.CALL, is_available=True)],
        memory_context="recent hand was aggressive",
    )

    assert "Phase: FLOP" in prompt
    assert "Legal actions:" in prompt
    assert "recent hand was aggressive" in prompt
