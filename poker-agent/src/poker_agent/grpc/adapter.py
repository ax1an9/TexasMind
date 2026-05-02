from __future__ import annotations

from google.protobuf.json_format import MessageToDict, ParseDict

from .generated import poker_agent_pb2 as pb2
from ..models import ActionResponse, DecisionRequest


class ProtoAdapter:
    def decision_request_to_proto(self, request: DecisionRequest) -> pb2.DecisionRequest:
        return ParseDict(request.model_dump(mode="json"), pb2.DecisionRequest())

    def action_response_from_proto(self, response: pb2.ActionResponse) -> ActionResponse:
        return ActionResponse.model_validate(MessageToDict(response, preserving_proto_field_name=True))

    def register_request_to_proto(self, payload: dict) -> pb2.RegisterRequest:
        return ParseDict(payload, pb2.RegisterRequest())

    def register_response_to_dict(self, response: pb2.RegisterResponse) -> dict:
        return MessageToDict(response, preserving_proto_field_name=True)

    def ping_request_to_proto(self) -> pb2.PingRequest:
        return pb2.PingRequest()

    def ping_response_to_dict(self, response: pb2.PingResponse) -> dict:
        return MessageToDict(response, preserving_proto_field_name=True)
