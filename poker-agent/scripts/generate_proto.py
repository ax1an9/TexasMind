from __future__ import annotations

from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
PROTO = ROOT / "proto" / "poker_agent.proto"
OUT = ROOT / "src" / "poker_agent" / "grpc" / "generated"


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    try:
        from grpc_tools import protoc  # type: ignore
    except ImportError:
        command = [
            sys.executable,
            "-m",
            "grpc_tools.protoc",
            f"-I{PROTO.parent}",
            f"--python_out={OUT}",
            f"--grpc_python_out={OUT}",
            str(PROTO),
        ]
        return subprocess.call(command)

    args = [
        "grpc_tools.protoc",
        f"-I{PROTO.parent}",
        f"--python_out={OUT}",
        f"--grpc_python_out={OUT}",
        str(PROTO),
    ]
    result = protoc.main(args)
    if result == 0:
        _rewrite_generated_imports()
    return result


def _rewrite_generated_imports() -> None:
    grpc_file = OUT / "poker_agent_pb2_grpc.py"
    if not grpc_file.exists():
        return

    content = grpc_file.read_text(encoding="utf-8")
    content = content.replace(
        "import poker_agent_pb2 as poker__agent__pb2",
        "from . import poker_agent_pb2 as poker__agent__pb2",
    )
    grpc_file.write_text(content, encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
