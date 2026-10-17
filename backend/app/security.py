import secrets

from fastapi import Header, HTTPException, Query, status

from .config import get_settings


def require_key(
    x_api_key: str | None = Header(default=None),
    key: str | None = Query(default=None, description="Alternative to the X-API-Key header (for <img> tags)"),
) -> None:
    supplied = x_api_key or key or ""
    if not secrets.compare_digest(supplied.encode(), get_settings().api_key.encode()):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or missing API key")
