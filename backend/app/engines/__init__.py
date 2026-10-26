from ..config import Settings
from .base import EngineError, TryOnEngine, TryOnRequest
from .gemini import GeminiEngine
from .mock import MockEngine
from .openrouter import OpenRouterEngine

__all__ = ["EngineError", "TryOnEngine", "TryOnRequest", "GeminiEngine", "MockEngine", "OpenRouterEngine", "build_engine"]


def build_engine(settings: Settings) -> TryOnEngine:
    if settings.engine == "gemini":
        return GeminiEngine(settings)
    if settings.engine == "openrouter":
        return OpenRouterEngine(settings)
    return MockEngine(delay_s=settings.mock_delay_s)
