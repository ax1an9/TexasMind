from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path

from .agent.react_agent import PokerReactAgent
from .config.loader import load_config
from .models import DecisionRequest


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run the poker ReAct agent")
    parser.add_argument("--config", default="config/default.yaml", help="Path to the YAML config file")
    parser.add_argument("--input", help="Path to a JSON decision request")
    parser.add_argument("--mode", choices=["player", "advisor"], help="Override the configured mode")
    parser.add_argument("--print-config", action="store_true", help="Print the resolved configuration and exit")
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    config = load_config(Path(args.config))
    if args.mode:
        config = config.model_copy(update={"mode": args.mode})

    if args.print_config:
        print(config.model_dump_json(indent=2))
        return 0

    agent = PokerReactAgent(config)

    if not args.input:
        print(f"{config.name} ready in {config.mode} mode")
        return 0

    request = DecisionRequest.model_validate_json(Path(args.input).read_text(encoding="utf-8"))
    result = asyncio.run(agent.decide(request) if config.mode == "player" else agent.advise(request))
    print(json.dumps(result.model_dump(mode="json"), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
