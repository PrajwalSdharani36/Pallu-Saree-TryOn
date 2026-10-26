"""OpenRouter engine: the same Google "Nano Banana" image models, billed through OpenRouter.

Useful where Google's own billing can't be set up (for example some Indian cards and UPI).
OpenRouter is prepaid: buy credits once with a card, and each look is taken from that balance.

Images:  POST {base}/images            {model, prompt, input_references, resolution, aspect_ratio}
Reading: POST {base}/chat/completions  (a vision chat model reads the folded saree)
"""
import asyncio
import base64
import json
import logging

import httpx

from ..config import Settings
from ..prompts import ANALYSIS_PROMPT, tryon_prompt
from .base import EngineError, TryOnRequest
from .gemini import parse_json_text

log = logging.getLogger("pallu.openrouter")

RETRY_STATUS = {408, 429, 500, 502, 503, 504}
TEXT_MODEL_FALLBACKS = ("google/gemini-2.5-flash", "google/gemini-2.5-flash-lite", "openai/gpt-4o-mini")


def _data_url(jpeg: bytes) -> str:
    return "data:image/jpeg;base64," + base64.b64encode(jpeg).decode("ascii")


def explain(status: int, text: str) -> str:
    t = text.lower()
    if status == 401:
        return "OpenRouter rejected the key. Check OPENROUTER_API_KEY in backend/.env."
    if status == 402 or "insufficient credits" in t:
        return "OpenRouter credits have run out. Add credits at openrouter.ai/settings/credits."
    if status == 429:
        return "OpenRouter's rate limit was reached. Wait a minute and press Try again."
    if status == 404:
        return "OpenRouter doesn't recognise this model."
    if status == 403:
        return "OpenRouter refused the request (the safety check or the key's limits)."
    if status >= 500:
        return "OpenRouter or the model provider had a temporary problem."
    return "OpenRouter couldn't process this request."


def output_image(data: dict) -> bytes:
    """The first image in an /images response (b64_json, or a data: URL)."""
    for item in data.get("data") or []:
        if not isinstance(item, dict):
            continue
        if isinstance(item.get("b64_json"), str):
            return base64.b64decode(item["b64_json"])
        url = item.get("url")
        if isinstance(url, str) and url.startswith("data:") and "," in url:
            return base64.b64decode(url.split(",", 1)[1])
    raise EngineError(f"The model returned no image: {json.dumps(data)[:300]}", retryable=False)


