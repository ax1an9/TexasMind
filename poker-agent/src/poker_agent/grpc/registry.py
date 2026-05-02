from __future__ import annotations

import logging

from ..agent.react_agent import PokerReactAgent
from ..config.schema import AgentConfig
from ..models import ActionResponse, DecisionRequest

log = logging.getLogger(__name__)


class AgentRegistry:
    """Manages per-game PokerReactAgent instances.

    An agent is created on the first ``get_or_create`` call for a given
    ``game_id`` and reused on subsequent calls.  ``remove`` saves the
    agent's memory before dropping it so state can be recovered if file
    persistence is enabled.
    """

    def __init__(self, config: AgentConfig) -> None:
        self._config = config
        self._agents: dict[str, PokerReactAgent] = {}

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def get_or_create(self, game_id: str) -> PokerReactAgent:
        """Return the agent for *game_id*, creating one if it does not exist."""
        agent = self._agents.get(game_id)
        if agent is None:
            agent = PokerReactAgent(self._config)
            agent.memory.load()
            self._agents[game_id] = agent
            log.info("[REGISTRY] Created agent for game=%s (active=%d)", game_id, self.active_count())
        return agent

    def remove(self, game_id: str) -> None:
        """Save the agent's memory and remove it from the registry."""
        agent = self._agents.pop(game_id, None)
        if agent is not None:
            agent.memory.save()
            log.info("[REGISTRY] Removed agent for game=%s (active=%d)", game_id, self.active_count())

    def active_count(self) -> int:
        """Return the number of live game agents."""
        return len(self._agents)

    async def decide(self, request: DecisionRequest) -> ActionResponse:
        """Delegate a decision to the per-game agent for ``request.game_id``."""
        agent = self.get_or_create(request.game_id)
        return await agent.decide(request)
