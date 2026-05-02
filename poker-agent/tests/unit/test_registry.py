from __future__ import annotations

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from poker_agent.config.schema import AgentConfig
from poker_agent.grpc.registry import AgentRegistry
from poker_agent.models import (
    ActionResponse,
    ActionType,
    Card,
    DecisionRequest,
    GamePhase,
    GameStateView,
    LegalAction,
    PlayerView,
    PotInfo,
    Rank,
    Suit,
)


def _make_decision_request(game_id: str = "game-1") -> DecisionRequest:
    """Build a minimal DecisionRequest for testing."""
    return DecisionRequest(
        game_id=game_id,
        game_state=GameStateView(
            phase=GamePhase.PRE_FLOP,
            board=[],
            self=PlayerView(
                seat_id="seat-1",
                chips=1000,
                hole_cards=[
                    Card(rank=Rank.ACE, suit=Suit.SPADES),
                    Card(rank=Rank.KING, suit=Suit.SPADES),
                ],
            ),
            pot=PotInfo(total_pot=30, main_pot=30),
        ),
        legal_actions=[
            LegalAction(type=ActionType.CALL, is_available=True),
            LegalAction(type=ActionType.FOLD, is_available=True),
        ],
    )


@pytest.fixture()
def config() -> AgentConfig:
    return AgentConfig()


def test_registry_creates_agent_per_game(config: AgentConfig) -> None:
    """Different game_ids should get different agent instances."""
    registry = AgentRegistry(config)

    with patch("poker_agent.grpc.registry.PokerReactAgent") as mock_agent_cls:
        mock_instance_1 = MagicMock()
        mock_instance_2 = MagicMock()
        mock_agent_cls.side_effect = [mock_instance_1, mock_instance_2]

        agent_1 = registry.get_or_create("game-1")
        agent_2 = registry.get_or_create("game-2")

        assert agent_1 is mock_instance_1
        assert agent_2 is mock_instance_2
        assert agent_1 is not agent_2
        assert mock_agent_cls.call_count == 2
        assert registry.active_count() == 2


def test_registry_reuses_existing_agent(config: AgentConfig) -> None:
    """Repeated calls with the same game_id should return the same agent."""
    registry = AgentRegistry(config)

    with patch("poker_agent.grpc.registry.PokerReactAgent") as mock_agent_cls:
        mock_instance = MagicMock()
        mock_agent_cls.return_value = mock_instance

        agent_a = registry.get_or_create("game-1")
        agent_b = registry.get_or_create("game-1")

        assert agent_a is agent_b
        assert mock_agent_cls.call_count == 1
        assert registry.active_count() == 1


def test_registry_new_agent_calls_memory_load(config: AgentConfig) -> None:
    """AgentRegistry should call memory.load() on newly created agents."""
    registry = AgentRegistry(config)

    with patch("poker_agent.grpc.registry.PokerReactAgent") as mock_agent_cls:
        mock_instance = MagicMock()
        mock_agent_cls.return_value = mock_instance

        registry.get_or_create("game-1")

        mock_instance.memory.load.assert_called_once()


def test_registry_remove_game(config: AgentConfig) -> None:
    """remove() should drop the agent; get_or_create should create a fresh one."""
    registry = AgentRegistry(config)

    with patch("poker_agent.grpc.registry.PokerReactAgent") as mock_agent_cls:
        mock_first = MagicMock()
        mock_second = MagicMock()
        mock_agent_cls.side_effect = [mock_first, mock_second]

        registry.get_or_create("game-1")
        assert registry.active_count() == 1

        registry.remove("game-1")
        assert registry.active_count() == 0

        # Getting the same game_id again should create a brand-new agent.
        agent_new = registry.get_or_create("game-1")
        assert agent_new is mock_second
        assert registry.active_count() == 1


def test_registry_remove_nonexistent_is_noop(config: AgentConfig) -> None:
    """Removing a game_id that does not exist should not raise."""
    registry = AgentRegistry(config)
    registry.remove("does-not-exist")  # should not raise
    assert registry.active_count() == 0


@pytest.mark.asyncio
async def test_registry_decide_uses_per_game_agent(config: AgentConfig) -> None:
    """decide() should delegate to the per-game agent and return its response."""
    registry = AgentRegistry(config)
    expected = ActionResponse(action_type=ActionType.CALL, amount=0, reasoning="test")

    with patch("poker_agent.grpc.registry.PokerReactAgent") as mock_agent_cls:
        mock_agent = MagicMock()
        mock_agent.decide = AsyncMock(return_value=expected)
        mock_agent_cls.return_value = mock_agent

        request = _make_decision_request("game-A")
        result = await registry.decide(request)

        assert result is expected
        mock_agent.decide.assert_awaited_once_with(request)


@pytest.mark.asyncio
async def test_registry_decide_routes_to_correct_agent(config: AgentConfig) -> None:
    """decide() should route to the correct per-game agent based on game_id."""
    registry = AgentRegistry(config)
    response_a = ActionResponse(action_type=ActionType.CALL, amount=0, reasoning="a")
    response_b = ActionResponse(action_type=ActionType.FOLD, amount=0, reasoning="b")

    with patch("poker_agent.grpc.registry.PokerReactAgent") as mock_agent_cls:
        mock_agent_a = MagicMock()
        mock_agent_a.decide = AsyncMock(return_value=response_a)
        mock_agent_b = MagicMock()
        mock_agent_b.decide = AsyncMock(return_value=response_b)
        mock_agent_cls.side_effect = [mock_agent_a, mock_agent_b]

        result_a = await registry.decide(_make_decision_request("game-A"))
        result_b = await registry.decide(_make_decision_request("game-B"))

        assert result_a is response_a
        assert result_b is response_b
        mock_agent_a.decide.assert_awaited()
        mock_agent_b.decide.assert_awaited()


def test_registry_cleanup_saves_memory(config: AgentConfig, tmp_path) -> None:
    """remove() should call memory.save() on the agent before removing it."""
    registry = AgentRegistry(config)

    with patch("poker_agent.grpc.registry.PokerReactAgent") as mock_agent_cls:
        mock_agent = MagicMock()
        mock_agent_cls.return_value = mock_agent

        registry.get_or_create("game-1")
        registry.remove("game-1")

        mock_agent.memory.save.assert_called_once()
        assert registry.active_count() == 0


def test_registry_cleanup_saves_memory_with_file_persistence(tmp_path) -> None:
    """Integration-style test: AgentRegistry.remove() persists memory to disk via MemoryManager.save()."""
    from poker_agent.config.schema import MemoryConfig

    mem_config = MemoryConfig(persistence="file", file_path=str(tmp_path / "mem.json"))
    cfg = AgentConfig(memory=mem_config)
    registry = AgentRegistry(cfg)

    with patch("poker_agent.grpc.registry.PokerReactAgent") as mock_agent_cls:
        mock_agent = MagicMock()
        # Use a real MemoryManager so save() actually writes to disk.
        from poker_agent.memory.manager import MemoryManager

        real_memory = MemoryManager(mem_config)
        mock_agent.memory = real_memory
        mock_agent_cls.return_value = mock_agent

        registry.get_or_create("game-42")
        registry.remove("game-42")

        assert (tmp_path / "mem.json").exists(), "memory file should have been written"
        assert registry.active_count() == 0
