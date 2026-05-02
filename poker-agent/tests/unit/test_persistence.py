"""Tests for memory persistence (to_dict / from_dict / save / load)."""

from __future__ import annotations

import json
import tempfile
from pathlib import Path

import pytest

from poker_agent.config.schema import MemoryConfig
from poker_agent.memory.hand_memory import HandMemory
from poker_agent.memory.manager import MemoryManager
from poker_agent.memory.opponent_memory import OpponentMemory
from poker_agent.memory.session_memory import SessionMemory


# ---------------------------------------------------------------------------
# HandMemory round-trip
# ---------------------------------------------------------------------------


class TestHandMemoryRoundtrip:
    def test_roundtrip_empty(self) -> None:
        original = HandMemory()
        data = original.to_dict()
        restored = HandMemory.from_dict(data)
        assert restored.snapshots == []
        assert restored.decisions == []

    def test_roundtrip_populated(self) -> None:
        original = HandMemory(
            snapshots=["preflop:100:50", "flop:200:100"],
            decisions=["CALL:50:good odds", "RAISE:200:bluff"],
        )
        data = original.to_dict()
        assert isinstance(data, dict)
        restored = HandMemory.from_dict(data)
        assert restored.snapshots == original.snapshots
        assert restored.decisions == original.decisions

    def test_to_dict_keys(self) -> None:
        data = HandMemory().to_dict()
        assert set(data.keys()) == {"snapshots", "decisions"}


# ---------------------------------------------------------------------------
# OpponentMemory round-trip
# ---------------------------------------------------------------------------


class TestOpponentMemoryRoundtrip:
    def test_roundtrip_empty(self) -> None:
        original = OpponentMemory()
        data = original.to_dict()
        restored = OpponentMemory.from_dict(data)
        assert restored.action_counts == {}
        assert restored.fold_counts == {}

    def test_roundtrip_populated(self) -> None:
        original = OpponentMemory(
            action_counts={"seat1:CALL": 3, "seat1:RAISE": 2, "seat2:FOLD": 5},
            fold_counts={"seat1": 1, "seat2": 5},
        )
        data = original.to_dict()
        restored = OpponentMemory.from_dict(data)
        assert restored.action_counts == original.action_counts
        assert restored.fold_counts == original.fold_counts

    def test_to_dict_keys(self) -> None:
        data = OpponentMemory().to_dict()
        assert set(data.keys()) == {"action_counts", "fold_counts"}


# ---------------------------------------------------------------------------
# SessionMemory round-trip
# ---------------------------------------------------------------------------


class TestSessionMemoryRoundtrip:
    def test_roundtrip_empty(self) -> None:
        original = SessionMemory()
        data = original.to_dict()
        restored = SessionMemory.from_dict(data)
        assert restored.hands_played == 0
        assert restored.hands_won == 0
        assert restored.notes == []

    def test_roundtrip_populated(self) -> None:
        original = SessionMemory(hands_played=10, hands_won=4, notes=["note1", "note2"])
        data = original.to_dict()
        restored = SessionMemory.from_dict(data)
        assert restored.hands_played == 10
        assert restored.hands_won == 4
        assert restored.notes == ["note1", "note2"]

    def test_to_dict_keys(self) -> None:
        data = SessionMemory().to_dict()
        assert set(data.keys()) == {"hands_played", "hands_won", "notes"}


# ---------------------------------------------------------------------------
# MemoryManager save / load
# ---------------------------------------------------------------------------


class TestMemoryManagerSaveLoad:
    def test_save_load_roundtrip(self, tmp_path: Path) -> None:
        file_path = str(tmp_path / "memory.json")
        config = MemoryConfig(
            hand_memory=True,
            opponent_memory=True,
            session_memory=True,
            persistence="file",
            file_path=file_path,
        )

        # Build up some state
        mgr = MemoryManager(config=config)
        assert mgr.hand_memory is not None
        mgr.hand_memory.snapshots = ["preflop:100:50"]
        mgr.hand_memory.decisions = ["CALL:50:reason"]
        assert mgr.opponent_memory is not None
        mgr.opponent_memory.action_counts = {"seat1:CALL": 2}
        mgr.opponent_memory.fold_counts = {"seat1": 1}
        assert mgr.session_memory is not None
        mgr.session_memory.hands_played = 5
        mgr.session_memory.hands_won = 2
        mgr.session_memory.notes = ["a note"]

        # Save
        mgr.save()
        assert Path(file_path).exists()

        # Load into a fresh manager
        mgr2 = MemoryManager(config=config)
        mgr2.load()

        assert mgr2.hand_memory is not None
        assert mgr2.hand_memory.snapshots == ["preflop:100:50"]
        assert mgr2.hand_memory.decisions == ["CALL:50:reason"]
        assert mgr2.opponent_memory is not None
        assert mgr2.opponent_memory.action_counts == {"seat1:CALL": 2}
        assert mgr2.opponent_memory.fold_counts == {"seat1": 1}
        assert mgr2.session_memory is not None
        assert mgr2.session_memory.hands_played == 5
        assert mgr2.session_memory.hands_won == 2
        assert mgr2.session_memory.notes == ["a note"]

    def test_save_creates_parent_directories(self, tmp_path: Path) -> None:
        file_path = str(tmp_path / "deep" / "nested" / "memory.json")
        config = MemoryConfig(persistence="file", file_path=file_path)
        mgr = MemoryManager(config=config)
        mgr.save()
        assert Path(file_path).exists()

    def test_load_nonexistent_file_is_noop(self, tmp_path: Path) -> None:
        file_path = str(tmp_path / "nope.json")
        config = MemoryConfig(persistence="file", file_path=file_path)
        mgr = MemoryManager(config=config)
        # Should not raise
        mgr.load()

    def test_file_contains_valid_json(self, tmp_path: Path) -> None:
        file_path = str(tmp_path / "memory.json")
        config = MemoryConfig(persistence="file", file_path=file_path)
        mgr = MemoryManager(config=config)
        mgr.save()
        data = json.loads(Path(file_path).read_text())
        assert "hand_memory" in data
        assert "opponent_memory" in data
        assert "session_memory" in data


# ---------------------------------------------------------------------------
# Persistence disabled (persistence="memory") is a no-op
# ---------------------------------------------------------------------------


class TestMemoryManagerSaveLoadDisabled:
    def test_save_is_noop_when_memory_mode(self, tmp_path: Path) -> None:
        file_path = str(tmp_path / "should_not_exist.json")
        config = MemoryConfig(persistence="memory", file_path=file_path)
        mgr = MemoryManager(config=config)
        mgr.save()
        assert not Path(file_path).exists()

    def test_load_is_noop_when_memory_mode(self, tmp_path: Path) -> None:
        config = MemoryConfig(persistence="memory", file_path=str(tmp_path / "x.json"))
        mgr = MemoryManager(config=config)
        # Should not raise even though file doesn't exist
        mgr.load()
