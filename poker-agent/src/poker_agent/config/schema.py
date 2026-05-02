from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class LLMConfig(BaseModel):
    provider: Literal["openai", "anthropic", "ollama", "custom"] = "openai"
    model: str = "gpt-4o-mini"
    api_key: str | None = None
    base_url: str | None = None
    temperature: float = 0.7
    max_tokens: int = 1000


class ToolConfig(BaseModel):
    enabled: bool = True
    weight: float = 1.0
    params: dict[str, Any] = Field(default_factory=dict)


class MemoryConfig(BaseModel):
    hand_memory: bool = True
    opponent_memory: bool = True
    session_memory: bool = True
    persistence: Literal["memory", "file"] = "memory"
    file_path: str = "./data/memory"


class AgentConfig(BaseModel):
    name: str = "ProPokerAgent"
    mode: Literal["player", "advisor"] = "player"
    llm: LLMConfig = Field(default_factory=LLMConfig)
    tools: dict[str, ToolConfig] = Field(default_factory=dict)
    memory: MemoryConfig = Field(default_factory=MemoryConfig)
    max_retries: int = 2
    timeout_ms: int = 10000

    def is_tool_enabled(self, tool_name: str) -> bool:
        tool = self.tools.get(tool_name)
        return bool(tool and tool.enabled)
