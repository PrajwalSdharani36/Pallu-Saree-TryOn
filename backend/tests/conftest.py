import io
import time

import pytest
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw

KEY = "test-key"


def make_jpeg(color=(150, 20, 60), size=(600, 800), border=(220, 180, 60)) -> bytes:
    img = Image.new("RGB", size, color)
    d = ImageDraw.Draw(img)
    d.rectangle([0, size[1] - 80, size[0], size[1]], fill=border)
    buf = io.BytesIO()
    img.save(buf, "JPEG")
    return buf.getvalue()


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    monkeypatch.setenv("API_KEY", KEY)
    monkeypatch.setenv("ENGINE", "mock")
    monkeypatch.setenv("MOCK_DELAY_S", "0.05")
    monkeypatch.setenv("PURGE_INTERVAL_S", "3600")
    from app import config, db

    config.get_settings.cache_clear()
    db.reset_engine()
    from app.main import app

    with TestClient(app, headers={"X-API-Key": KEY}) as c:
        yield c
    db.reset_engine()
    config.get_settings.cache_clear()


def add_saree(client, name="Kanjivaram Silk", barcode="", color=(150, 20, 60)):
    r = client.post(
        "/api/sarees",
        data={"name": name, "barcode": barcode, "fabric": "Silk", "color": "Maroon", "price": "12500"},
        files={"image": ("s.jpg", make_jpeg(color), "image/jpeg")},
    )
    assert r.status_code == 201, r.text
    return r.json()


def wait_for(client, ref, pred, timeout=10.0):
    end = time.time() + timeout
    while time.time() < end:
        s = client.get(f"/api/sessions/{ref}").json()
        if pred(s):
            return s
        time.sleep(0.05)
    raise AssertionError(f"timeout; last state: {s}")
