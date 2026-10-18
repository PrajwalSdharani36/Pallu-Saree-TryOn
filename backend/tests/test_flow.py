from datetime import timedelta

from .conftest import KEY, add_saree, make_jpeg, wait_for


def test_health_and_auth(client):
    h = client.get("/api/health").json()
    assert h["engine"] == "mock" and h["engine_status"]["state"] == "demo"
    r = client.get("/api/sarees", headers={"X-API-Key": "wrong"})
    assert r.status_code == 401
    assert client.get(f"/api/sarees?key={KEY}", headers={"X-API-Key": ""}).status_code == 200


def test_catalog_crud_and_analysis(client):
    s = add_saree(client, barcode="SAR001")
    assert s["barcode"] == "SAR001" and s["price"] == 12500
    auto = add_saree(client, name="Banarasi")
    assert auto["barcode"].startswith("PS")
    # duplicate barcode
    r = client.post("/api/sarees", data={"name": "x", "barcode": "SAR001"},
                    files={"image": ("s.jpg", make_jpeg(), "image/jpeg")})
    assert r.status_code == 409
    # analysis runs in the background (mock engine extracts colours)
    full = client.post(f"/api/sarees/{s['id']}/analyze").json()
    assert full["analysis_status"] == "done" and full["analysis"]["body"]["color"].startswith("#")
    assert client.get("/api/sarees/barcode/SAR001").json()["id"] == s["id"]
    assert client.get(f"/api/sarees/{s['id']}/image").headers["content-type"] == "image/jpeg"
    assert client.get(f"/api/sarees/{s['id']}/label.png").headers["content-type"] == "image/png"
    assert len(client.get("/api/sarees?q=banar").json()) == 1
    client.patch(f"/api/sarees/{s['id']}", json={"price": 9999})
    assert client.get(f"/api/sarees/{s['id']}").json()["price"] == 9999
    assert client.delete(f"/api/sarees/{s['id']}").status_code == 204
    assert client.get("/api/sarees/barcode/SAR001").status_code == 404


def test_bad_image_rejected(client):
    r = client.post("/api/sarees", data={"name": "x"}, files={"image": ("s.jpg", b"not an image", "image/jpeg")})
    assert r.status_code == 400


def test_full_store_flow(client):
    a = add_saree(client, "Kanjivaram", "A1")
    b = add_saree(client, "Banarasi", "B2", color=(20, 60, 140))

    # staff scans two barcodes -> session + QR
    r = client.post("/api/sessions", json={"barcodes": ["A1", "B2", "A1"], "customer_label": "Token 7"})
    assert r.status_code == 201, r.text
    sess = r.json()
    assert sess["total"] == 2 and sess["status"] == "open"
    assert sess["qr_payload"] == f"pallu://session/{sess['code']}"
    assert client.get(sess["qr_url"]).headers["content-type"] == "image/png"
    assert client.get("/api/session-lookup", params={"q": sess["code"].lower()}).status_code == 200

    # kiosk looks the session up from the scanned QR payload
    assert client.get("/api/session-lookup", params={"q": sess["qr_payload"]}).json()["id"] == sess["id"]

    # no consent -> refused
    r = client.post(f"/api/sessions/{sess['code']}/photo", files={"photo": ("p.jpg", make_jpeg(), "image/jpeg")})
    assert r.status_code == 400

    r = client.post(
        f"/api/sessions/{sess['code']}/photo",
        data={"consent": "true", "mode": "self", "drape_style": "bengali"},
        files={"photo": ("p.jpg", make_jpeg((200, 170, 150)), "image/jpeg")},
    )
    assert r.status_code == 200, r.text
    assert r.json()["has_photo"] and r.json()["drape_style"] == "bengali"

    done = wait_for(client, sess["id"], lambda s: s["status"] == "ready")
    assert done["done"] == 2
    item = done["items"][0]
    img = client.get(item["result_url"])
    assert img.status_code == 200 and img.headers["content-type"] == "image/jpeg"

    # staff adds one more saree while the customer is at the kiosk -> generated straight away
    c = add_saree(client, "Chanderi", "C3", color=(240, 230, 200))
    client.post(f"/api/sessions/{sess['id']}/items", json={"barcode": "C3"})
    done = wait_for(client, sess["id"], lambda s: s["total"] == 3 and s["status"] == "ready")
    assert done["done"] == 3

    # favourite + retry
    client.post(f"/api/sessions/{sess['id']}/items/{item['id']}/favorite", json={"favorite": True})
    client.post(f"/api/sessions/{sess['id']}/items/{item['id']}/retry")
    done = wait_for(client, sess["id"], lambda s: s["status"] == "ready")
    assert done["items"][0]["favorite"] is True

    stats = client.get("/api/stats").json()
    assert stats["sessions"] == 1 and stats["tryons_done"] >= 3 and stats["favorites"] == 1

    # finish -> photos and generated images gone, shortlist kept
    fin = client.post(f"/api/sessions/{sess['id']}/finish").json()
    assert fin["status"] == "closed" and not fin["has_photo"]
    assert all(i["result_url"] is None for i in fin["items"])
    assert fin["items"][0]["favorite"] is True
    assert client.get(item["result_url"]).status_code == 404
    # closed sessions accept no new photos
    r = client.post(f"/api/sessions/{sess['id']}/photo", data={"consent": "true"},
                    files={"photo": ("p.jpg", make_jpeg(), "image/jpeg")})
    assert r.status_code == 410
    assert {a["id"], b["id"], c["id"]} == {i["saree"]["id"] for i in fin["items"]}


