import asyncio
import base64
import json

import httpx
import pytest

from app.config import Settings
from app.engines import build_engine
from app.engines.base import EngineError, TryOnRequest
from app.engines.openrouter import OpenRouterEngine
from app.prompts import SareeInfo

JPG = base64.b64encode(b"\xff\xd8 fake look").decode()


def run(coro):
    return asyncio.run(coro)


def engine(handler):
    s = Settings(engine="openrouter", openrouter_api_key="or-key")
    return OpenRouterEngine(s, client=httpx.AsyncClient(transport=httpx.MockTransport(handler)))


def req():
    return TryOnRequest(person_jpeg=b"PERSON", saree_jpeg=b"SAREE", saree=SareeInfo(name="Kanjivaram"), drape_style="bengali")


def test_factory_picks_openrouter():
    assert build_engine(Settings(engine="openrouter", openrouter_api_key="k")).name == "openrouter"
    with pytest.raises(RuntimeError, match="OPENROUTER_API_KEY"):
        build_engine(Settings(engine="openrouter", openrouter_api_key=""))


def test_image_request_and_response():
    seen = {}

    def handler(request: httpx.Request):
        seen["url"] = str(request.url)
        seen["auth"] = request.headers["authorization"]
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json={"data": [{"b64_json": JPG, "media_type": "image/jpeg"}]})

    out = run(engine(handler).generate(req()))
    assert out == b"\xff\xd8 fake look"
    assert seen["url"] == "https://openrouter.ai/api/v1/images"
    assert seen["auth"] == "Bearer or-key"
    b = seen["body"]
    assert b["model"] == "google/gemini-nano-banana-2.1"
    assert b["aspect_ratio"] == "3:4" and b["resolution"] == "1K"
    refs = [r["image_url"]["url"] for r in b["input_references"]]
    # saree first, customer second, as the prompt says
    assert refs[0] == "data:image/jpeg;base64," + base64.b64encode(b"SAREE").decode()
    assert refs[1] == "data:image/jpeg;base64," + base64.b64encode(b"PERSON").decode()
    assert "Bengali" in b["prompt"]


def test_no_credits_is_explained_and_not_retried():
    calls = []

    def handler(request):
        calls.append(1)
        return httpx.Response(402, json={"error": {"message": "Insufficient credits"}})

    with pytest.raises(EngineError, match="credits have run out"):
        run(engine(handler).generate(req()))
    assert len(calls) == 1


def test_no_image_is_an_error():
    with pytest.raises(EngineError, match="no image"):
        run(engine(lambda r: httpx.Response(200, json={"data": []})).generate(req()))


def test_analysis_with_fallback_model():
    seen = []

    def handler(request):
        body = json.loads(request.content)
        seen.append(body["model"])
        if body["model"] == "google/gemini-2.5-flash":
            return httpx.Response(404, json={"error": {"message": "No endpoints found"}})
        return httpx.Response(200, json={"choices": [{"message": {"content": '```json\n{"body": {"color": "red"}}\n```'}}]})

    e = engine(handler)
    assert run(e.analyze_saree(b"S")) == {"body": {"color": "red"}}
    assert run(e.analyze_saree(b"S")) == {"body": {"color": "red"}}
    assert seen == ["google/gemini-2.5-flash", "google/gemini-2.5-flash-lite", "google/gemini-2.5-flash-lite"]


def test_startup_check():
    def credits(total, used):
        return lambda r: httpx.Response(200, json={"data": {"total_credits": total, "total_usage": used}})

    ok = run(engine(credits(5, 1.25)).check())
    assert ok["state"] == "ok" and "$3.75" in ok["message"]
    assert run(engine(credits(5, 5)).check())["state"] == "error"
    bad = run(engine(lambda r: httpx.Response(401, json={"error": {"message": "No auth"}})).check())
    assert bad["state"] == "error" and "rejected the key" in bad["message"]


def test_analysis_repairs_or_retries_bad_json():
    calls = []

    def handler(request):
        body = json.loads(request.content)
        calls.append(body)
        assert body["response_format"] == {"type": "json_object"}
        if len(calls) == 1:  # broken: missing comma between keys
            return httpx.Response(200, json={"choices": [{"message": {"content": '{"body": {"color": "pink"} "pallu": {}}'}}]})
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"body": {"color": "pink"}, "pallu": {}}'}}]})

    assert run(engine(handler).analyze_saree(b"S")) == {"body": {"color": "pink"}, "pallu": {}}
    assert len(calls) == 2 and "not valid JSON" in calls[1]["messages"][-1]["content"]

    # trailing commas are fixed without another call
    calls.clear()
    trailing = lambda r: (calls.append(1), httpx.Response(200, json={"choices": [{"message": {"content": '{"a": [1, 2,], "b": 3,}'}}]}))[1]
    assert run(engine(trailing).analyze_saree(b"S")) == {"a": [1, 2], "b": 3}
    assert len(calls) == 1
