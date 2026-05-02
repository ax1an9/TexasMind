from __future__ import annotations

import os
from pathlib import Path

import yaml

from .schema import AgentConfig


def _expand_env_values(value: object) -> object:
    if isinstance(value, str):
        return os.path.expandvars(value)
    if isinstance(value, list):
        return [_expand_env_values(item) for item in value]
    if isinstance(value, dict):
        return {key: _expand_env_values(item) for key, item in value.items()}
    return value


def load_config(path: str | Path) -> AgentConfig:
    config_path = Path(path)
    raw = yaml.safe_load(os.path.expandvars(config_path.read_text(encoding="utf-8")))
    if raw is None:
        raw = {}
    return AgentConfig.model_validate(_expand_env_values(raw))
