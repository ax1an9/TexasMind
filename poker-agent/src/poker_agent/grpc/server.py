from __future__ import annotations

import argparse
import asyncio
from dataclasses import dataclass
import logging
import time

import grpc

from ..config.loader import load_config
from ..models import ActionEntry, ActionResponse, ActionType, Card, DecisionRequest, GamePhase, GameStateView, HintResult, LegalAction, PlayerSummary, PlayerView, PotInfo, PotSlice, Rank, Suit, TimingInfo
from .adapter import ProtoAdapter
from .generated import poker_agent_pb2 as pb2
from .generated import poker_agent_pb2_grpc as pb2_grpc
from .registry import AgentRegistry

log = logging.getLogger(__name__)


@dataclass
class PokerAgentService(pb2_grpc.PokerAgentServicer):
    registry: AgentRegistry

    def __post_init__(self) -> None:
        self.adapter = ProtoAdapter()

    async def Register(self, request: pb2.RegisterRequest, context: grpc.aio.ServicerContext) -> pb2.RegisterResponse:
        message = f"registered {request.agent_name or request.agent_id}"
        return pb2.RegisterResponse(success=True, message=message)

    async def MakeDecision(self, request: pb2.DecisionRequest, context: grpc.aio.ServicerContext) -> pb2.ActionResponse:
        t0 = time.monotonic()
        game_id = request.game_id
        seat_id = request.game_state.self.seat_id
        log.info("[GRPC] MakeDecision game=%s seat=%s", game_id, seat_id)

        decision_request = self._decision_request_from_proto(request)
        action = await self.registry.decide(decision_request)

        elapsed_ms = int((time.monotonic() - t0) * 1000)
        log.info(
            "[GRPC] MakeDecision game=%s seat=%s -> %s %d (path=%s, latency=%dms)",
            game_id, seat_id, action.action_type.value, action.amount,
            action.execution_path, elapsed_ms,
        )

        return pb2.ActionResponse(
            action_type=pb2.ActionType.Value(action.action_type.value),
            amount=action.amount,
            reasoning=action.reasoning,
            execution_path=action.execution_path,
            confidence=action.confidence,
            decision_latency_ms=action.decision_latency_ms,
            raw_output=action.raw_output,
        )

    async def Ping(self, request: pb2.PingRequest, context: grpc.aio.ServicerContext) -> pb2.PingResponse:
        return pb2.PingResponse(success=True, server_version="poker-agent-0.1.0")

    def _decision_request_from_proto(self, request: pb2.DecisionRequest) -> DecisionRequest:
        game_state = self._game_state_from_proto(request.game_state)
        legal_actions = [self._legal_action_from_proto(action) for action in request.legal_actions]
        timing = None
        if request.HasField("timing"):
            timing = TimingInfo(
                decision_deadline_ms=request.timing.decision_deadline_ms,
                max_think_time_ms=request.timing.max_think_time_ms,
            )
        return DecisionRequest(game_id=request.game_id, game_state=game_state, legal_actions=legal_actions, timing=timing)

    def _game_state_from_proto(self, request: pb2.GameStateView) -> GameStateView:
        return GameStateView(
            phase=pb2.GamePhase.Name(request.phase),
            board=[self._card_from_proto(card) for card in request.board],
            self=self._player_view_from_proto(request.self),
            opponents=[self._player_summary_from_proto(opponent) for opponent in request.opponents],
            pot=self._pot_info_from_proto(request.pot),
            action_history=[self._action_entry_from_proto(entry) for entry in request.action_history],
            dealer_position=request.dealer_position,
            current_bet=request.current_bet,
        )

    def _player_view_from_proto(self, request: pb2.PlayerView) -> PlayerView:
        return PlayerView(
            seat_id=request.seat_id,
            chips=request.chips,
            hole_cards=[self._card_from_proto(card) for card in request.hole_cards],
            round_contribution=request.round_contribution,
            is_all_in=request.is_all_in,
            is_folded=request.is_folded,
        )

    def _player_summary_from_proto(self, request: pb2.PlayerSummary) -> PlayerSummary:
        return PlayerSummary(
            seat_id=request.seat_id,
            chips=request.chips,
            round_contribution=request.round_contribution,
            is_all_in=request.is_all_in,
            is_folded=request.is_folded,
        )

    def _legal_action_from_proto(self, request: pb2.LegalAction) -> LegalAction:
        return LegalAction(
            type=pb2.ActionType.Name(request.type),
            min_amount=request.min_amount,
            max_amount=request.max_amount,
            is_available=request.is_available,
        )

    def _action_entry_from_proto(self, request: pb2.ActionEntry) -> ActionEntry:
        return ActionEntry(
            seat_id=request.seat_id,
            action_type=ActionType(pb2.ActionType.Name(request.action_type)),
            amount=request.amount,
            phase=GamePhase(pb2.GamePhase.Name(request.phase)),
        )

    def _pot_info_from_proto(self, request: pb2.PotInfo) -> PotInfo:
        return PotInfo(
            total_pot=request.total_pot,
            main_pot=request.main_pot,
            side_pots=[
                PotSlice(amount=side_pot.amount, eligible_seat_ids=list(side_pot.eligible_seat_ids))
                for side_pot in request.side_pots
            ],
        )

    def _card_from_proto(self, request: pb2.Card) -> Card:
        return Card(
            rank=Rank(pb2.Rank.Name(request.rank)),
            suit=Suit(pb2.Suit.Name(request.suit)),
        )


async def serve(config_path: str, bind_address: str = "0.0.0.0:9090") -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(name)s] %(levelname)s %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )
    config = load_config(config_path)
    registry = AgentRegistry(config)
    server = grpc.aio.server()
    pb2_grpc.add_PokerAgentServicer_to_server(PokerAgentService(registry), server)
    server.add_insecure_port(bind_address)
    log.info("PokerAgent gRPC server starting on %s", bind_address)
    await server.start()
    await server.wait_for_termination()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run the PokerAgent gRPC server")
    parser.add_argument("--config", default="config/default.yaml", help="Path to the YAML config file")
    parser.add_argument("--bind", default="0.0.0.0:9090", help="Bind address for the gRPC server")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    asyncio.run(serve(args.config, args.bind))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
