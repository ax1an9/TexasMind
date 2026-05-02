"""End-to-end smoke tests simulating realistic poker scenarios from the Java server.

Each test builds a proto DecisionRequest (mimicking Java's ProtoAdapter),
sends it through the gRPC layer, and verifies:
  1. The response is a legal action.
  2. The agent's hand memory recorded the decision and state snapshot.
"""

from __future__ import annotations

import asyncio

import grpc

from poker_agent.config.schema import AgentConfig, ToolConfig
from poker_agent.grpc.generated import poker_agent_pb2 as pb2
from poker_agent.grpc.generated import poker_agent_pb2_grpc as pb2_grpc
from poker_agent.grpc.registry import AgentRegistry
from poker_agent.grpc.server import PokerAgentService


# ── Helpers ────────────────────────────────────────────────────────────

def _config_with_tools() -> AgentConfig:
    """Return an AgentConfig with all built-in tools enabled."""
    return AgentConfig(
        tools={
            "hand_evaluation": ToolConfig(enabled=True),
            "pot_odds": ToolConfig(enabled=True),
            "opponent_modeling": ToolConfig(enabled=True),
            "history_analysis": ToolConfig(enabled=True),
        },
    )

def _card(rank: int, suit: int) -> pb2.Card:
    """Shortcut for building a Card proto."""
    return pb2.Card(rank=rank, suit=suit)


def _build_request(
    game_id: str,
    phase: int,
    self_cards: list[tuple[int, int]],
    self_chips: int,
    self_contribution: int = 0,
    opponents: list[dict] | None = None,
    board_cards: list[tuple[int, int]] | None = None,
    pot_total: int = 0,
    current_bet: int = 0,
    action_history: list[dict] | None = None,
    legal_actions: list[dict] | None = None,
) -> pb2.DecisionRequest:
    """Build a proto DecisionRequest mimicking Java's ProtoAdapter output.

    Parameters
    ----------
    game_id : str
        The room / game identifier.
    phase : int
        A ``pb2.GamePhase`` value (e.g. ``pb2.PRE_FLOP``).
    self_cards : list[tuple[int, int]]
        Hole cards as ``(pb2.Rank, pb2.Suit)`` tuples.
    self_chips : int
        Bot's chip count.
    self_contribution : int
        Bot's contribution to the current round.
    opponents : list[dict] | None
        Each dict has keys: seat_id, chips, contribution, is_all_in, is_folded.
    board_cards : list[tuple[int, int]] | None
        Community cards as ``(pb2.Rank, pb2.Suit)`` tuples.
    pot_total : int
        Total pot size.
    current_bet : int
        Current bet to match.
    action_history : list[dict] | None
        Each dict has keys: seat_id, action_type (pb2 value), amount, phase.
    legal_actions : list[dict] | None
        Each dict has keys: type (pb2 value), min_amount, max_amount, is_available.
    """
    opp_protos: list[pb2.PlayerSummary] = []
    for opp in opponents or []:
        opp_protos.append(
            pb2.PlayerSummary(
                seat_id=opp.get("seat_id", "opp"),
                chips=opp.get("chips", 1000),
                round_contribution=opp.get("contribution", 0),
                is_all_in=opp.get("is_all_in", False),
                is_folded=opp.get("is_folded", False),
            )
        )

    hist_protos: list[pb2.ActionEntry] = []
    for h in action_history or []:
        hist_protos.append(
            pb2.ActionEntry(
                seat_id=h["seat_id"],
                action_type=h["action_type"],
                amount=h.get("amount", 0),
                phase=h.get("phase", pb2.PRE_FLOP),
            )
        )

    action_protos: list[pb2.LegalAction] = []
    for a in legal_actions or []:
        action_protos.append(
            pb2.LegalAction(
                type=a["type"],
                min_amount=a.get("min_amount", 0),
                max_amount=a.get("max_amount", 0),
                is_available=a.get("is_available", True),
            )
        )

    return pb2.DecisionRequest(
        game_id=game_id,
        game_state=pb2.GameStateView(
            phase=phase,
            board=[_card(r, s) for r, s in board_cards or []],
            self=pb2.PlayerView(
                seat_id="Bot_1",
                chips=self_chips,
                hole_cards=[_card(r, s) for r, s in self_cards],
                round_contribution=self_contribution,
            ),
            opponents=opp_protos,
            pot=pb2.PotInfo(total_pot=pot_total, main_pot=pot_total),
            action_history=hist_protos,
            current_bet=current_bet,
        ),
        legal_actions=action_protos,
    )


