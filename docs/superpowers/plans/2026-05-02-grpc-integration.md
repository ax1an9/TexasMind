# gRPC Integration: Java Server + Python Agent Bridge

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the Java poker server to the Python poker-agent via gRPC so that bots in game rooms use the Python ReAct agent (with heuristic fallback) instead of the built-in SimpleHoldemAgent.

**Architecture:** Java side gets a `GrpcAgentBridge` implementing `BuiltinAgent` that converts `GameState`/`PlayerState` to protobuf, calls the Python gRPC server, and converts the response back. Python side gets an `AgentRegistry` for per-game agent lifecycle and file-based memory persistence. Fallback to `SimpleHoldemAgent` on timeout/error.

**Tech Stack:** Java 8, Spring Boot 2.7, gRPC 1.62, protobuf 3.25; Python 3.11, grpcio 1.62, pydantic, PyYAML

---

## File Map

### Java side (new files)
- `poker-common/proto/poker_agent.proto` — copied from Python side
- `poker-common/pom.xml` — add gRPC + protobuf dependencies + protoc plugin
- `poker-server/pom.xml` — add gRPC dependency
- `poker-ai/pom.xml` — add gRPC dependency
- `poker-ai/src/main/java/com/texasholdem/ai/grpc/GrpcAgentBridge.java` — BuiltinAgent impl that calls Python via gRPC
- `poker-ai/src/main/java/com/texasholdem/ai/grpc/ProtoAdapter.java` — GameState→protobuf conversion
- `poker-ai/src/main/java/com/texasholdem/ai/grpc/GrpcAgentConfig.java` — config POJO
- `poker-server/src/main/java/com/texasholdem/server/config/AppConfig.java` — modify to support agent type switching

### Java side (generated)
- `poker-common/target/generated-sources/` — protoc-gen-grpc-java stubs

### Python side (new files)
- `poker-agent/src/poker_agent/grpc/registry.py` — AgentRegistry for per-game agent lifecycle
- `poker-agent/src/poker_agent/memory/persistence.py` — file-based memory save/load
- `poker-agent/tests/unit/test_registry.py` — AgentRegistry unit tests
- `poker-agent/tests/unit/test_persistence.py` — memory persistence unit tests
- `poker-agent/tests/integration/test_grpc_e2e.py` — end-to-end integration test

### Python side (modified files)
- `poker-agent/src/poker_agent/grpc/server.py` — fix type bugs, use AgentRegistry
- `poker-agent/src/poker_agent/memory/manager.py` — add save/load methods
- `poker-agent/src/poker_agent/memory/hand_memory.py` — add to_dict/from_dict
- `poker-agent/src/poker_agent/memory/opponent_memory.py` — add to_dict/from_dict
- `poker-agent/src/poker_agent/memory/session_memory.py` — add to_dict/from_dict

---

## Task 1: Fix Python gRPC Server Type Bugs

The `_card_from_proto` and `_action_entry_from_proto` methods in `server.py` return dicts instead of Pydantic models, which will cause validation errors when passed to `PokerReactAgent.decide()`.

**Files:**
- Modify: `poker-agent/src/poker_agent/grpc/server.py:106-107`
- Modify: `poker-agent/src/poker_agent/grpc/server.py:91-97`
- Modify: `poker-agent/src/poker_agent/grpc/server.py:99-103`
- Test: `poker-agent/tests/integration/test_grpc_service.py`

- [ ] **Step 1: Fix `_card_from_proto` to return Card model**

In `poker-agent/src/poker_agent/grpc/server.py`, change line 106-107:

```python
    def _card_from_proto(self, request: pb2.Card) -> Card:
        return Card(rank=Rank(pb2.Rank.Name(request.rank)), suit=Suit(pb2.Suit.Name(request.suit)))
```

Add the missing imports at the top of the file (line 12 area):

```python
from ..models import ActionResponse, ActionType, Card, DecisionRequest, GamePhase, GameStateView, HintResult, LegalAction, PlayerSummary, PlayerView, PotInfo, PotSlice, Rank, Suit, TimingInfo
```

(Replace the existing import line that already imports most of these — just add `Card`, `Rank`, `Suit`, `PotSlice`.)

- [ ] **Step 2: Fix `_action_entry_from_proto` to return ActionEntry model**

Change lines 91-97:

```python
    def _action_entry_from_proto(self, request: pb2.ActionEntry) -> ActionEntry:
        return ActionEntry(
            seat_id=request.seat_id,
            action_type=ActionType(pb2.ActionType.Name(request.action_type)),
            amount=request.amount,
            phase=GamePhase(pb2.GamePhase.Name(request.phase)),
        )
```

Add `ActionEntry` to the imports.

- [ ] **Step 3: Fix `_pot_info_from_proto` to return proper PotSlice models**

Change lines 99-103:

```python
    def _pot_info_from_proto(self, request: pb2.PotInfo) -> PotInfo:
        return PotInfo(
            total_pot=request.total_pot,
            main_pot=request.main_pot,
            side_pots=[PotSlice(amount=sp.amount, eligible_seat_ids=list(sp.eligible_seat_ids)) for sp in request.side_pots],
        )
```

- [ ] **Step 4: Update existing integration test to verify types**

Add a type-check assertion to the existing test in `poker-agent/tests/integration/test_grpc_service.py`. After the existing `assert response.action_type in {pb2.CHECK, pb2.CALL}`, add a test that verifies the agent received proper model types by checking that the service's agent recorded a decision in memory:

```python
def test_grpc_service_make_decision_roundtrip() -> None:
    async def run_test() -> None:
        server = grpc.aio.server()
        config = AgentConfig()
        agent = PokerReactAgent(config)
        service = PokerAgentService(agent)
        pb2_grpc.add_PokerAgentServicer_to_server(service, server)
        port = server.add_insecure_port("127.0.0.1:0")
        await server.start()
        channel = grpc.aio.insecure_channel(f"127.0.0.1:{port}")
        stub = pb2_grpc.PokerAgentStub(channel)

        try:
            response = await stub.MakeDecision(_build_state())
            assert response.action_type in {pb2.CHECK, pb2.CALL}
            # Verify agent memory was updated (proves model types were correct)
            assert agent.memory.hand_memory is not None
            assert len(agent.memory.hand_memory.decisions) == 1
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run_test())
```

- [ ] **Step 5: Run tests**

```bash
cd /mnt/d/workspace/texas/poker-agent && python -m pytest tests/ -v
```

Expected: All tests pass, including the updated integration test.

- [ ] **Step 6: Commit**

```bash
git add poker-agent/src/poker_agent/grpc/server.py poker-agent/tests/integration/test_grpc_service.py
git commit -m "fix: return proper Pydantic models from gRPC server proto converters"
```

---

## Task 2: Add Memory Persistence

Add `to_dict()`/`from_dict()` to each memory class and a file-based save/load to `MemoryManager`.

**Files:**
- Modify: `poker-agent/src/poker_agent/memory/hand_memory.py`
- Modify: `poker-agent/src/poker_agent/memory/opponent_memory.py`
- Modify: `poker-agent/src/poker_agent/memory/session_memory.py`
- Modify: `poker-agent/src/poker_agent/memory/manager.py`
- Create: `poker-agent/src/poker_agent/memory/persistence.py`
- Create: `poker-agent/tests/unit/test_persistence.py`

- [ ] **Step 1: Write persistence unit tests**