def test_unknown_barcode(client):
    add_saree(client, "A", "A1")
    r = client.post("/api/sessions", json={"barcodes": ["A1", "NOPE"]})
    assert r.status_code == 404 and r.json()["detail"]["missing"] == ["NOPE"]


def test_auto_purge(client):
    from sqlmodel import Session

    from app.db import get_engine
    from app.models import TryOnSession

    add_saree(client, "A", "A1")
    sess = client.post("/api/sessions", json={"barcodes": ["A1"]}).json()
    client.post(f"/api/sessions/{sess['id']}/photo", data={"consent": "true", "mode": "gift"},
                files={"photo": ("p.jpg", make_jpeg(), "image/jpeg")})
    wait_for(client, sess["id"], lambda s: s["status"] == "ready")

    with Session(get_engine()) as db:  # pretend consent was given long ago
        row = db.get(TryOnSession, sess["id"])
        row.consent_at = row.consent_at - timedelta(hours=2)
        db.add(row)
        db.commit()
    worker = client.app.state.worker
    assert worker.purge_once() == 1
    s = client.get(f"/api/sessions/{sess['id']}").json()
    assert s["status"] == "expired" and not s["has_photo"] and s["items"][0]["result_url"] is None


def test_delete_photo_now(client):
    add_saree(client, "A", "A1")
    sess = client.post("/api/sessions", json={"barcodes": ["A1"]}).json()
    client.post(f"/api/sessions/{sess['id']}/photo", data={"consent": "true"},
                files={"photo": ("p.jpg", make_jpeg(), "image/jpeg")})
    s = client.delete(f"/api/sessions/{sess['id']}/photo").json()
    assert not s["has_photo"] and s["status"] == "open"


def test_gift_flow_staff_uploads_photo(client):
    add_saree(client, "A", "A1")
    sess = client.post("/api/sessions", json={"barcodes": ["A1"]}).json()
    # kiosk: customer picks "someone else" + Bengali drape, then waits
    s = client.patch(f"/api/sessions/{sess['code']}", json={"mode": "gift", "drape_style": "bengali"}).json()
    assert s["mode"] == "gift" and s["drape_style"] == "bengali" and not s["has_photo"]
    assert client.patch(f"/api/sessions/{sess['id']}", json={"drape_style": "saree-wrap"}).status_code == 422
    # staff phone uploads the photo the customer sent; empty drape keeps the kiosk's choice
    r = client.post(f"/api/sessions/{sess['id']}/photo", data={"consent": "true", "mode": "gift", "drape_style": ""},
                    files={"photo": ("p.jpg", make_jpeg(), "image/jpeg")})
    assert r.status_code == 200 and r.json()["drape_style"] == "bengali"
    done = wait_for(client, sess["id"], lambda s: s["status"] == "ready")
    assert done["done"] == 1 and done["mode"] == "gift"


def test_switching_to_real_engine_rereads_demo_analysis(client):
    import asyncio

    from app.worker import Worker
    from app.models import AnalysisStatus, Saree
    from app.db import get_engine
    from sqlmodel import Session

    s = add_saree(client, "A", "A1")
    client.post(f"/api/sarees/{s['id']}/analyze")  # mock analysis, tagged _engine=mock

    class FakeReal:
        name = "gemini"
        async def analyze_saree(self, jpeg):
            return {"body": {"color": "red"}, "summary": "real"}

    w = Worker(FakeReal(), client.app.state.worker.s)

    async def go():
        w._reanalyze_stale()
        await asyncio.gather(*list(w._side))

    asyncio.run(go())
    with Session(get_engine()) as db:
        row = db.get(Saree, s["id"])
        assert row.analysis_status == AnalysisStatus.done and '"real"' in row.analysis_json
