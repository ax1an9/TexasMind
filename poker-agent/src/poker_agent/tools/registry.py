from __future__ import annotations

from typing import Iterable

from ..config.schema import AgentConfig
from .base import PokerTool


class ToolRegistry:
    def __init__(self) -> None:
        self._tools: dict[str, PokerTool] = {}

    def register(self, tool: PokerTool) -> None:
        self._tools[tool.name] = tool

    def get(self, name: str) -> PokerTool | None:
        return self._tools.get(name)

    def values(self) -> Iterable[PokerTool]:
        return self._tools.values()

    def get_enabled_tools(self, config: AgentConfig) -> list[PokerTool]:
        return [tool for name, tool in self._tools.items() if config.is_tool_enabled(name)]

    def to_langchain_tools(self, config: AgentConfig):
        return [tool.to_langchain_tool() for tool in self.get_enabled_tools(config)]