async def _serve(registry: AgentRegistry):
    """Start an in-process gRPC server.  Returns (server, channel, stub)."""
    server = grpc.aio.server()
    service = PokerAgentService(registry)
    pb2_grpc.add_PokerAgentServicer_to_server(service, server)
    port = server.add_insecure_port("127.0.0.1:0")
    await server.start()
    channel = grpc.aio.insecure_channel(f"127.0.0.1:{port}")
    stub = pb2_grpc.PokerAgentStub(channel)
    return server, channel, stub


def _assert_legal(response: pb2.ActionResponse, legal: list[dict]) -> None:
    """Assert the response action type is in the legal set and amount is valid."""
    legal_types = {a["type"] for a in legal}
    assert response.action_type in legal_types, (
        f"Action type {response.action_type} not in legal types {legal_types}"
    )
    for a in legal:
        if a["type"] == response.action_type:
            if response.action_type in (pb2.RAISE, pb2.BET, pb2.ALL_IN):
                min_a = a.get("min_amount", 0)
                max_a = a.get("max_amount", 0)
                if max_a > 0:
                    assert min_a <= response.amount <= max_a, (
                        f"Amount {response.amount} out of range [{min_a}, {max_a}]"
                    )
            break


def _assert_memory(
    agent,
    min_decisions: int = 1,
    min_snapshots: int = 1,
) -> None:
    """Verify the agent's hand memory recorded decisions and snapshots."""
    hm = agent.memory.hand_memory
    assert hm is not None, "hand_memory should be enabled"
    assert len(hm.decisions) >= min_decisions, (
        f"Expected >= {min_decisions} decisions, got {len(hm.decisions)}"
    )
    assert len(hm.snapshots) >= min_snapshots, (
        f"Expected >= {min_snapshots} snapshots, got {len(hm.snapshots)}"
    )
    # Each decision string should contain the action type and amount
    for dec in hm.decisions:
        parts = dec.split(":")
        assert len(parts) >= 2, f"Decision string malformed: {dec!r}"


# ── Default legal actions used across multiple tests ───────────────────

_DEFAULT_LEGAL = [
    {"type": pb2.FOLD},
    {"type": pb2.CALL},
    {"type": pb2.RAISE, "min_amount": 40, "max_amount": 1000},
]


# ── Test 1: Standard pre-flop decision ─────────────────────────────────

def test_e2e_realistic_decision() -> None:
    """Simulate what Java's ProtoAdapter.buildDecisionRequest would produce.

    Game: room_1_1, PRE_FLOP
    Self: Bot_1, 1000 chips, AK suited (strong hand), contributed 10
    Opponent: human_player, 980 chips, raised to 20
    Pot: 30, current bet: 20
    Legal: FOLD, CALL(20), RAISE(40-1000), ALL_IN(1000)
    """

    async def run() -> None:
        registry = AgentRegistry(_config_with_tools())
        server, channel, stub = await _serve(registry)
        try:
            legal = [
                {"type": pb2.FOLD},
                {"type": pb2.CALL},
                {"type": pb2.RAISE, "min_amount": 40, "max_amount": 1000},
                {"type": pb2.ALL_IN, "min_amount": 1000, "max_amount": 1000},
            ]
            request = _build_request(
                game_id="room_1_1",
                phase=pb2.PRE_FLOP,
                self_cards=[(pb2.ACE, pb2.HEARTS), (pb2.KING, pb2.HEARTS)],
                self_chips=1000,
                self_contribution=10,
                opponents=[
                    {"seat_id": "human_player", "chips": 980, "contribution": 20},
                ],
                pot_total=30,
                current_bet=20,
                legal_actions=legal,
            )

            response = await stub.MakeDecision(request)
            _assert_legal(response, legal)

            # Verify agent memory
            agent = registry.get_or_create("room_1_1")
            _assert_memory(agent)
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run())


# ── Test 2: Concurrent games ──────────────────────────────────────────

