from __future__ import annotations

from abc import ABC, abstractmethod
from importlib import import_module


class PokerTool(ABC):
    @property
    @abstractmethod
    def name(self) -> str:
        raise NotImplementedError

    @property
    @abstractmethod
    def description(self) -> str:
        raise NotImplementedError

    @abstractmethod
    def run(self, **kwargs: object) -> str:
        raise NotImplementedError

    def to_langchain_tool(self):
        try:
            module = import_module("langchain_core.tools")
            structured_tool = getattr(module, "StructuredTool")
        except ImportError as exc:  # pragma: no cover - optional runtime dependency
            raise RuntimeError("langchain-core is required to build LangChain tools") from exc

        return structured_tool.from_function(
            name=self.name,
            description=self.description,
            func=self.run,
        )
