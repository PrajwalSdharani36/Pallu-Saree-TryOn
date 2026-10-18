from dataclasses import dataclass
from typing import Protocol

from ..prompts import SareeInfo


class EngineError(RuntimeError):
    """Raised when the engine could not produce an image. `retryable` hints the worker."""

    def __init__(self, message: str, retryable: bool = False, status: int | None = None):
        super().__init__(message)
        self.retryable = retryable
        self.status = status  # HTTP status from the provider, when there was one


@dataclass
class TryOnRequest:
    person_jpeg: bytes
    saree_jpeg: bytes
    saree: SareeInfo
    drape_style: str = "nivi"
    mode: str = "self"


class TryOnEngine(Protocol):
    name: str

    async def generate(self, req: TryOnRequest) -> bytes:
        """Return the try-on image (any common image format)."""
        ...

    async def analyze_saree(self, saree_jpeg: bytes) -> dict:
        """Return a component description of a folded saree (see prompts.ANALYSIS_PROMPT)."""
        ...