class OpenRouterEngine:
    name = "openrouter"

    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None):
        if not settings.openrouter_api_key:
            raise RuntimeError("OPENROUTER_API_KEY is not set (or use ENGINE=mock for offline demos)")
        self.s = settings
        self._client = client or httpx.AsyncClient(timeout=settings.request_timeout_s)
        self._text_model: str | None = None
        self.status: dict = {"state": "checking", "message": "Checking the OpenRouter key…"}

    @property
    def _base(self) -> str:
        return self.s.openrouter_base_url.rstrip("/")

    @property
    def _headers(self) -> dict:
        return {
            "Authorization": f"Bearer {self.s.openrouter_api_key}",
            "Content-Type": "application/json",
            "X-Title": "Pallu saree try-on",
        }

    async def _post(self, path: str, body: dict, attempts: int = 3) -> dict:
        delay, last, status = 2.0, "", None
        for attempt in range(1, attempts + 1):
            try:
                r = await self._client.post(self._base + path, json=body, headers=self._headers)
            except httpx.TimeoutException:
                last, status = "OpenRouter request timed out", None
            except httpx.HTTPError as e:
                last, status = f"Network error talking to OpenRouter: {e}", None
            else:
                if r.status_code < 400:
                    data = r.json()
                    err = data.get("error") if isinstance(data, dict) else None
                    if err:  # some providers report errors inside a 200
                        msg = err.get("message", err) if isinstance(err, dict) else err
                        raise EngineError(f"OpenRouter: {msg}"[:500], retryable=True)
                    return data
                status = r.status_code
                last = f"{explain(status, r.text)} (OpenRouter HTTP {status}: {r.text[:400]})"
                if status not in RETRY_STATUS:
                    raise EngineError(last, retryable=False, status=status)
            if attempt < attempts:
                log.warning("%s (attempt %d/%d), retrying in %.0fs", last, attempt, attempts, delay)
                await asyncio.sleep(delay)
                delay *= 2
        raise EngineError(last, retryable=True, status=status)

    async def generate(self, req: TryOnRequest) -> bytes:
        body = {
            "model": self.s.openrouter_image_model,
            "prompt": tryon_prompt(req.saree, req.drape_style, req.mode),
            # same order the prompt describes: IMAGE 1 = the saree, IMAGE 2 = the customer
            "input_references": [
                {"type": "image_url", "image_url": {"url": _data_url(req.saree_jpeg)}},
                {"type": "image_url", "image_url": {"url": _data_url(req.person_jpeg)}},
            ],
            "resolution": self.s.image_size,
            "aspect_ratio": self.s.aspect_ratio,
            "n": 1,
        }
        data = await self._post("/images", body)
        image = output_image(data)
        if self.status.get("state") != "ok":
            self.status = {"state": "ok", "message": f"Connected to OpenRouter. Looks are made with {self.s.openrouter_image_model}."}
        return image

    def text_models(self) -> list[str]:
        if self._text_model:
            return [self._text_model]
        first = self.s.openrouter_text_model
        return [first] + [m for m in TEXT_MODEL_FALLBACKS if m != first]

    async def analyze_saree(self, saree_jpeg: bytes) -> dict:
        first_error: EngineError | None = None
        for model in self.text_models():
            body = {
                "model": model,
                "temperature": 0.2,
                "messages": [{
                    "role": "user",
                    "content": [
                        {"type": "text", "text": ANALYSIS_PROMPT},
                        {"type": "image_url", "image_url": {"url": _data_url(saree_jpeg)}},
                    ],
                }],
            }
            try:
                data = await self._post("/chat/completions", body)
            except EngineError as e:
                if self._text_model is None and e.status in (400, 404):
                    log.warning("Text model %s not available; trying the next one", model)
                    first_error = first_error or e
                    continue
                raise
            if self._text_model is None:
                self._text_model = model
                log.info("Saree reading: using %s", model)
            try:
                text = data["choices"][0]["message"]["content"]
                if isinstance(text, list):  # content parts
                    text = "".join(p.get("text", "") for p in text if isinstance(p, dict))
                return parse_json_text(text or "")
            except (KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError) as e:
                raise EngineError(f"Could not parse saree analysis: {e}") from e
        raise first_error or EngineError("No OpenRouter text model was available")

    async def check(self) -> dict:
        """Free start-up check: is the key accepted, and how much credit is left?"""
        try:
            r = await self._client.get(self._base + "/credits", headers=self._headers, timeout=20)
        except httpx.HTTPError as e:
            self.status = {"state": "error", "message": f"Can't reach OpenRouter from this computer ({type(e).__name__}). Check the internet."}
        else:
            if r.status_code == 200:
                try:
                    d = r.json().get("data", {})
                    left = float(d.get("total_credits", 0)) - float(d.get("total_usage", 0))
                except (ValueError, TypeError, AttributeError):
                    left = None
                if left is not None and left <= 0.01:
                    self.status = {"state": "error", "message": "OpenRouter accepted the key, but there are no credits. Add credits at openrouter.ai/settings/credits."}
                else:
                    money = f" Credit left: about ${left:.2f}." if left is not None else ""
                    self.status = {"state": "ok", "message": f"OpenRouter accepted the key. Looks will be made with {self.s.openrouter_image_model}.{money}"}
            else:
                self.status = {"state": "error", "message": explain(r.status_code, r.text) + f" (HTTP {r.status_code})"}
        level = logging.INFO if self.status["state"] == "ok" else logging.WARNING
        log.log(level, "OpenRouter check: %s", self.status["message"])
        return self.status

    async def aclose(self) -> None:
        await self._client.aclose()
