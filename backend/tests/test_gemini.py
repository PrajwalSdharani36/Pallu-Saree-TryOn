import asyncio
import base64
import json

import httpx
import pytest

from app.config import Settings
from app.engines.base import EngineError, TryOnRequest
from app.engines.gemini import GeminiEngine, find_images, parse_json_text
from app.prompts import SareeInfo, tryon_prompt

PNG = base64.b64encode(b"\x89PNG fake").decode()


def run(coro):
    return asyncio.run(coro)


def engine(handler, style="interactions"):
    s = Settings(gemini_api_key="k", gemini_api_style=style, engine="gemini")
    return GeminiEngine(s, client=httpx.AsyncClient(transport=httpx.MockTransport(handler)))


def req():
    return TryOnRequest(person_jpeg=b"P", saree_jpeg=b"S", saree=SareeInfo(name="Kanjivaram", fabric="Silk"))


def test_find_images_both_shapes():
    classic = {"candidates": [{"content": {"parts": [{"text": "hi"}, {"inlineData": {"mimeType": "image/png", "data": PNG}}]}}]}
    inter = {"steps": [{"type": "model_output", "content": [{"type": "image", "mime_type": "image/png", "data": PNG}]}]}
    assert find_images(classic)[0][1].startswith(b"\x89PNG")
    assert find_images(inter)[0][1].startswith(b"\x89PNG")


def test_interactions_request_shape():
    seen = {}

    def handler(request: httpx.Request):
        seen["url"] = str(request.url)
        seen["key"] = request.headers["x-goog-api-key"]
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json={"output_image": {"type": "image", "mime_type": "image/png", "data": PNG}})

    out = run(engine(handler).generate(req()))
    assert out.startswith(b"\x89PNG")
    assert seen["url"].endswith("/v1beta/interactions") and seen["key"] == "k"
    body = seen["body"]
    assert body["model"] == "gemini-nano-banana-2.1"
    assert [p["type"] for p in body["input"]] == ["text", "image", "image"]
    assert body["response_format"]["aspect_ratio"] == "3:4"


def test_generate_content_request_shape():
    seen = {}

    def handler(request: httpx.Request):
        seen["url"] = str(request.url)
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"inlineData": {"mimeType": "image/png", "data": PNG}}]}}]})

    run(engine(handler, "generate_content").generate(req()))
    assert seen["url"].endswith(":generateContent")
    assert seen["body"]["generationConfig"]["responseModalities"] == ["TEXT", "IMAGE"]


def test_no_image_is_error():
    def handler(request):
        return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": "I can't help with that"}]}}]})

    with pytest.raises(EngineError, match="can't help"):
        run(engine(handler).generate(req()))


def test_client_error_not_retried():
    calls = []

    def handler(request):
        calls.append(1)
        return httpx.Response(400, json={"error": {"message": "bad model"}})

    with pytest.raises(EngineError):
        run(engine(handler).generate(req()))
    assert len(calls) == 1


def test_analysis_parses_json():
    payload = {"body": {"color": "maroon"}, "pallu": {"pattern": "peacock"}}

    def handler(request):
        text = "```json\n" + json.dumps(payload) + "\n```"
        return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": text}]}}]})

    assert run(engine(handler).analyze_saree(b"S")) == payload
    assert parse_json_text('noise {"a": 1} noise') == {"a": 1}


def test_prompt_uses_analysis_and_drape():
    p = tryon_prompt(
        SareeInfo(name="Paithani", fabric="Silk", analysis={"pallu": {"color": "green", "pattern": "peacock"},
                                                             "blouse_piece": {"visible": True, "color": "purple"}}),
        "gujarati", "gift",
    )
    assert "Seedha Pallu" in p and "peacock" in p and "purple" in p and "Extend it naturally" in p