Create `poker-agent/tests/unit/test_persistence.py`:

```python
from __future__ import annotations

import json
import tempfile
from pathlib import Path

from poker_agent.config.schema import MemoryConfig
from poker_agent.memory.hand_memory import HandMemory
from poker_agent.memory.manager import MemoryManager
from poker_agent.memory.opponent_memory import OpponentMemory
from poker_agent.memory.session_memory import SessionMemory
from poker_agent.models import ActionEntry, ActionResponse, ActionType, GamePhase, GameStateView, PlayerSummary, PlayerView, PotInfo


def _make_state() -> GameStateView:
    return GameStateView(
        phase=GamePhase.FLOP,
        self=PlayerView(seat_id="1", chips=1000),
        pot=PotInfo(total_pot=100, main_pot=100),
        current_bet=50,
    )


def test_hand_memory_roundtrip() -> None:
    hm = HandMemory()
    hm.record_state(_make_state())
    hm.add_decision(_make_state(), ActionResponse(action_type=ActionType.CALL, amount=50), "test")
    data = hm.to_dict()
    hm2 = HandMemory.from_dict(data)
    assert hm2.snapshots == hm.snapshots
    assert hm2.decisions == hm.decisions


def test_opponent_memory_roundtrip() -> None:
    om = OpponentMemory()
    om.update_from_action("p2", ActionEntry(seat_id="p2", action_type=ActionType.RAISE, amount=100, phase=GamePhase.FLOP))
    om.update_from_action("p2", ActionEntry(seat_id="p2", action_type=ActionType.FOLD, amount=0, phase=GamePhase.RIVER))
    data = om.to_dict()
    om2 = OpponentMemory.from_dict(data)
    assert om2.action_counts == om.action_counts
    assert om2.fold_counts == om.fold_counts


def test_session_memory_roundtrip() -> None:
    sm = SessionMemory()
    sm.record_hand(won=True)
    sm.record_hand(won=False)
    sm.add_note("test note")
    data = sm.to_dict()
    sm2 = SessionMemory.from_dict(data)
    assert sm2.hands_played == 2
    assert sm2.hands_won == 1
    assert sm2.notes == ["test note"]


def test_memory_manager_save_load() -> None:
    with tempfile.TemporaryDirectory() as tmpdir:
        config = MemoryConfig(persistence="file", file_path=str(Path(tmpdir) / "mem"))
        mm = MemoryManager(config)
        mm.record_decision(_make_state(), ActionResponse(action_type=ActionType.CALL, amount=50), "test")
        mm.session_memory.record_hand(won=True)
        mm.save()

        mm2 = MemoryManager(config)
        mm2.load()
        assert mm2.session_memory.hands_played == 1
        assert mm2.session_memory.hands_won == 1
        assert len(mm2.hand_memory.decisions) == 1


def test_memory_manager_save_load_disabled() -> None:
    config = MemoryConfig(persistence="memory")
    mm = MemoryManager(config)
    mm.save()  # should not raise
    mm.load()  # should not raise
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /mnt/d/workspace/texas/poker-agent && python -m pytest tests/unit/test_persistence.py -v
```

Expected: FAIL — `to_dict`/`from_dict`/`save`/`load` don't exist yet.

- [ ] **Step 3: Add to_dict/from_dict to HandMemory**

Replace the full content of `poker-agent/src/poker_agent/memory/hand_memory.py`:

```python
from __future__ import annotations

from dataclasses import dataclass, field

from ..models import ActionEntry, ActionResponse, GameStateView


@dataclass
class HandMemory:
    snapshots: list[str] = field(default_factory=list)
    decisions: list[str] = field(default_factory=list)

    def record_state(self, game_state: GameStateView) -> None:
        self.snapshots.append(f"{game_state.phase.value}:{game_state.pot.total_pot}:{game_state.current_bet}")

    def add_decision(self, game_state: GameStateView, action: ActionResponse, reasoning: str) -> None:
        self.record_state(game_state)
        self.decisions.append(f"{action.action_type.value}:{action.amount}:{reasoning}")

    def get_summary(self) -> str:
        if not self.snapshots:
            return "no hand memory"
        return " | ".join(self.snapshots[-5:])

    def clear(self) -> None:
        self.snapshots.clear()
        self.decisions.clear()

    def to_dict(self) -> dict:
        return {"snapshots": list(self.snapshots), "decisions": list(self.decisions)}

    @classmethod
    def from_dict(cls, data: dict) -> HandMemory:
        return cls(snapshots=list(data.get("snapshots", [])), decisions=list(data.get("decisions", [])))
```

- [ ] **Step 4: Add to_dict/from_dict to OpponentMemory**

Replace the full content of `poker-agent/src/poker_agent/memory/opponent_memory.py`:

```python
from __future__ import annotations

from dataclasses import dataclass, field

from ..models import ActionEntry, PlayerSummary


@dataclass
class OpponentMemory:
    action_counts: dict[str, int] = field(default_factory=dict)
    fold_counts: dict[str, int] = field(default_factory=dict)

    def update_from_action(self, seat_id: str, action: ActionEntry) -> None:
        key = f"{seat_id}:{action.action_type.value}"
        self.action_counts[key] = self.action_counts.get(key, 0) + 1
        if action.action_type.value == "FOLD":
            self.fold_counts[seat_id] = self.fold_counts.get(seat_id, 0) + 1

    def update_from_player(self, player: PlayerSummary) -> None:
        self.fold_counts.setdefault(player.seat_id, 0)

    def get_summary(self) -> str:
        if not self.action_counts:
            return "no opponent profile"
        top_entries = sorted(self.action_counts.items(), key=lambda item: item[1], reverse=True)[:3]
        return ", ".join(f"{key}={count}" for key, count in top_entries)

    def to_dict(self) -> dict:
        return {"action_counts": dict(self.action_counts), "fold_counts": dict(self.fold_counts)}

    @classmethod
    def from_dict(cls, data: dict) -> OpponentMemory:
        return cls(action_counts=dict(data.get("action_counts", {})), fold_counts=dict(data.get("fold_counts", {})))
```

- [ ] **Step 5: Add to_dict/from_dict to SessionMemory**

Replace the full content of `poker-agent/src/poker_agent/memory/session_memory.py`:

```python
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class SessionMemory:
    hands_played: int = 0
    hands_won: int = 0
    notes: list[str] = field(default_factory=list)

    def record_hand(self, won: bool | None = None) -> None:
        self.hands_played += 1
        if won:
            self.hands_won += 1

    def add_note(self, note: str) -> None:
        self.notes.append(note)

    def get_summary(self) -> str:
        win_rate = self.hands_won / self.hands_played if self.hands_played else 0.0
        note_text = "; ".join(self.notes[-3:]) if self.notes else "no session notes"
        return f"hands={self.hands_played}, wins={self.hands_won}, win_rate={win_rate:.2f}, notes={note_text}"

    def to_dict(self) -> dict:
        return {"hands_played": self.hands_played, "hands_won": self.hands_won, "notes": list(self.notes)}

    @classmethod
    def from_dict(cls, data: dict) -> SessionMemory:
        return cls(
            hands_played=data.get("hands_played", 0),
            hands_won=data.get("hands_won", 0),
            notes=list(data.get("notes", [])),
        )
```

- [ ] **Step 6: Add save/load to MemoryManager**

Replace the full content of `poker-agent/src/poker_agent/memory/manager.py`:

```python
from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from pathlib import Path

from ..config.schema import MemoryConfig
from ..models import ActionResponse, GameStateView, PlayerSummary
from .hand_memory import HandMemory
from .opponent_memory import OpponentMemory
from .session_memory import SessionMemory

log = logging.getLogger(__name__)


@dataclass
class MemoryManager:
    config: MemoryConfig
    hand_memory: HandMemory | None = None
    opponent_memory: OpponentMemory | None = None
    session_memory: SessionMemory | None = None

    def __post_init__(self) -> None:
        if self.config.hand_memory:
            self.hand_memory = HandMemory()
        if self.config.opponent_memory:
            self.opponent_memory = OpponentMemory()
        if self.config.session_memory:
            self.session_memory = SessionMemory()

    def get_context(self) -> str:
        parts: list[str] = []
        if self.hand_memory:
            parts.append(f"当前牌局记忆:\n{self.hand_memory.get_summary()}")
        if self.opponent_memory:
            parts.append(f"对手画像:\n{self.opponent_memory.get_summary()}")
        if self.session_memory:
            parts.append(f"会话记忆:\n{self.session_memory.get_summary()}")
        return "\n\n".join(parts)

    def record_decision(self, game_state: GameStateView, action: ActionResponse, reasoning: str) -> None:
        if self.hand_memory:
            self.hand_memory.add_decision(game_state, action, reasoning)

    def record_opponents(self, opponents: list[PlayerSummary]) -> None:
        if not self.opponent_memory:
            return
        for opponent in opponents:
            self.opponent_memory.update_from_player(opponent)

    def new_hand(self) -> None:
        if self.hand_memory:
            self.hand_memory.clear()
        if self.session_memory:
            self.session_memory.record_hand()

    def save(self) -> None:
        if self.config.persistence != "file":
            return
        path = Path(self.config.file_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        data = {
            "hand_memory": self.hand_memory.to_dict() if self.hand_memory else None,
            "opponent_memory": self.opponent_memory.to_dict() if self.opponent_memory else None,
            "session_memory": self.session_memory.to_dict() if self.session_memory else None,
        }
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        log.debug("Memory saved to %s", path)

    def load(self) -> None:
        if self.config.persistence != "file":
            return
        path = Path(self.config.file_path)
        if not path.exists():
            log.debug("No memory file at %s, starting fresh", path)
            return
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            if self.hand_memory and data.get("hand_memory"):
                self.hand_memory = HandMemory.from_dict(data["hand_memory"])
            if self.opponent_memory and data.get("opponent_memory"):
                self.opponent_memory = OpponentMemory.from_dict(data["opponent_memory"])
            if self.session_memory and data.get("session_memory"):
                self.session_memory = SessionMemory.from_dict(data["session_memory"])
            log.debug("Memory loaded from %s", path)
        except (json.JSONDecodeError, KeyError) as e:
            log.warning("Failed to load memory from %s: %s", path, e)
```

- [ ] **Step 7: Run tests**

```bash
cd /mnt/d/workspace/texas/poker-agent && python -m pytest tests/unit/test_persistence.py -v
```

Expected: All 5 tests pass.

- [ ] **Step 8: Commit**

```bash
git add poker-agent/src/poker_agent/memory/ poker-agent/tests/unit/test_persistence.py
git commit -m "feat: add file-based memory persistence with to_dict/from_dict serialization"
```

---

## Task 3: Add AgentRegistry for Per-Game Agent Lifecycle

The current gRPC server creates a single agent for all games. We need per-game agent instances that are created on first `MakeDecision` for a game and cleaned up when the game ends.

**Files:**
- Create: `poker-agent/src/poker_agent/grpc/registry.py`
- Modify: `poker-agent/src/poker_agent/grpc/server.py`
- Create: `poker-agent/tests/unit/test_registry.py`

- [ ] **Step 1: Write AgentRegistry unit tests**

Create `poker-agent/tests/unit/test_registry.py`:

```python
from __future__ import annotations

import asyncio

from poker_agent.agent.react_agent import PokerReactAgent
from poker_agent.config.schema import AgentConfig
from poker_agent.grpc.registry import AgentRegistry
from poker_agent.models import ActionResponse, ActionType, DecisionRequest, GamePhase, GameStateView, LegalAction, PlayerView, PotInfo


def _make_request(game_id: str) -> DecisionRequest:
    return DecisionRequest(
        game_id=game_id,
        game_state=GameStateView(
            phase=GamePhase.PRE_FLOP,
            self=PlayerView(seat_id="1", chips=1000),
            pot=PotInfo(total_pot=0, main_pot=0),
        ),
        legal_actions=[LegalAction(type=ActionType.CHECK, is_available=True)],
    )


def test_registry_creates_agent_per_game() -> None:
    registry = AgentRegistry(AgentConfig())
    a1 = registry.get_or_create("game-1")
    a2 = registry.get_or_create("game-2")
    a1_again = registry.get_or_create("game-1")
    assert a1 is not a2
    assert a1 is a1_again
    assert registry.active_count() == 2


def test_registry_remove_game() -> None:
    registry = AgentRegistry(AgentConfig())
    registry.get_or_create("game-1")
    registry.get_or_create("game-2")
    registry.remove("game-1")
    assert registry.active_count() == 1
    assert registry.get_or_create("game-1") is not registry.get_or_create("game-2")


def test_registry_decide_uses_per_game_agent() -> None:
    async def run() -> None:
        registry = AgentRegistry(AgentConfig())
        req = _make_request("game-1")
        response = await registry.decide(req)
        assert isinstance(response, ActionResponse)
        assert response.action_type in {ActionType.CHECK, ActionType.CALL, ActionType.FOLD}
        # Memory should be on the per-game agent
        agent = registry.get_or_create("game-1")
        assert agent.memory.hand_memory is not None
        assert len(agent.memory.hand_memory.decisions) == 1

    asyncio.run(run())


def test_registry_cleanup_saves_memory(tmp_path) -> None:
    config = AgentConfig()
    config.memory.persistence = "file"
    config.memory.file_path = str(tmp_path / "mem")
    registry = AgentRegistry(config)

    async def run() -> None:
        await registry.decide(_make_request("game-1"))
        registry.remove("game-1")

    asyncio.run(run())
    # Memory file should have been saved
    assert (tmp_path / "mem").exists()
```

Note: there's a typo in the first test (`not a game-2` should be `is not a2`). Fix in step 3.

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /mnt/d/workspace/texas/poker-agent && python -m pytest tests/unit/test_registry.py -v
```

Expected: FAIL — `AgentRegistry` doesn't exist.

- [ ] **Step 3: Implement AgentRegistry**

Create `poker-agent/src/poker_agent/grpc/registry.py`:

```python
from __future__ import annotations

import logging
from typing import Any

from ..agent.react_agent import PokerReactAgent
from ..config.schema import AgentConfig
from ..models import ActionResponse, DecisionRequest

log = logging.getLogger(__name__)


class AgentRegistry:
    """Manages per-game PokerReactAgent instances."""

    def __init__(self, config: AgentConfig) -> None:
        self._config = config
        self._agents: dict[str, PokerReactAgent] = {}

    def get_or_create(self, game_id: str) -> PokerReactAgent:
        if game_id not in self._agents:
            log.info("Creating agent for game %s", game_id)
            agent = PokerReactAgent(self._config)
            agent.memory.load()
            self._agents[game_id] = agent
        return self._agents[game_id]

    def remove(self, game_id: str) -> None:
        agent = self._agents.pop(game_id, None)
        if agent is not None:
            agent.memory.save()
            log.info("Removed agent for game %s", game_id)

    def active_count(self) -> int:
        return len(self._agents)

    async def decide(self, request: DecisionRequest) -> ActionResponse:
        agent = self.get_or_create(request.game_id)
        return await agent.decide(request)
