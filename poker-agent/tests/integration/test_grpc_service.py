from __future__ import annotations

import asyncio

import grpc

from poker_agent.agent.react_agent import PokerReactAgent
from poker_agent.config.schema import AgentConfig
from poker_agent.grpc.adapter import ProtoAdapter
from poker_agent.grpc.generated import poker_agent_pb2 as pb2
from poker_agent.grpc.generated import poker_agent_pb2_grpc as pb2_grpc
from poker_agent.grpc.server import PokerAgentService
from poker_agent.models import ActionType, Card, GamePhase, GameStateView, LegalAction, PlayerSummary, PlayerView, PotInfo


def _build_state() -> pb2.DecisionRequest:
    state = pb2.DecisionRequest(
        game_id="game-1",
        game_state=pb2.GameStateView(
            phase=pb2.FLOP,
            board=[pb2.Card(rank=pb2.ACE, suit=pb2.SPADES)],
            self=pb2.PlayerView(
                seat_id="1",
                chips=1000,
                hole_cards=[pb2.Card(rank=pb2.KING, suit=pb2.SPADES)],
                round_contribution=50,
            ),
            opponents=[pb2.PlayerSummary(seat_id="2", chips=900)],
            pot=pb2.PotInfo(total_pot=150, main_pot=150),
            current_bet=50,
        ),
        legal_actions=[
            pb2.LegalAction(type=pb2.CHECK, is_available=True),
            pb2.LegalAction(type=pb2.CALL, is_available=True),
        ],
    )
    return state


def _build_state_with_history_and_side_pots() -> pb2.DecisionRequest:
    """Build a more complex state that exercises action_history and side pot conversion."""
    state = pb2.DecisionRequest(
        game_id="game-2",
        game_state=pb2.GameStateView(
            phase=pb2.TURN,
            board=[
                pb2.Card(rank=pb2.ACE, suit=pb2.SPADES),
                pb2.Card(rank=pb2.KING, suit=pb2.HEARTS),
                pb2.Card(rank=pb2.TEN, suit=pb2.DIAMONDS),
                pb2.Card(rank=pb2.FIVE, suit=pb2.CLUBS),
            ],
            self=pb2.PlayerView(
                seat_id="1",
                chips=800,
                hole_cards=[
                    pb2.Card(rank=pb2.ACE, suit=pb2.HEARTS),
                    pb2.Card(rank=pb2.KING, suit=pb2.DIAMONDS),
                ],
                round_contribution=100,
            ),
            opponents=[
                pb2.PlayerSummary(seat_id="2", chips=600, round_contribution=100),
                pb2.PlayerSummary(seat_id="3", chips=0, is_all_in=True, round_contribution=200),
            ],
            pot=pb2.PotInfo(
                total_pot=500,
                main_pot=400,
                side_pots=[pb2.PotSlice(amount=100, eligible_seat_ids=["1", "2"])],
            ),
            action_history=[
                pb2.ActionEntry(seat_id="2", action_type=pb2.CALL, amount=50, phase=pb2.PRE_FLOP),
                pb2.ActionEntry(seat_id="3", action_type=pb2.RAISE, amount=100, phase=pb2.PRE_FLOP),
                pb2.ActionEntry(seat_id="1", action_type=pb2.CALL, amount=100, phase=pb2.PRE_FLOP),
            ],
            dealer_position=1,
            current_bet=100,
        ),
        legal_actions=[
            pb2.LegalAction(type=pb2.CHECK, is_available=True),
            pb2.LegalAction(type=pb2.RAISE, is_available=True, min_amount=200, max_amount=800),
        ],
    )
    return state


def test_grpc_service_make_decision_roundtrip() -> None:
    async def run_test() -> None:
        server = grpc.aio.server()
        agent = PokerReactAgent(AgentConfig())
        service = PokerAgentService(agent)
        pb2_grpc.add_PokerAgentServicer_to_server(service, server)
        port = server.add_insecure_port("127.0.0.1:0")
        await server.start()
        channel = grpc.aio.insecure_channel(f"127.0.0.1:{port}")
        stub = pb2_grpc.PokerAgentStub(channel)

        try:
            response = await stub.MakeDecision(_build_state())
            assert response.action_type in {pb2.CHECK, pb2.CALL}

            # Verify the agent's memory was updated, proving model types were correct.
            hand_memory = agent.memory.hand_memory
            assert hand_memory is not None, "hand_memory should be enabled by default"
            assert len(hand_memory.snapshots) == 1, f"expected 1 snapshot, got {len(hand_memory.snapshots)}"
            assert len(hand_memory.decisions) == 1, f"expected 1 decision, got {len(hand_memory.decisions)}"
            # The snapshot should encode the game state: "FLOP:150:50"
            assert hand_memory.snapshots[0] == "FLOP:150:50"
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run_test())


def test_grpc_service_with_action_history_and_side_pots() -> None:
    """Verify that action_history and side_pots are converted to proper Pydantic models."""

    async def run_test() -> None:
        server = grpc.aio.server()
        agent = PokerReactAgent(AgentConfig())
        service = PokerAgentService(agent)
        pb2_grpc.add_PokerAgentServicer_to_server(service, server)
        port = server.add_insecure_port("127.0.0.1:0")
        await server.start()
        channel = grpc.aio.insecure_channel(f"127.0.0.1:{port}")
        stub = pb2_grpc.PokerAgentStub(channel)

        try:
            response = await stub.MakeDecision(_build_state_with_history_and_side_pots())
            assert response.action_type in {pb2.CHECK, pb2.RAISE}

            # Verify memory was updated with the complex state
            hand_memory = agent.memory.hand_memory
            assert hand_memory is not None
            assert len(hand_memory.snapshots) == 1
            assert len(hand_memory.decisions) == 1
            # TURN phase, total_pot=500, current_bet=100
            assert hand_memory.snapshots[0] == "TURN:500:100"
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run_test())
