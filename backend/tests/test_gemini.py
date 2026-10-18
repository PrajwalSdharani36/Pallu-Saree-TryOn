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
    assert body["response_format"]["mime_type"] == "image/jpeg"
    assert body["store"] is False  # customer photos are not kept by Google for later retrieval


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


def test_bad_request_tries_other_ways_then_reports_first_error():
    calls = []

    def handler(request):
        calls.append(str(request.url))
        return httpx.Response(400, json={"error": {"message": "bad request"}})

    with pytest.raises(EngineError, match="bad request") as e:
        run(engine(handler).generate(req()))
    assert e.value.status == 400
    assert len(calls) == 4  # both API styles x (model, fallback model); 400s are never retried
    assert calls[0].endswith("/v1beta/interactions") and calls[1].endswith("gemini-nano-banana-2.1:generateContent")
    assert calls[3].endswith("gemini-3.1-flash-image:generateContent")


def test_falls_back_to_generate_content_and_remembers():
    calls = []

    def handler(request):
        calls.append(str(request.url))
        if request.url.path.endswith("/interactions"):
            return httpx.Response(404, json={"error": {"message": "Not found"}})
        return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"inlineData": {"mimeType": "image/png", "data": PNG}}]}}]})

    e = engine(handler)
    assert run(e.generate(req())).startswith(b"\x89PNG")
    assert run(e.generate(req())).startswith(b"\x89PNG")
    assert len(calls) == 3  # second look goes straight to the way that worked
    assert e.status["state"] == "ok"


def test_errors_after_a_working_route_are_not_fallen_back():
    calls = []

    def handler(request):
        calls.append(1)
        if len(calls) == 1:
            return httpx.Response(200, json={"steps": [{"type": "model_output", "content": [{"type": "image", "data": PNG}]}]})
        return httpx.Response(400, json={"error": {"message": "image could not be processed"}})

    e = engine(handler)
    run(e.generate(req()))
    with pytest.raises(EngineError):
        run(e.generate(req()))
    assert len(calls) == 2


def test_invalid_key_is_explained_and_not_fallen_back():
    calls = []

    def handler(request):
        calls.append(1)
        return httpx.Response(400, json={"error": {"message": "API key not valid. Please pass a valid API key.", "status": "INVALID_ARGUMENT"}})

    with pytest.raises(EngineError, match="rejected the API key"):
        run(engine(handler).generate(req()))
    assert len(calls) == 1


def test_billing_off_is_explained_and_not_retried():
    calls = []

    def handler(request):
        calls.append(1)
        return httpx.Response(429, json={"error": {"message": "Quota exceeded for metric: generate_content_free_tier_requests, limit: 0"}})

    with pytest.raises(EngineError, match="Billing isn't switched on"):
        run(engine(handler).generate(req()))
    assert len(calls) == 1


def test_echoed_customer_photo_is_never_used_as_the_look():
    me = base64.b64encode(b"CUSTOMER PHOTO").decode()
    echoed_only = {"status": "completed", "steps": [
        {"type": "user_input", "content": [{"type": "text", "text": "prompt"}, {"type": "image", "data": me}]},
        {"type": "model_output", "content": [{"type": "text", "text": "Sorry, I can't make that image."}]},
    ]}
    with pytest.raises(EngineError, match="can't make that image"):
        run(engine(lambda r: httpx.Response(200, json=echoed_only)).generate(req()))

    with_thought = {"status": "completed", "steps": [
        {"type": "user_input", "content": [{"type": "image", "data": me}]},
        {"type": "thought", "summary": [{"type": "image", "data": me}]},
        {"type": "model_output", "content": [{"type": "image", "mime_type": "image/jpeg", "data": PNG}]},
    ]}
    assert find_images(with_thought) == [("image/jpeg", b"\x89PNG fake")]


def test_failed_interaction_is_an_error():
    body = {"status": "failed", "errors": [{"code": "x", "message": "Image generation blocked"}], "steps": []}
    with pytest.raises(EngineError, match="blocked"):
        run(engine(lambda r: httpx.Response(200, json=body)).generate(req()))


def test_text_model_fallback_on_404():
    seen = []

    def handler(request):
        seen.append(request.url.path)
        if "gemini-flash-latest" in request.url.path:
            return httpx.Response(404, json={"error": {"message": "models/gemini-flash-latest is not found"}})
        return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": '{"body": {"color": "red"}}'}]}}]})

    e = engine(handler)
    assert run(e.analyze_saree(b"S")) == {"body": {"color": "red"}}
    assert run(e.analyze_saree(b"S")) == {"body": {"color": "red"}}
    assert seen == [
        "/v1beta/models/gemini-flash-latest:generateContent",
        "/v1beta/models/gemini-3.8-flash:generateContent",
        "/v1beta/models/gemini-3.8-flash:generateContent",
    ]


def test_startup_check_states():
    def answer(code, text="{}"):
        return lambda r: httpx.Response(code, text=text)

    assert run(engine(answer(200)).check())["state"] == "ok"
    assert run(engine(answer(404)).check())["state"] == "warning"
    bad = run(engine(answer(400, '{"error":{"message":"API key not valid"}}')).check())
    assert bad["state"] == "error" and "API key" in bad["message"]

    def offline(request):
        raise httpx.ConnectError("no route")

    assert run(engine(offline).check())["state"] == "error"


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
