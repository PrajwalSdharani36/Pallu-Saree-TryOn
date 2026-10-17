"""Google Gemini ("Nano Banana") image engine.

Two request styles are supported because Google offers both:
  * interactions       POST /v1beta/interactions                (current docs)
  * generate_content   POST /v1beta/models/{model}:generateContent (classic)
Switch with GEMINI_API_STYLE. Response parsing is shape-agnostic: it walks the
JSON and takes the last image block it finds, so either style works.
"""
import asyncio
import base64
import json
import logging
import re
from typing import Any

import httpx

from ..config import Settings
from ..prompts import ANALYSIS_PROMPT, tryon_prompt
from .base import EngineError, TryOnRequest

log = logging.getLogger("pallu.gemini")

RETRY_STATUS = {408, 429, 500, 502, 503, 504}


def _b64(data: bytes) -> str:
    return base64.b64encode(data).decode("ascii")


def find_images(obj: Any) -> list[tuple[str, bytes]]:
    """Collect every (mime, bytes) image in a Gemini response, in document order."""
    found: list[tuple[str, bytes]] = []

    def walk(o: Any) -> None:
        if isinstance(o, dict):
            # classic: {"inlineData": {"mimeType": "image/png", "data": "..."}}
            for k in ("inlineData", "inline_data"):
                blob = o.get(k)
                if isinstance(blob, dict) and isinstance(blob.get("data"), str):
                    mime = blob.get("mimeType") or blob.get("mime_type") or ""
                    if mime.startswith("image/") or not mime:
                        found.append((mime or "image/png", base64.b64decode(blob["data"])))
            # interactions: {"type": "image", "mime_type": "image/png", "data": "..."}
            if o.get("type") == "image" and isinstance(o.get("data"), str):
                found.append((o.get("mime_type") or o.get("mimeType") or "image/png", base64.b64decode(o["data"])))
            for v in o.values():
                if isinstance(v, (dict, list)):
                    walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)

    walk(obj)
    return found


def find_text(obj: Any) -> str:
    texts: list[str] = []

    def walk(o: Any) -> None:
        if isinstance(o, dict):
            if isinstance(o.get("text"), str):
                texts.append(o["text"])
            for k, v in o.items():
                if k in ("inlineData", "inline_data", "data"):
                    continue
                if isinstance(v, (dict, list)):
                    walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)

    walk(obj)
    return "\n".join(texts).strip()


def parse_json_text(text: str) -> dict:
    text = text.strip()
    m = re.search(r"```(?:json)?\s*(.*?)```", text, re.S)
    if m:
        text = m.group(1)
    start, end = text.find("{"), text.rfind("}")
    if start == -1 or end == -1:
        raise ValueError("No JSON object in model output")
    return json.loads(text[start : end + 1])


class GeminiEngine:
    name = "gemini"

    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None):
        if not settings.gemini_api_key:
            raise RuntimeError("GEMINI_API_KEY is not set (or use ENGINE=mock for offline demos)")
        self.s = settings
        self._client = client or httpx.AsyncClient(timeout=settings.request_timeout_s)

    # ---------- request bodies ----------
    def image_request(self, prompt: str, images: list[bytes]) -> tuple[str, dict]:
        s = self.s
        base = s.gemini_base_url.rstrip("/")
        if s.gemini_api_style == "interactions":
            body = {
                "model": s.gemini_image_model,
                "input": [{"type": "text", "text": prompt}]
                + [{"type": "image", "mime_type": "image/jpeg", "data": _b64(i)} for i in images],
                "response_format": {"type": "image", "aspect_ratio": s.aspect_ratio, "image_size": s.image_size},
            }
            return f"{base}/v1beta/interactions", body
        body = {
            "contents": [
                {
                    "role": "user",
                    "parts": [{"text": prompt}]
                    + [{"inline_data": {"mime_type": "image/jpeg", "data": _b64(i)}} for i in images],
                }
            ],
            "generationConfig": {
                "responseModalities": ["TEXT", "IMAGE"],
                "imageConfig": {"aspectRatio": s.aspect_ratio, "imageSize": s.image_size},
            },
        }
        return f"{base}/v1beta/models/{s.gemini_image_model}:generateContent", body

    def text_request(self, prompt: str, image: bytes) -> tuple[str, dict]:
        base = self.s.gemini_base_url.rstrip("/")
        body = {
            "contents": [
                {
                    "role": "user",
                    "parts": [{"text": prompt}, {"inline_data": {"mime_type": "image/jpeg", "data": _b64(image)}}],
                }
            ],
            "generationConfig": {"responseMimeType": "application/json", "temperature": 0.2},
        }
        return f"{base}/v1beta/models/{self.s.gemini_text_model}:generateContent", body

    # ---------- transport ----------
    async def _post(self, url: str, body: dict, attempts: int = 3) -> dict:
        headers = {"x-goog-api-key": self.s.gemini_api_key, "Content-Type": "application/json"}
        delay = 2.0
        last = ""
        for attempt in range(1, attempts + 1):
            try:
                r = await self._client.post(url, json=body, headers=headers)
            except httpx.TimeoutException:
                last = "Gemini request timed out"
            except httpx.HTTPError as e:
                last = f"Network error talking to Gemini: {e}"
            else:
                if r.status_code < 400:
                    return r.json()
                last = f"Gemini HTTP {r.status_code}: {r.text[:400]}"
                if r.status_code not in RETRY_STATUS:
                    raise EngineError(last, retryable=False)
            if attempt < attempts:
                log.warning("%s (attempt %d/%d), retrying in %.0fs", last, attempt, attempts, delay)
                await asyncio.sleep(delay)
                delay *= 2
        raise EngineError(last, retryable=True)

    # ---------- public API ----------
    async def generate(self, req: TryOnRequest) -> bytes:
        prompt = tryon_prompt(req.saree, req.drape_style, req.mode)
        url, body = self.image_request(prompt, [req.saree_jpeg, req.person_jpeg])
        data = await self._post(url, body)
        images = find_images(data)
        if not images:
            reason = find_text(data) or json.dumps(data)[:300]
            raise EngineError(f"Model returned no image: {reason[:300]}", retryable=False)
        return images[-1][1]

    async def analyze_saree(self, saree_jpeg: bytes) -> dict:
        url, body = self.text_request(ANALYSIS_PROMPT, saree_jpeg)
        data = await self._post(url, body)
        text = find_text(data)
        try:
            return parse_json_text(text)
        except (ValueError, json.JSONDecodeError) as e:
            raise EngineError(f"Could not parse saree analysis: {e}; got {text[:200]!r}") from e

    async def aclose(self) -> None:
        await self._client.aclose()