def test_e2e_multiple_games() -> None:
    """Three different games each get their own agent with independent memory."""

    async def run() -> None:
        registry = AgentRegistry(_config_with_tools())
        server, channel, stub = await _serve(registry)
        try:
            scenarios = [
                {
                    "game_id": "room_1",
                    "phase": pb2.PRE_FLOP,
                    "cards": [(pb2.ACE, pb2.SPADES), (pb2.ACE, pb2.HEARTS)],
                    "board": [],
                    "pot": 30,
                    "bet": 20,
                },
                {
                    "game_id": "room_2",
                    "phase": pb2.FLOP,
                    "cards": [(pb2.TWO, pb2.CLUBS), (pb2.SEVEN, pb2.DIAMONDS)],
                    "board": [
                        (pb2.FIVE, pb2.HEARTS),
                        (pb2.NINE, pb2.SPADES),
                        (pb2.JACK, pb2.CLUBS),
                    ],
                    "pot": 100,
                    "bet": 20,
                },
                {
                    "game_id": "room_3",
                    "phase": pb2.TURN,
                    "cards": [(pb2.KING, pb2.HEARTS), (pb2.QUEEN, pb2.HEARTS)],
                    "board": [
                        (pb2.THREE, pb2.HEARTS),
                        (pb2.EIGHT, pb2.DIAMONDS),
                        (pb2.TEN, pb2.CLUBS),
                        (pb2.FOUR, pb2.SPADES),
                    ],
                    "pot": 200,
                    "bet": 50,
                },
            ]

            for sc in scenarios:
                request = _build_request(
                    game_id=sc["game_id"],
                    phase=sc["phase"],
                    self_cards=sc["cards"],
                    self_chips=1000,
                    board_cards=sc["board"],
                    pot_total=sc["pot"],
                    current_bet=sc["bet"],
                    legal_actions=_DEFAULT_LEGAL,
                )
                response = await stub.MakeDecision(request)
                assert response.action_type in {pb2.FOLD, pb2.CALL, pb2.RAISE}

            # Each game should have its own agent
            assert registry.active_count() == 3

            # Each agent should have independent memory
            for sc in scenarios:
                agent = registry.get_or_create(sc["game_id"])
                _assert_memory(agent, min_decisions=1, min_snapshots=1)
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run())


# ── Test 3: Strong hand — AA pre-flop ─────────────────────────────────

def test_classic_strong_hand() -> None:
    """AA pre-flop, first to act.  Agent should prefer BET or ALL_IN."""

    async def run() -> None:
        registry = AgentRegistry(_config_with_tools())
        server, channel, stub = await _serve(registry)
        try:
            legal = [
                {"type": pb2.CHECK},
                {"type": pb2.BET, "min_amount": 20, "max_amount": 2000},
                {"type": pb2.ALL_IN, "min_amount": 2000, "max_amount": 2000},
            ]
            request = _build_request(
                game_id="strong_hand",
                phase=pb2.PRE_FLOP,
                self_cards=[(pb2.ACE, pb2.SPADES), (pb2.ACE, pb2.HEARTS)],
                self_chips=2000,
                opponents=[{"seat_id": "villain", "chips": 2000}],
                pot_total=0,
                current_bet=0,
                legal_actions=legal,
            )

            response = await stub.MakeDecision(request)
            _assert_legal(response, legal)

            # Strong hand heuristic: strength >= 0.55 -> BET
            assert response.action_type in {pb2.BET, pb2.ALL_IN}, (
                f"Expected BET or ALL_IN with AA, got {response.action_type}"
            )

            agent = registry.get_or_create("strong_hand")
            _assert_memory(agent)
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run())


# ── Test 4: Weak hand — 72 offsuit ────────────────────────────────────

def test_classic_weak_hand() -> None:
    """72 offsuit facing a raise.  Verify agent returns a legal action."""

    async def run() -> None:
        registry = AgentRegistry(_config_with_tools())
        server, channel, stub = await _serve(registry)
        try:
            legal = [
                {"type": pb2.FOLD},
                {"type": pb2.CALL},
                {"type": pb2.RAISE, "min_amount": 200, "max_amount": 500},
            ]
            request = _build_request(
                game_id="weak_hand",
                phase=pb2.PRE_FLOP,
                self_cards=[(pb2.SEVEN, pb2.CLUBS), (pb2.TWO, pb2.DIAMONDS)],
                self_chips=500,
                self_contribution=0,
                opponents=[{"seat_id": "villain", "chips": 900, "contribution": 100}],
                pot_total=150,
                current_bet=100,
                legal_actions=legal,
            )

            response = await stub.MakeDecision(request)
            _assert_legal(response, legal)

            agent = registry.get_or_create("weak_hand")
            _assert_memory(agent)
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run())


# ── Test 5: Drawing hand — flush draw on flop ─────────────────────────

def test_classic_drawing_hand() -> None:
    """Kh Qh with 2h 7h Jc board (flush draw).  Verify legal action."""

    async def run() -> None:
        registry = AgentRegistry(_config_with_tools())
        server, channel, stub = await _serve(registry)
        try:
            legal = [
                {"type": pb2.FOLD},
                {"type": pb2.CALL},
                {"type": pb2.RAISE, "min_amount": 100, "max_amount": 800},
            ]
            request = _build_request(
                game_id="drawing_hand",
                phase=pb2.FLOP,
                self_cards=[(pb2.KING, pb2.HEARTS), (pb2.QUEEN, pb2.HEARTS)],
                self_chips=800,
                self_contribution=0,
                opponents=[{"seat_id": "villain", "chips": 700, "contribution": 50}],
                board_cards=[
                    (pb2.TWO, pb2.HEARTS),
                    (pb2.SEVEN, pb2.HEARTS),
                    (pb2.JACK, pb2.CLUBS),
                ],
                pot_total=100,
                current_bet=50,
                legal_actions=legal,
            )

            response = await stub.MakeDecision(request)
            _assert_legal(response, legal)

            agent = registry.get_or_create("drawing_hand")
            _assert_memory(agent)
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run())


