# Poker Agent

LangChain ReAct 风格的德州扑克 AI Agent 骨架，按 `docs/superpowers/specs/2026-05-02-poker-react-agent-design.md` 搭建。

## Quick Start

```bash
uv sync --dev
uv run poker-agent --config config/default.yaml
```

## Generate Proto

```bash
uv run python scripts/generate_proto.py
```

## Layout

- `src/poker_agent/agent`: ReAct agent, prompt builder, output parser
- `src/poker_agent/tools`: pluggable poker tools
- `src/poker_agent/memory`: layered memory manager
- `src/poker_agent/grpc`: client and proto adapter helpers
- `src/poker_agent/config`: config models and loader
