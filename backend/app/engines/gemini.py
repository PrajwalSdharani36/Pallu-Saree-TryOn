"""Google Gemini ("Nano Banana") image engine.

Two request styles are supported because Google offers both:
  * interactions       POST /v1beta/interactions                (current docs)
  * generate_content   POST /v1beta/models/{model}:generateContent (classic)

GEMINI_API_STYLE picks the first one tried. Until a request has worked, a 400/404 from Google
makes the engine try the other style, then the fallback model, and it remembers whichever works,
so a renamed model or endpoint doesn't stop the store.

Only images the model produced are used: the customer's own photo can be echoed back in the
response (user_input steps) and must never be shown as a "look".
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
STYLES = ("interactions", "generate_content")
# Tried in order if the configured model isn't found (each costs about twice Nano Banana 2.1 at 1K).
IMAGE_MODEL_FALLBACKS = ("gemini-3.1-flash-image",)
# Tried in order if the configured text model isn't found. Saree reading is free on these.
TEXT_MODEL_FALLBACKS = ("gemini-3.8-flash", "gemini-3.6-flash", "gemini-2.5-flash")

# Parts of a response that are not the model's final output.
_NOT_OUTPUT_TYPES = {"user_input", "thought", "function_call", "function_result"}


def _b64(data: bytes) -> str:
    return base64.b64encode(data).decode("ascii")


def _is_not_output(o: dict) -> bool:
    return o.get("type") in _NOT_OUTPUT_TYPES or o.get("thought") is True or o.get("role") == "user"


def find_images(obj: Any) -> list[tuple[str, bytes]]:
    """Every (mime, bytes) image the MODEL produced, in document order (skips echoed input and thoughts)."""
    found: list[tuple[str, bytes]] = []

    def walk(o: Any) -> None:
        if isinstance(o, dict):
            if _is_not_output(o):
                return
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
            for k, v in o.items():
                if k in ("input", "system_instruction"):  # never our own request echoed back
                    continue
                if isinstance(v, (dict, list)):
                    walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)

    walk(obj)
    # the same block can be reachable twice (e.g. output_image shortcut + steps): keep order, drop repeats
    seen: set[bytes] = set()
    unique = []
    for mime, data in found:
        if data not in seen:
            seen.add(data)
            unique.append((mime, data))
    return unique


def find_text(obj: Any) -> str:
    texts: list[str] = []

    def walk(o: Any) -> None:
        if isinstance(o, dict):
            if _is_not_output(o):
                return
            if isinstance(o.get("text"), str):
                texts.append(o["text"])
            for k, v in o.items():
                if k in ("inlineData", "inline_data", "data", "input", "system_instruction"):
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


def explain(status: int, text: str) -> str:
    """Plain-English reason for a Gemini HTTP error, for the server log and the staff app."""
    t = text.lower()
    if "api key not valid" in t or "api_key_invalid" in t or (status == 401):
        return "Google rejected the API key. Check GEMINI_API_KEY in backend/.env (no spaces or quotes)."
    if status == 429 and ("limit: 0" in t or "free_tier" in t or "free tier" in t):
        return "Billing isn't switched on for this key's Google project (image models have no free tier)."
    if status == 429:
        return "Google's rate limit was reached. Wait a minute and press Try again."
    if status == 403:
        return "Google refused the request. Turn on billing and the Gemini API for the key's project."
    if status == 404:
        return "Google doesn't recognise this model or endpoint."
    if status >= 500:
        return "Google's service had a temporary problem."
    return "Google couldn't process this request."


class GeminiEngine:
    name = "gemini"

    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None):
        if not settings.gemini_api_key:
            raise RuntimeError("GEMINI_API_KEY is not set (or use ENGINE=mock for offline demos)")
        self.s = settings
        self._client = client or httpx.AsyncClient(timeout=settings.request_timeout_s)
        self._image_route: tuple[str, str] | None = None  # (style, model) that has worked
        self._text_model: str | None = None
        self.status: dict = {"state": "checking", "message": "Checking the Google key…"}

    # ---------- request bodies ----------
    def image_request(self, prompt: str, images: list[bytes], style: str | None = None,
                      model: str | None = None) -> tuple[str, dict]:
        s = self.s
        style = style or s.gemini_api_style
        model = model or s.gemini_image_model
        base = s.gemini_base_url.rstrip("/")
        if style == "interactions":
            body = {
                "model": model,
                "input": [{"type": "text", "text": prompt}]
                + [{"type": "image", "mime_type": "image/jpeg", "data": _b64(i)} for i in images],
                "response_format": {
                    "type": "image",
                    "mime_type": "image/jpeg",
                    "aspect_ratio": s.aspect_ratio,
                    "image_size": s.image_size,
                },
                "store": s.gemini_store,
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
        return f"{base}/v1beta/models/{model}:generateContent", body

    def text_request(self, prompt: str, image: bytes, model: str | None = None) -> tuple[str, dict]:
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
        return f"{base}/v1beta/models/{model or self.s.gemini_text_model}:generateContent", body

    def image_routes(self) -> list[tuple[str, str]]:
        if self._image_route:
            return [self._image_route]
        first = self.s.gemini_api_style
        other = STYLES[1] if first == STYLES[0] else STYLES[0]
        models = [self.s.gemini_image_model] + [m for m in IMAGE_MODEL_FALLBACKS if m != self.s.gemini_image_model]
        return [(style, m) for m in models for style in (first, other)]

    def text_models(self) -> list[str]:
        if self._text_model:
            return [self._text_model]
        return [self.s.gemini_text_model] + [m for m in TEXT_MODEL_FALLBACKS if m != self.s.gemini_text_model]

    # ---------- transport ----------
    @property
    def _headers(self) -> dict:
        return {"x-goog-api-key": self.s.gemini_api_key, "Content-Type": "application/json"}

    async def _post(self, url: str, body: dict, attempts: int = 3) -> dict:
        delay = 2.0
        last = ""
        status: int | None = None
        for attempt in range(1, attempts + 1):
            try:
                r = await self._client.post(url, json=body, headers=self._headers)
            except httpx.TimeoutException:
                last, status = "Gemini request timed out", None
            except httpx.HTTPError as e:
                last, status = f"Network error talking to Gemini: {e}", None
            else:
                if r.status_code < 400:
                    return r.json()
                status = r.status_code
                last = f"{explain(status, r.text)} (Gemini HTTP {status}: {r.text[:400]})"
                billing_off = status == 429 and "limit: 0" in r.text.lower()
                if status not in RETRY_STATUS or billing_off:
                    raise EngineError(last, retryable=False, status=status)
            if attempt < attempts:
                log.warning("%s (attempt %d/%d), retrying in %.0fs", last, attempt, attempts, delay)
                await asyncio.sleep(delay)
                delay *= 2
        raise EngineError(last, retryable=True, status=status)

    # ---------- public API ----------
    async def generate(self, req: TryOnRequest) -> bytes:
        prompt = tryon_prompt(req.saree, req.drape_style, req.mode)
        first_error: EngineError | None = None
        for style, model in self.image_routes():
            url, body = self.image_request(prompt, [req.saree_jpeg, req.person_jpeg], style, model)
            try:
                data = await self._post(url, body)
            except EngineError as e:
                # Until something has worked, a "bad request"/"not found" may just mean a renamed
                # model or endpoint: try the next way. After that, errors are real.
                if self._image_route is None and e.status in (400, 404) and "api key" not in str(e).lower():
                    log.warning("Gemini %s with %s refused (%s); trying another way", style, model, e)
                    first_error = first_error or e
                    continue
                raise
            if self._image_route is None:
                self._image_route = (style, model)
                log.info("Gemini looks: using the %s API with %s", style, model)
                self.status = {"state": "ok", "message": f"Connected to Google. Looks are made with {model}."}
            return self._output_image(data)
        raise first_error or EngineError("No way to reach the Gemini image model worked")

    def _output_image(self, data: dict) -> bytes:
        state = data.get("status") if isinstance(data, dict) else None
        if state in ("failed", "cancelled", "incomplete"):
            errs = "; ".join(str(e.get("message", e)) for e in data.get("errors") or [] if isinstance(e, dict))
            raise EngineError(f"Gemini {state}: {errs or find_text(data) or 'no reason given'}"[:500], retryable=True)
        images = find_images(data)
        if not images:
            reason = find_text(data) or json.dumps(data)[:300]
            raise EngineError(f"The model returned no image: {reason[:300]}", retryable=False)
        return images[-1][1]

    async def analyze_saree(self, saree_jpeg: bytes) -> dict:
        first_error: EngineError | None = None
        for model in self.text_models():
            url, body = self.text_request(ANALYSIS_PROMPT, saree_jpeg, model)
            try:
                data = await self._post(url, body)
            except EngineError as e:
                if self._text_model is None and e.status == 404:
                    log.warning("Text model %s not found; trying the next one", model)
                    first_error = first_error or e
                    continue
                raise
            if self._text_model is None:
                self._text_model = model
                log.info("Saree reading: using %s", model)
            text = find_text(data)
            try:
                return parse_json_text(text)
            except (ValueError, json.JSONDecodeError) as e:
                raise EngineError(f"Could not parse saree analysis: {e}; got {text[:200]!r}") from e
        raise first_error or EngineError("No Gemini text model was found")

    async def check(self) -> dict:
        """Free start-up check: is the key accepted and does the image model exist? (No image is made.)"""
        base = self.s.gemini_base_url.rstrip("/")
        model = self.s.gemini_image_model
        try:
            r = await self._client.get(f"{base}/v1beta/models/{model}", headers=self._headers, timeout=20)
        except httpx.HTTPError as e:
            self.status = {"state": "error", "message": f"Can't reach Google from this computer ({type(e).__name__}). Check the internet."}
        else:
            if r.status_code == 200:
                self.status = {
                    "state": "ok",
                    "message": f"Google accepted the key. Looks will be made with {model}. "
                               "Billing is checked when the first look is made.",
                }
            elif r.status_code == 404:
                self.status = {
                    "state": "warning",
                    "message": f"Key accepted, but Google doesn't list {model}. The server will try "
                               f"{', '.join(IMAGE_MODEL_FALLBACKS)} instead.",
                }
            else:
                self.status = {"state": "error", "message": explain(r.status_code, r.text) + f" (HTTP {r.status_code})"}
        level = logging.INFO if self.status["state"] == "ok" else logging.WARNING
        log.log(level, "Gemini check: %s", self.status["message"])
        return self.status

    async def aclose(self) -> None:
        await self._client.aclose()