# ── Test 6: Short stack all-in ────────────────────────────────────────

def test_classic_all_in_scenario() -> None:
    """AA with 50 chips facing a 200 raise.  Verify legal action."""

    async def run() -> None:
        registry = AgentRegistry(_config_with_tools())
        server, channel, stub = await _serve(registry)
        try:
            legal = [
                {"type": pb2.FOLD},
                {"type": pb2.ALL_IN, "min_amount": 50, "max_amount": 50},
            ]
            request = _build_request(
                game_id="all_in_scenario",
                phase=pb2.PRE_FLOP,
                self_cards=[(pb2.ACE, pb2.DIAMONDS), (pb2.ACE, pb2.CLUBS)],
                self_chips=50,
                self_contribution=0,
                opponents=[{"seat_id": "villain", "chips": 1500, "contribution": 200}],
                pot_total=300,
                current_bet=200,
                legal_actions=legal,
            )

            response = await stub.MakeDecision(request)
            _assert_legal(response, legal)

            agent = registry.get_or_create("all_in_scenario")
            _assert_memory(agent)
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run())


# ── Test 7: Sequential decisions accumulate in memory ─────────────────

def test_classic_showdown_memory() -> None:
    """Three sequential decisions for the same game accumulate in memory."""

    async def run() -> None:
        registry = AgentRegistry(_config_with_tools())
        server, channel, stub = await _serve(registry)
        try:
            game_id = "showdown_game"
            phases = [pb2.PRE_FLOP, pb2.FLOP, pb2.TURN]
            boards: list[list[tuple[int, int]]] = [
                [],
                [(pb2.TWO, pb2.HEARTS), (pb2.SEVEN, pb2.DIAMONDS), (pb2.JACK, pb2.CLUBS)],
                [
                    (pb2.TWO, pb2.HEARTS),
                    (pb2.SEVEN, pb2.DIAMONDS),
                    (pb2.JACK, pb2.CLUBS),
                    (pb2.FIVE, pb2.SPADES),
                ],
            ]
            pots = [30, 100, 200]
            bets = [20, 50, 80]

            for phase, board, pot, bet in zip(phases, boards, pots, bets):
                request = _build_request(
                    game_id=game_id,
                    phase=phase,
                    self_cards=[(pb2.ACE, pb2.HEARTS), (pb2.KING, pb2.SPADES)],
                    self_chips=900,
                    self_contribution=10,
                    opponents=[{"seat_id": "villain", "chips": 800, "contribution": bet}],
                    board_cards=board,
                    pot_total=pot,
                    current_bet=bet,
                    legal_actions=[
                        {"type": pb2.FOLD},
                        {"type": pb2.CALL},
                        {"type": pb2.RAISE, "min_amount": bet * 2, "max_amount": 900},
                    ],
                )
                response = await stub.MakeDecision(request)
                assert response.action_type in {pb2.FOLD, pb2.CALL, pb2.RAISE}

            # Verify 3 decisions and 3 snapshots accumulated
            agent = registry.get_or_create(game_id)
            _assert_memory(agent, min_decisions=3, min_snapshots=3)
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run())


# ── Test 8: Full game lifecycle ────────────────────────────────────────

def test_classic_lifecycle() -> None:
    """Create game, make a decision, then remove.  Verify memory was saved."""

    async def run() -> None:
        registry = AgentRegistry(_config_with_tools())
        server, channel, stub = await _serve(registry)
        try:
            game_id = "lifecycle_game"

            # Phase 1: Create game via first decision
            request = _build_request(
                game_id=game_id,
                phase=pb2.PRE_FLOP,
                self_cards=[(pb2.TEN, pb2.SPADES), (pb2.JACK, pb2.SPADES)],
                self_chips=1000,
                opponents=[{"seat_id": "villain", "chips": 1000, "contribution": 20}],
                pot_total=30,
                current_bet=20,
                legal_actions=_DEFAULT_LEGAL,
            )
            response = await stub.MakeDecision(request)
            assert response.action_type in {pb2.FOLD, pb2.CALL, pb2.RAISE}

            # Verify agent was created
            assert registry.active_count() == 1

            # Verify memory was populated
            agent = registry.get_or_create(game_id)
            _assert_memory(agent, min_decisions=1, min_snapshots=1)
            assert len(agent.memory.hand_memory.decisions) == 1

            # Phase 2: Remove the game (triggers memory save)
            registry.remove(game_id)
            assert registry.active_count() == 0
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run())