```

- [ ] **Step 4: Fix the test typo**

In `poker-agent/tests/unit/test_registry.py`, fix line:

```python
    assert a1 is not a game-2
```

to:

```python
    assert a1 is not a2
```

- [ ] **Step 5: Run tests**

```bash
cd /mnt/d/workspace/texas/poker-agent && python -m pytest tests/unit/test_registry.py -v
```

Expected: All 4 tests pass.

- [ ] **Step 6: Update gRPC server to use AgentRegistry**

Modify `poker-agent/src/poker_agent/grpc/server.py`. Replace the `PokerAgentService` class and `serve` function:

```python
@dataclass
class PokerAgentService(pb2_grpc.PokerAgentServicer):
    registry: AgentRegistry

    async def Register(self, request: pb2.RegisterRequest, context: grpc.aio.ServicerContext) -> pb2.RegisterResponse:
        message = f"registered {request.agent_name or request.agent_id}"
        return pb2.RegisterResponse(success=True, message=message)

    async def MakeDecision(self, request: pb2.DecisionRequest, context: grpc.aio.ServicerContext) -> pb2.ActionResponse:
        decision_request = self._decision_request_from_proto(request)
        action = await self.registry.decide(decision_request)
        return pb2.ActionResponse(
            action_type=pb2.ActionType.Value(action.action_type.value),
            amount=action.amount,
            reasoning=action.reasoning,
        )

    async def Ping(self, request: pb2.PingRequest, context: grpc.aio.ServicerContext) -> pb2.PingResponse:
        return pb2.PingResponse(success=True, server_version="poker-agent-0.1.0")
```

Update the imports at the top to include `AgentRegistry`:

```python
from .registry import AgentRegistry
```

Update the `serve` function:

```python
async def serve(config_path: str, bind_address: str = "0.0.0.0:9090") -> None:
    config = load_config(config_path)
    registry = AgentRegistry(config)
    server = grpc.aio.server()
    pb2_grpc.add_PokerAgentServicer_to_server(PokerAgentService(registry), server)
    server.add_insecure_port(bind_address)
    await server.start()
    await server.wait_for_termination()
```

Remove the now-unused `PokerReactAgent` import from the server file (it's used via the registry now).

- [ ] **Step 7: Update the existing integration test for the new server API**

Update `poker-agent/tests/integration/test_grpc_service.py` to use `AgentRegistry`:

```python
from poker_agent.grpc.registry import AgentRegistry

def test_grpc_service_make_decision_roundtrip() -> None:
    async def run_test() -> None:
        server = grpc.aio.server()
        config = AgentConfig()
        registry = AgentRegistry(config)
        service = PokerAgentService(registry)
        pb2_grpc.add_PokerAgentServicer_to_server(service, server)
        port = server.add_insecure_port("127.0.0.1:0")
        await server.start()
        channel = grpc.aio.insecure_channel(f"127.0.0.1:{port}")
        stub = pb2_grpc.PokerAgentStub(channel)

        try:
            response = await stub.MakeDecision(_build_state())
            assert response.action_type in {pb2.CHECK, pb2.CALL}
            # Verify per-game agent was created and memory updated
            agent = registry.get_or_create("game-1")
            assert len(agent.memory.hand_memory.decisions) == 1
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run_test())
```

- [ ] **Step 8: Run all tests**

```bash
cd /mnt/d/workspace/texas/poker-agent && python -m pytest tests/ -v
```

Expected: All tests pass.

- [ ] **Step 9: Commit**

```bash
git add poker-agent/src/poker_agent/grpc/registry.py poker-agent/src/poker_agent/grpc/server.py poker-agent/tests/
git commit -m "feat: add AgentRegistry for per-game agent lifecycle with memory save/load"
```

---

## Task 4: Add gRPC Dependencies to Java Build

**Files:**
- Modify: `pom.xml` (root — add gRPC version properties and dependency management)
- Modify: `poker-common/pom.xml` (add gRPC + protobuf deps + protoc plugin)
- Modify: `poker-ai/pom.xml` (add gRPC dependency)
- Modify: `poker-server/pom.xml` (add gRPC dependency)

- [ ] **Step 1: Add gRPC properties and dependency management to root pom.xml**

In `/mnt/d/workspace/texas/pom.xml`, add to `<properties>`:

```xml
        <grpc.version>1.62.2</grpc.version>
        <protobuf.version>3.25.3</protobuf.version>
        <javax.annotation.version>1.3.2</javax.annotation.version>
