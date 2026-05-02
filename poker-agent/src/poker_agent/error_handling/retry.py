from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable


async def retry_async(
    factory: Callable[[], Awaitable[object]],
    attempts: int = 2,
    base_delay: float = 0.25,
) -> object:
    last_error: Exception | None = None
    for attempt in range(attempts + 1):
        try:
            return await factory()
        except Exception as exc:  # pragma: no cover - exercised in integration failures
            last_error = exc
            if attempt >= attempts:
                break
            await asyncio.sleep(base_delay * (2**attempt))
    assert last_error is not None
    raise last_error
