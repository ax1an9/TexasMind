from __future__ import annotations

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class GamePhase(str, Enum):
    PRE_FLOP = "PRE_FLOP"
    FLOP = "FLOP"
    TURN = "TURN"
    RIVER = "RIVER"
    SHOWDOWN = "SHOWDOWN"
    SETTLED = "SETTLED"


class ActionType(str, Enum):
    FOLD = "FOLD"
    CHECK = "CHECK"
    CALL = "CALL"
    BET = "BET"
    RAISE = "RAISE"
    ALL_IN = "ALL_IN"


class Rank(str, Enum):
    TWO = "TWO"
    THREE = "THREE"
    FOUR = "FOUR"
    FIVE = "FIVE"
    SIX = "SIX"
    SEVEN = "SEVEN"
    EIGHT = "EIGHT"
    NINE = "NINE"
    TEN = "TEN"
    JACK = "JACK"
    QUEEN = "QUEEN"
    KING = "KING"
    ACE = "ACE"


class Suit(str, Enum):
    HEARTS = "HEARTS"
    DIAMONDS = "DIAMONDS"
    CLUBS = "CLUBS"
    SPADES = "SPADES"


class Card(BaseModel):
    rank: Rank
    suit: Suit


class PlayerView(BaseModel):
    seat_id: str
    chips: int
    hole_cards: list[Card] = Field(default_factory=list)
    round_contribution: int = 0
    is_all_in: bool = False
    is_folded: bool = False


class PlayerSummary(BaseModel):
    seat_id: str
    chips: int
    round_contribution: int = 0
    is_all_in: bool = False
    is_folded: bool = False


class PotSlice(BaseModel):
    amount: int
    eligible_seat_ids: list[str] = Field(default_factory=list)


class PotInfo(BaseModel):
    total_pot: int
    main_pot: int
    side_pots: list[PotSlice] = Field(default_factory=list)


class ActionEntry(BaseModel):
    seat_id: str
    action_type: ActionType
    amount: int = 0
    phase: GamePhase


class LegalAction(BaseModel):
    type: ActionType
    min_amount: int = 0
    max_amount: int = 0
    is_available: bool = True


class GameStateView(BaseModel):
    phase: GamePhase
    board: list[Card] = Field(default_factory=list)
    self: PlayerView
    opponents: list[PlayerSummary] = Field(default_factory=list)
    pot: PotInfo
    action_history: list[ActionEntry] = Field(default_factory=list)
    dealer_position: int = 0
    current_bet: int = 0


class TimingInfo(BaseModel):
    decision_deadline_ms: int = 0
    max_think_time_ms: int = 0


class DecisionRequest(BaseModel):
    game_id: str
    game_state: GameStateView
    legal_actions: list[LegalAction] = Field(default_factory=list)
    timing: TimingInfo | None = None


class ActionResponse(BaseModel):
    action_type: ActionType
    amount: int = 0
    reasoning: str = ""
    execution_path: str = ""        # "llm" or "heuristic"
    confidence: float = 0.0
    decision_latency_ms: int = 0
    raw_output: str = ""            # full ReAct reasoning trace


class HintResult(BaseModel):
    action_type: ActionType
    confidence: float = 0.0
    reasoning: str = ""
    details: dict[str, Any] = Field(default_factory=dict)
