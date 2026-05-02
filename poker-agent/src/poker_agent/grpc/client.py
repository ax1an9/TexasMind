from __future__ import annotations

from dataclasses import dataclass
from importlib import import_module
from typing import Any

import grpc

from .adapter import ProtoAdapter
from .generated import poker_agent_pb2 as pb2
from .generated import poker_agent_pb2_grpc as pb2_grpc
from ..models import ActionResponse, DecisionRequest


@dataclass
class GrpcClientConfig:
    target: str = "localhost:9090"
    timeout_ms: int = 10000
    secure: bool = False
    authority: str | None = None


class PokerGrpcClient:
    def __init__(self, config: GrpcClientConfig, stub: Any | None = None, channel: grpc.Channel | None = None) -> None:
        self.config = config
        self._stub = stub
        self._channel = channel
        self._adapter = ProtoAdapter()
        if self._stub is None and self._channel is not None:
            self._stub = self._create_stub(self._channel)

    @classmethod
    def connect(cls, config: GrpcClientConfig) -> "PokerGrpcClient":
        channel = grpc.aio.insecure_channel(config.target) if not config.secure else grpc.aio.secure_channel(config.target, grpc.ssl_channel_credentials())
        return cls(config=config, channel=channel)

    async def close(self) -> None:
        if self._channel is not None:
            await self._channel.close()

    async def register(self, request: dict[str, Any]) -> dict[str, Any]:
        return await self._call("Register", self._adapter.register_request_to_proto(request))

    async def make_decision(self, request: DecisionRequest) -> ActionResponse:
        proto_request = self._adapter.decision_request_to_proto(request)
        response = await self._call("MakeDecision", proto_request)
        return self._adapter.action_response_from_proto(response)

    async def ping(self) -> dict[str, Any]:
        response = await self._call("Ping", self._adapter.ping_request_to_proto())
        return self._adapter.ping_response_to_dict(response)

    async def _call(self, method_name: str, *args: Any, **kwargs: Any) -> Any:
        if self._stub is None:
            raise RuntimeError("gRPC stub is not configured. Generate proto stubs and provide a stub instance.")
        method = getattr(self._stub, method_name)
        return await method(*args, timeout=self.config.timeout_ms / 1000.0, **kwargs)

    def _create_stub(self, channel: grpc.Channel) -> Any:
        return pb2_grpc.PokerAgentStub(channel)
