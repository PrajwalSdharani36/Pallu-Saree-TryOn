"""Runtime configuration, read from environment variables or a .env file."""
from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # --- Store ---
    store_name: str = "Pallu Saree Studio"
    # Shared key used by the store's Android devices and the admin page.
    api_key: str = "change-me"

    # --- Storage ---
    data_dir: Path = Path("data")
    database_url: str = ""  # defaults to sqlite in data_dir

    # --- Try-on engine ---
    engine: Literal["gemini", "mock"] = "mock"
    gemini_api_key: str = ""
    gemini_base_url: str = "https://generativelanguage.googleapis.com"
    # "interactions" = current Gemini Interactions API, "generate_content" = classic models/*:generateContent
    gemini_api_style: Literal["interactions", "generate_content"] = "interactions"
    gemini_image_model: str = "gemini-nano-banana-2.1"
    # Text+vision model used to label saree components (body / border / pallu ...)
    gemini_text_model: str = "gemini-flash-latest"
    # Ask Google not to keep the request (customer photos) for later retrieval.
    gemini_store: bool = False
    image_size: str = "1K"  # 1K | 2K | 4K
    aspect_ratio: str = "3:4"  # portrait, full length
    request_timeout_s: float = 180.0
    max_concurrent_generations: int = 3
    mock_delay_s: float = 1.5  # simulated generation time for ENGINE=mock

    # --- Sessions & privacy ---
    session_ttl_minutes: int = 120  # how long a QR session stays usable
    photo_ttl_minutes: int = 30  # customer photo + generated images auto-deleted after this
    max_sarees_per_session: int = 12
    max_upload_mb: int = 12
    purge_interval_s: int = 60

    @property
    def db_url(self) -> str:
        if self.database_url:
            return self.database_url
        return f"sqlite:///{(self.data_dir / 'pallu.db').as_posix()}"


@lru_cache
def get_settings() -> Settings:
    s = Settings()
    s.data_dir.mkdir(parents=True, exist_ok=True)
    return s