```

Add to `<dependencyManagement><dependencies>`:

```xml
            <dependency>
                <groupId>io.grpc</groupId>
                <artifactId>grpc-bom</artifactId>
                <version>${grpc.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
```

- [ ] **Step 2: Copy proto file to poker-common**

```bash
cp /mnt/d/workspace/texas/poker-agent/proto/poker_agent.proto /mnt/d/workspace/texas/poker-common/proto/poker_agent.proto
mkdir -p /mnt/d/workspace/texas/poker-common/proto
cp /mnt/d/workspace/texas/poker-agent/proto/poker_agent.proto /mnt/d/workspace/texas/poker-common/proto/poker_agent.proto
```

- [ ] **Step 3: Update poker-common/pom.xml with gRPC dependencies and protoc plugin**

Read the current `poker-common/pom.xml` first, then add gRPC dependencies and the protobuf-maven-plugin. The full file should be:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.texasholdem</groupId>
        <artifactId>texas-holdem-server</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>poker-common</artifactId>

    <dependencies>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-netty-shaded</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-protobuf</artifactId>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-stub</artifactId>
        </dependency>
        <dependency>
            <groupId>javax.annotation</groupId>
            <artifactId>javax.annotation-api</artifactId>
            <version>${javax.annotation.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>1.7.1</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>0.6.1</version>
                <configuration>
                    <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
                    <pluginId>grpc-java</pluginId>
                    <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                            <goal>compile-custom</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Add gRPC dependency to poker-ai/pom.xml**

Add to `<dependencies>` in `poker-ai/pom.xml`:

```xml
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-stub</artifactId>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-protobuf</artifactId>
        </dependency>
        <dependency>
            <groupId>javax.annotation</groupId>
            <artifactId>javax.annotation-api</artifactId>
            <version>${javax.annotation.version}</version>
        </dependency>
```

Also add dependency on `poker-common` (for the generated stubs):

```xml
        <dependency>
            <groupId>com.texasholdem</groupId>
            <artifactId>poker-common</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 5: Verify build compiles**

```bash
cd /mnt/d/workspace/texas && mvn clean compile -pl poker-common,poker-ai,poker-server -am
```

Expected: BUILD SUCCESS. Proto stubs generated in `poker-common/target/generated-sources/`.

- [ ] **Step 6: Commit**

```bash
git add pom.xml poker-common/pom.xml poker-common/proto/ poker-ai/pom.xml
git commit -m "build: add gRPC dependencies and protobuf code generation to Java modules"
```

---

## Task 5: Implement Java-Side ProtoAdapter

Converts Java `GameState`/`PlayerState`/`Action` to protobuf messages for the gRPC call.

**Files:**
- Create: `poker-ai/src/main/java/com/texasholdem/ai/grpc/ProtoAdapter.java`
- Create: `poker-ai/src/test/java/com/texasholdem/ai/grpc/ProtoAdapterTest.java`

- [ ] **Step 1: Write ProtoAdapter test**

Create `poker-ai/src/test/java/com/texasholdem/ai/grpc/ProtoAdapterTest.java`:

```java
package com.texasholdem.ai.grpc;

import com.texasholdem.core.model.*;
import com.texasholdem.poker_agent.PokerAgentProto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProtoAdapterTest {

    @Test
    public void testConvertCard() {
        Card card = new Card(Rank.ACE, Suit.SPADES);
        PokerAgentProto.Card proto = ProtoAdapter.toProto(card);
        assertEquals(PokerAgentProto.Rank.ACE, proto.getRank());
        assertEquals(PokerAgentProto.Suit.SPADES, proto.getSuit());
    }

    @Test
    public void testConvertGamePhase() {
        assertEquals(PokerAgentProto.GamePhase.PRE_FLOP, ProtoAdapter.toProto(GamePhase.PRE_FLOP));
        assertEquals(PokerAgentProto.GamePhase.FLOP, ProtoAdapter.toProto(GamePhase.FLOP));
        assertEquals(PokerAgentProto.GamePhase.SETTLED, ProtoAdapter.toProto(GamePhase.SETTLED));
    }

    @Test
    public void testConvertActionType() {
        assertEquals(PokerAgentProto.ActionType.FOLD, ProtoAdapter.toProto(ActionType.FOLD));
        assertEquals(PokerAgentProto.ActionType.CHECK, ProtoAdapter.toProto(ActionType.CHECK));
        assertEquals(PokerAgentProto.ActionType.CALL, ProtoAdapter.toProto(ActionType.CALL));
        assertEquals(PokerAgentProto.ActionType.BET, ProtoAdapter.toProto(ActionType.BET));
        assertEquals(PokerAgentProto.ActionType.RAISE, ProtoAdapter.toProto(ActionType.RAISE));
        assertEquals(PokerAgentProto.ActionType.ALL_IN, ProtoAdapter.toProto(ActionType.ALL_IN));
    }

    @Test
    public void testConvertPlayerState() {
        PlayerState player = new PlayerState("seat1", 1000);
        player = player.withHoleCards(Arrays.asList(new Card(Rank.KING, Suit.HEARTS), new Card(Rank.QUEEN, Suit.DIAMONDS)));
        PokerAgentProto.PlayerView proto = ProtoAdapter.toProto(player, Arrays.asList(
                new Card(Rank.KING, Suit.HEARTS), new Card(Rank.QUEEN, Suit.DIAMONDS)));
        assertEquals("seat1", proto.getSeatId());
        assertEquals(1000, proto.getChips());
        assertEquals(2, proto.getHoleCardsCount());
    }

    @Test
    public void testConvertPotInfo() {
        PotInfo pot = PotInfo.empty();
        PokerAgentProto.PotInfo proto = ProtoAdapter.toProto(pot);
        assertEquals(0, proto.getTotalPot());
    }

    @Test
    public void testBuildDecisionRequest() {
        PlayerState player = new PlayerState("seat1", 1000);
        player = player.withHoleCards(Arrays.asList(new Card(Rank.ACE, Suit.SPADES), new Card(Rank.KING, Suit.SPADES)));
        List<PlayerState> players = Arrays.asList(player, new PlayerState("seat2", 900));
        GameState state = new GameState(
                GamePhase.PRE_FLOP,
                Collections.emptyList(),
                players,
                PotInfo.empty(),
                Collections.emptyList(),
                0, 0, 0, 1, 0,
                Collections.emptyList(), 0
        );
        PokerAgentProto.DecisionRequest proto = ProtoAdapter.buildDecisionRequest("game-1", state, player);
        assertEquals("game-1", proto.getGameId());
        assertTrue(proto.getGameState().getSelf().getHoleCardsCount() > 0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /mnt/d/workspace/texas && mvn test -pl poker-ai -Dtest=ProtoAdapterTest
```

Expected: FAIL — `ProtoAdapter` class doesn't exist.

- [ ] **Step 3: Implement ProtoAdapter**

Create `poker-ai/src/main/java/com/texasholdem/ai/grpc/ProtoAdapter.java`:

```java
package com.texasholdem.ai.grpc;

import com.texasholdem.core.model.*;
import com.texasholdem.poker_agent.PokerAgentProto;

import java.util.ArrayList;
import java.util.List;

public final class ProtoAdapter {

    private ProtoAdapter() {}

    public static PokerAgentProto.Card toProto(Card card) {
        return PokerAgentProto.Card.newBuilder()
                .setRank(toProto(card.getRank()))
                .setSuit(toProto(card.getSuit()))
                .build();
    }

    public static PokerAgentProto.Rank toProto(Rank rank) {
        return PokerAgentProto.Rank.valueOf(rank.name());
    }

    public static PokerAgentProto.Suit toProto(Suit suit) {
        return PokerAgentProto.Suit.valueOf(suit.name());
    }

    public static PokerAgentProto.GamePhase toProto(GamePhase phase) {
        return PokerAgentProto.GamePhase.valueOf(phase.name());
    }

    public static PokerAgentProto.ActionType toProto(ActionType type) {
        return PokerAgentProto.ActionType.valueOf(type.name());
    }

    public static PokerAgentProto.PlayerView toProto(PlayerState player, List<Card> holeCards) {
        PokerAgentProto.PlayerView.Builder builder = PokerAgentProto.PlayerView.newBuilder()
                .setSeatId(player.getSeatId())
                .setChips(player.getChips())
                .setRoundContribution(player.getRoundContribution())
                .setIsAllIn(player.isAllIn())
                .setIsFolded(player.isFolded());
        for (Card card : holeCards) {
            builder.addHoleCards(toProto(card));
        }
        return builder.build();
    }

    public static PokerAgentProto.PlayerSummary toSummaryProto(PlayerState player) {
        return PokerAgentProto.PlayerSummary.newBuilder()
                .setSeatId(player.getSeatId())
                .setChips(player.getChips())
                .setRoundContribution(player.getRoundContribution())
                .setIsAllIn(player.isAllIn())
                .setIsFolded(player.isFolded())
                .build();
    }

    public static PokerAgentProto.PotInfo toProto(PotInfo pot) {
        PokerAgentProto.PotInfo.Builder builder = PokerAgentProto.PotInfo.newBuilder()
                .setTotalPot(pot.getTotalPot())
                .setMainPot(pot.getMainPot());
        for (PotSlice slice : pot.getSidePots()) {
            builder.addSidePots(toProto(slice));
        }
        return builder.build();
    }

    public static PokerAgentProto.PotSlice toProto(PotSlice slice) {
        PokerAgentProto.PotSlice.Builder builder = PokerAgentProto.PotSlice.newBuilder()
                .setAmount(slice.getAmount());
        for (String seatId : slice.getEligibleSeatIds()) {
            builder.addEligibleSeatIds(seatId);
        }
        return builder.build();
    }

    public static PokerAgentProto.ActionEntry toActionEntryProto(Action action, GamePhase phase) {
        return PokerAgentProto.ActionEntry.newBuilder()
                .setSeatId(action.getPlayerId())
                .setActionType(toProto(action.getType()))
                .setAmount(action.getAmount())
                .setPhase(toProto(phase))
                .build();
    }

    public static PokerAgentProto.LegalAction toProto(ActionType type, boolean available, int minAmount, int maxAmount) {
        return PokerAgentProto.LegalAction.newBuilder()
                .setType(toProto(type))
                .setIsAvailable(available)
                .setMinAmount(minAmount)
                .setMaxAmount(maxAmount)
                .build();
    }

    public static PokerAgentProto.DecisionRequest buildDecisionRequest(String gameId, GameState state, PlayerState self) {
        // Build game state view
        PokerAgentProto.GameStateView.Builder stateBuilder = PokerAgentProto.GameStateView.newBuilder()
                .setPhase(toProto(state.getPhase()))
                .setDealerPosition(state.getDealerPosition())
                .setCurrentBet(state.getCurrentBet());

        // Board cards
        for (Card card : state.getBoard()) {
            stateBuilder.addBoard(toProto(card));
        }

        // Self (player view with hole cards)
        stateBuilder.setSelf(toProto(self, self.getHoleCards()));

        // Opponents (summaries without hole cards)
        for (PlayerState p : state.getPlayers()) {
            if (!p.getSeatId().equals(self.getSeatId())) {
                stateBuilder.addOpponents(toSummaryProto(p));
            }
        }

        // Pot
        stateBuilder.setPot(toProto(state.getPot()));

        // Action history
        for (Action action : state.getActionHistory()) {
            stateBuilder.addActionHistory(toActionEntryProto(action, state.getPhase()));
        }

        // Legal actions — determine from game state
        List<PokerAgentProto.LegalAction> legalActions = determineLegalActions(state, self);

        PokerAgentProto.DecisionRequest.Builder reqBuilder = PokerAgentProto.DecisionRequest.newBuilder()
                .setGameId(gameId)
                .setGameState(stateBuilder.build());
        for (PokerAgentProto.LegalAction la : legalActions) {
            reqBuilder.addLegalActions(la);
        }

        return reqBuilder.build();
    }

    private static List<PokerAgentProto.LegalAction> determineLegalActions(GameState state, PlayerState self) {
        List<PokerAgentProto.LegalAction> actions = new ArrayList<>();
        int toCall = state.getCurrentBet() - self.getRoundContribution();

        // FOLD is always available if there's something to call
        if (toCall > 0) {
            actions.add(toProto(ActionType.FOLD, true, 0, 0));
        }

        // CHECK if nothing to call
        if (toCall <= 0) {
            actions.add(toProto(ActionType.CHECK, true, 0, 0));
        }

        // CALL if there's something to call and player has enough chips
        if (toCall > 0 && self.getChips() > 0) {
            int callAmount = Math.min(toCall, self.getChips());
            actions.add(toProto(ActionType.CALL, true, callAmount, callAmount));
        }

        // BET if no current bet
        if (state.getCurrentBet() == 0 && self.getChips() > 0) {
            int minBet = Math.min(10, self.getChips()); // Use big blind as min bet
            actions.add(toProto(ActionType.BET, true, minBet, self.getChips()));
        }

        // RAISE if there's a current bet
        if (state.getCurrentBet() > 0 && self.getChips() > toCall) {
            int minRaise = state.getCurrentBet() * 2;
            actions.add(toProto(ActionType.RAISE, true, Math.min(minRaise, self.getChips()), self.getChips()));
        }

        // ALL_IN if player has chips
        if (self.getChips() > 0) {
            actions.add(toProto(ActionType.ALL_IN, true, self.getChips(), self.getChips()));
        }

        return actions;
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd /mnt/d/workspace/texas && mvn test -pl poker-ai -Dtest=ProtoAdapterTest
```

Expected: All 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add poker-ai/src/main/java/com/texasholdem/ai/grpc/ProtoAdapter.java poker-ai/src/test/java/com/texasholdem/ai/grpc/ProtoAdapterTest.java
git commit -m "feat: add ProtoAdapter for GameState to protobuf conversion"
```

---

## Task 6: Implement GrpcAgentBridge

The core integration piece — a `BuiltinAgent` implementation that calls the Python gRPC server.

**Files:**
- Create: `poker-ai/src/main/java/com/texasholdem/ai/grpc/GrpcAgentConfig.java`
- Create: `poker-ai/src/main/java/com/texasholdem/ai/grpc/GrpcAgentBridge.java`
- Create: `poker-ai/src/test/java/com/texasholdem/ai/grpc/GrpcAgentBridgeTest.java`

- [ ] **Step 1: Write GrpcAgentBridge test**

Create `poker-ai/src/test/java/com/texasholdem/ai/grpc/GrpcAgentBridgeTest.java`:

```java
package com.texasholdem.ai.grpc;

import com.texasholdem.core.model.*;
import com.texasholdem.poker_agent.PokerAgentProto;
import com.texasholdem.poker_agent.PokerAgentGrpc;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class GrpcAgentBridgeTest {

    @Test
    public void testDecideCallsGrpcAndReturnsAction() throws Exception {
        // Create a fake gRPC server that returns CHECK
        String serverName = InProcessServerBuilder.generateName();

        PokerAgentGrpc.PokerAgentImplBase fakeService = new PokerAgentGrpc.PokerAgentImplBase() {
            @Override
            public void makeDecision(PokerAgentProto.DecisionRequest request,
                                     StreamObserver<PokerAgentProto.ActionResponse> responseObserver) {
                responseObserver.onNext(PokerAgentProto.ActionResponse.newBuilder()
                        .setActionType(PokerAgentProto.ActionType.CHECK)
                        .setAmount(0)
                        .setReasoning("test")
                        .build());
                responseObserver.onCompleted();
            }

            @Override
            public void register(PokerAgentProto.RegisterRequest request,
                                 StreamObserver<PokerAgentProto.RegisterResponse> responseObserver) {
                responseObserver.onNext(PokerAgentProto.RegisterResponse.newBuilder()
                        .setSuccess(true).setMessage("ok").build());
                responseObserver.onCompleted();
            }

            @Override
            public void ping(PokerAgentProto.PingRequest request,
                             StreamObserver<PokerAgentProto.PingResponse> responseObserver) {
                responseObserver.onNext(PokerAgentProto.PingResponse.newBuilder()
                        .setSuccess(true).build());
                responseObserver.onCompleted();
            }
        };

        io.grpc.Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor().addService(fakeService).build().start();

        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

        GrpcAgentConfig config = new GrpcAgentConfig();
        config.setTimeoutMs(5000);
        config.setFallbackAgent(null); // no fallback for this test

        GrpcAgentBridge bridge = new GrpcAgentBridge(config, channel);

        PlayerState player = new PlayerState("seat1", 1000);
        player = player.withHoleCards(Arrays.asList(new Card(Rank.ACE, Suit.SPADES), new Card(Rank.KING, Suit.SPADES)));
        GameState state = new GameState(
                GamePhase.PRE_FLOP, Collections.emptyList(),
                Arrays.asList(player, new PlayerState("seat2", 900)),
                PotInfo.empty(), Collections.emptyList(),
                0, 0, 0, 1, 0, Collections.emptyList(), 0);

        Action action = bridge.decide(state, player);
        assertEquals(ActionType.CHECK, action.getType());

        server.shutdown();
        channel.shutdown();
    }

    @Test
    public void testFallbackOnTimeout() throws Exception {
        GrpcAgentConfig config = new GrpcAgentConfig();
        config.setTimeoutMs(1); // 1ms timeout — will always fail
        SimpleHoldemAgent fallback = new SimpleHoldemAgent();
        config.setFallbackAgent(fallback);

        // Use a channel that's not connected to anything
        ManagedChannel channel = InProcessChannelBuilder.forName("nonexistent").directExecutor().build();

        GrpcAgentBridge bridge = new GrpcAgentBridge(config, channel);

        PlayerState player = new PlayerState("seat1", 1000);
        player = player.withHoleCards(Arrays.asList(new Card(Rank.TWO, Suit.HEARTS), new Card(Rank.THREE, Suit.DIAMONDS)));
        GameState state = new GameState(
                GamePhase.PRE_FLOP, Collections.emptyList(),
                Arrays.asList(player, new PlayerState("seat2", 900)),
                PotInfo.empty(), Collections.emptyList(),
                0, 0, 0, 1, 0, Collections.emptyList(), 0);

        Action action = bridge.decide(state, player);
        assertNotNull(action); // Should get fallback action
        channel.shutdown();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /mnt/d/workspace/texas && mvn test -pl poker-ai -Dtest=GrpcAgentBridgeTest
```

Expected: FAIL — `GrpcAgentBridge` doesn't exist.

- [ ] **Step 3: Implement GrpcAgentConfig**

Create `poker-ai/src/main/java/com/texasholdem/ai/grpc/GrpcAgentConfig.java`:

```java
package com.texasholdem.ai.grpc;

import com.texasholdem.ai.BuiltinAgent;

public class GrpcAgentConfig {
    private String host = "localhost";
    private int port = 9090;
    private int timeoutMs = 4000;
    private BuiltinAgent fallbackAgent;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public BuiltinAgent getFallbackAgent() { return fallbackAgent; }
    public void setFallbackAgent(BuiltinAgent fallbackAgent) { this.fallbackAgent = fallbackAgent; }
}
```

- [ ] **Step 4: Implement GrpcAgentBridge**

Create `poker-ai/src/main/java/com/texasholdem/ai/grpc/GrpcAgentBridge.java`:

```java
package com.texasholdem.ai.grpc;

import com.texasholdem.ai.BuiltinAgent;
import com.texasholdem.core.model.*;
import com.texasholdem.poker_agent.PokerAgentGrpc;
import com.texasholdem.poker_agent.PokerAgentProto;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class GrpcAgentBridge implements BuiltinAgent {
    private static final Logger log = LoggerFactory.getLogger(GrpcAgentBridge.class);

    private final GrpcAgentConfig config;
    private final ManagedChannel channel;
    private final PokerAgentGrpc.PokerAgentBlockingStub stub;

    public GrpcAgentBridge(GrpcAgentConfig config, ManagedChannel channel) {
        this.config = config;
        this.channel = channel;
        this.stub = PokerAgentGrpc.newBlockingStub(channel)
                .withDeadlineAfter(config.getTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    @Override
    public Action decide(GameState state, PlayerState self) {
        try {
            PokerAgentProto.DecisionRequest request = ProtoAdapter.buildDecisionRequest(
                    "game_" + state.getHandNumber(), state, self);
            PokerAgentProto.ActionResponse response = stub.makeDecision(request);
            return toAction(response, self.getSeatId());
        } catch (StatusRuntimeException e) {
            log.warn("gRPC call failed ({}), falling back", e.getStatus());
            return fallback(state, self);
        } catch (Exception e) {
            log.warn("Unexpected error in gRPC agent bridge, falling back", e);
            return fallback(state, self);
        }
    }

    private Action toAction(PokerAgentProto.ActionResponse response, String playerId) {
        ActionType type = ActionType.valueOf(response.getActionType().name());
        int amount = response.getAmount();
        switch (type) {
            case FOLD: return new FoldAction(playerId);
            case CHECK: return new CheckAction(playerId);
            case CALL: return new CallAction(playerId);
            case BET: return new BetAction(playerId, amount);
            case RAISE: return new RaiseAction(playerId, amount);
            case ALL_IN: return new AllInAction(playerId);
            default: return new FoldAction(playerId);
        }
    }

    private Action fallback(GameState state, PlayerState self) {
        if (config.getFallbackAgent() != null) {
            return config.getFallbackAgent().decide(state, self);
        }
        return new FoldAction(self.getSeatId());
    }

    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 5: Run test**

```bash
cd /mnt/d/workspace/texas && mvn test -pl poker-ai -Dtest=GrpcAgentBridgeTest
```

Expected: Both tests pass.

- [ ] **Step 6: Commit**

```bash
git add poker-ai/src/main/java/com/texasholdem/ai/grpc/ poker-ai/src/test/java/com/texasholdem/ai/grpc/
git commit -m "feat: add GrpcAgentBridge with timeout fallback to SimpleHoldemAgent"
```

---

## Task 7: Wire GrpcAgentBridge into AppConfig

Allow switching between `SimpleHoldemAgent` (default) and `GrpcAgentBridge` via Spring properties.

**Files:**
- Modify: `poker-server/src/main/java/com/texasholdem/server/config/AppConfig.java`
- Modify: `poker-server/pom.xml` (add gRPC dependency)

- [ ] **Step 1: Add gRPC dependency to poker-server/pom.xml**

Add to `<dependencies>`:

```xml
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-netty-shaded</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.texasholdem</groupId>
            <artifactId>poker-common</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 2: Update AppConfig**

Read the current `AppConfig.java` first, then replace with:

```java
package com.texasholdem.server.config;

import com.texasholdem.ai.BuiltinAgent;
import com.texasholdem.ai.SimpleHoldemAgent;
import com.texasholdem.ai.grpc.GrpcAgentBridge;
import com.texasholdem.ai.grpc.GrpcAgentConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

@Configuration
public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Value("${agent.type:simple}")
    private String agentType;

    @Value("${agent.grpc.host:localhost}")
    private String grpcHost;

    @Value("${agent.grpc.port:9090}")
    private int grpcPort;

    @Value("${agent.grpc.timeout-ms:4000}")
    private int grpcTimeoutMs;

    private ManagedChannel grpcChannel;

    @Bean
    public BuiltinAgent builtinAgent() {
        if ("grpc".equals(agentType)) {
            log.info("Creating GrpcAgentBridge -> {}:{}", grpcHost, grpcPort);
            GrpcAgentConfig config = new GrpcAgentConfig();
            config.setHost(grpcHost);
            config.setPort(grpcPort);
            config.setTimeoutMs(grpcTimeoutMs);
            config.setFallbackAgent(new SimpleHoldemAgent());

            grpcChannel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
                    .usePlaintext()
                    .build();
            return new GrpcAgentBridge(config, grpcChannel);
        }
        log.info("Creating SimpleHoldemAgent");
        return new SimpleHoldemAgent();
    }

    @PreDestroy
    public void cleanup() {
        if (grpcChannel != null) {
            grpcChannel.shutdown();
        }
    }
}
```

- [ ] **Step 3: Verify build**

```bash
cd /mnt/d/workspace/texas && mvn clean compile -pl poker-server -am
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add poker-server/pom.xml poker-server/src/main/java/com/texasholdem/server/config/AppConfig.java
git commit -m "feat: wire GrpcAgentBridge into AppConfig with agent.type property switching"
```

---

## Task 8: End-to-End Smoke Test

Verify the full Java → Python gRPC chain works.

**Files:**
- Create: `poker-agent/tests/integration/test_grpc_e2e.py`

- [ ] **Step 1: Write e2e test**

Create `poker-agent/tests/integration/test_grpc_e2e.py`:

```python
"""End-to-end test: Python gRPC server handles a realistic decision request
that mimics what the Java GrpcAgentBridge would send."""
from __future__ import annotations

import asyncio

import grpc

from poker_agent.config.schema import AgentConfig
from poker_agent.grpc.generated import poker_agent_pb2 as pb2
from poker_agent.grpc.generated import poker_agent_pb2_grpc as pb2_grpc
from poker_agent.grpc.registry import AgentRegistry
from poker_agent.grpc.server import PokerAgentService


def _build_realistic_request() -> pb2.DecisionRequest:
    """Mimics what ProtoAdapter.buildDecisionRequest would produce on the Java side."""
    return pb2.DecisionRequest(
        game_id="room_1_1",
        game_state=pb2.GameStateView(
            phase=pb2.PRE_FLOP,
            board=[],
            self=pb2.PlayerView(
                seat_id="Bot_1",
                chips=1000,
                hole_cards=[
                    pb2.Card(rank=pb2.ACE, suit=pb2.SPADES),
                    pb2.Card(rank=pb2.KING, suit=pb2.SPADES),
                ],
                round_contribution=10,
                is_all_in=False,
                is_folded=False,
            ),
            opponents=[
                pb2.PlayerSummary(seat_id="human_player", chips=980, round_contribution=20),
            ],
            pot=pb2.PotInfo(total_pot=30, main_pot=30, side_pots=[]),
            action_history=[
                pb2.ActionEntry(seat_id="human_player", action_type=pb2.RAISE, amount=20, phase=pb2.PRE_FLOP),
            ],
            dealer_position=0,
            current_bet=20,
        ),
        legal_actions=[
            pb2.LegalAction(type=pb2.FOLD, is_available=True),
            pb2.LegalAction(type=pb2.CALL, is_available=True, min_amount=20, max_amount=20),
            pb2.LegalAction(type=pb2.RAISE, is_available=True, min_amount=40, max_amount=1000),
            pb2.LegalAction(type=pb2.ALL_IN, is_available=True, min_amount=1000, max_amount=1000),
        ],
        timing=pb2.TimingInfo(decision_deadline_ms=0, max_think_time_ms=4000),
    )


def test_e2e_realistic_decision() -> None:
    async def run() -> None:
        server = grpc.aio.server()
        config = AgentConfig()
        registry = AgentRegistry(config)
        service = PokerAgentService(registry)
        pb2_grpc.add_PokerAgentServicer_to_server(service, server)
        port = server.add_insecure_port("127.0.0.1:0")
        await server.start()

        channel = grpc.aio.insecure_channel(f"127.0.0.1:{port}")
        stub = pb2_grpc.PokerAgentStub(channel)

        try:
            # Register
            reg_resp = await stub.Register(pb2.RegisterRequest(agent_id="java-bridge", agent_name="GrpcAgentBridge"))
            assert reg_resp.success

            # Make decision
            resp = await stub.MakeDecision(_build_realistic_request())
            assert resp.action_type in {pb2.FOLD, pb2.CALL, pb2.RAISE, pb2.ALL_IN}
            assert resp.reasoning  # Should have some reasoning

            # Verify per-game agent was created
            agent = registry.get_or_create("room_1_1")
            assert len(agent.memory.hand_memory.decisions) == 1

            # Ping
            ping = await stub.Ping(pb2.PingRequest())
            assert ping.success

            # Second decision for same game — should reuse agent
            resp2 = await stub.MakeDecision(_build_realistic_request())
            assert resp2.action_type in {pb2.FOLD, pb2.CALL, pb2.RAISE, pb2.ALL_IN}
            assert len(agent.memory.hand_memory.decisions) == 2

            # Remove game — should save memory
            registry.remove("room_1_1")
            assert registry.active_count() == 0
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run())


def test_e2e_multiple_games() -> None:
    """Verify multiple concurrent games each get their own agent."""
    async def run() -> None:
        server = grpc.aio.server()
        config = AgentConfig()
        registry = AgentRegistry(config)
        service = PokerAgentService(registry)
        pb2_grpc.add_PokerAgentServicer_to_server(service, server)
        port = server.add_insecure_port("127.0.0.1:0")
        await server.start()

        channel = grpc.aio.insecure_channel(f"127.0.0.1:{port}")
        stub = pb2_grpc.PokerAgentStub(channel)

        try:
            for game_id in ["room_1", "room_2", "room_3"]:
                req = pb2.DecisionRequest(
                    game_id=game_id,
                    game_state=pb2.GameStateView(
                        phase=pb2.FLOP,
                        self=pb2.PlayerView(seat_id="bot", chips=500),
                        pot=pb2.PotInfo(total_pot=100, main_pot=100),
                    ),
                    legal_actions=[pb2.LegalAction(type=pb2.CHECK, is_available=True)],
                )
                await stub.MakeDecision(req)

            assert registry.active_count() == 3

            # Each game's agent should have independent memory
            for game_id in ["room_1", "room_2", "room_3"]:
                agent = registry.get_or_create(game_id)
                assert len(agent.memory.hand_memory.decisions) == 1
        finally:
            await channel.close()
            await server.stop(0)

    asyncio.run(run())
```

- [ ] **Step 2: Run e2e tests**

```bash
cd /mnt/d/workspace/texas/poker-agent && python -m pytest tests/integration/test_grpc_e2e.py -v
```

Expected: Both tests pass.

- [ ] **Step 3: Run full test suite**

```bash
cd /mnt/d/workspace/texas/poker-agent && python -m pytest tests/ -v
```

Expected: All tests pass (unit + integration + e2e).

- [ ] **Step 4: Commit**

```bash
git add poker-agent/tests/integration/test_grpc_e2e.py
git commit -m "test: add e2e integration tests for gRPC server with multi-game support"
```

---

## Task 9: Full Build Verification

- [ ] **Step 1: Run Java build with all tests**

```bash
cd /mnt/d/workspace/texas && mvn clean test
```

Expected: BUILD SUCCESS. All Java tests pass including ProtoAdapterTest and GrpcAgentBridgeTest.

- [ ] **Step 2: Run Python tests**

```bash
cd /mnt/d/workspace/texas/poker-agent && python -m pytest tests/ -v --tb=short
```

Expected: All tests pass.

- [ ] **Step 3: Manual smoke test — start Python agent server**

```bash
cd /mnt/d/workspace/texas/poker-agent && python -m poker_agent.grpc.server --config config/default.yaml --bind 0.0.0.0:9090
```

Verify server starts without errors. Ctrl+C to stop.

- [ ] **Step 4: Final commit with any remaining fixes**

```bash
git add -A
git commit -m "chore: final integration fixes for gRPC bridge"
```

---

## Usage

After implementation, to enable the gRPC agent bridge in the Java server:

```properties
# application.properties
agent.type=grpc
agent.grpc.host=localhost
agent.grpc.port=9090
agent.grpc.timeout-ms=4000
```

Start the Python agent first:
```bash
cd poker-agent && python -m poker_agent.grpc.server --config config/default.yaml
```

Then start the Java server. Bots in game rooms will now use the Python ReAct agent with automatic fallback to SimpleHoldemAgent on timeout.
