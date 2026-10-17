import asyncio
import io
from datetime import timedelta

import qrcode
from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse, Response
from sqlmodel import Session, col, select

from .. import storage
from ..config import get_settings
from ..db import get_db
from ..models import ItemStatus, new_session_code, Saree, SessionItem, SessionStatus, TryOnSession, utcnow
from ..prompts import DRAPE_STYLES
from ..schemas import QR_PREFIX, AddItem, FavoriteIn, SessionCreate, SessionOut
from ..security import require_key
from ..worker import refresh_session_status, wipe_session_media
from .sarees import read_upload

router = APIRouter(tags=["sessions"], dependencies=[Depends(require_key)])

CLOSED = (SessionStatus.closed, SessionStatus.expired)


def rows_for(db: Session, sess: TryOnSession) -> list[tuple[SessionItem, Saree]]:
    stmt = (
        select(SessionItem, Saree)
        .where(SessionItem.session_id == sess.id, SessionItem.saree_id == Saree.id)
        .order_by(SessionItem.position)
    )
    return list(db.exec(stmt).all())


def out(db: Session, sess: TryOnSession) -> SessionOut:
    return SessionOut.of(sess, rows_for(db, sess))


def find_session(db: Session, ref: str) -> TryOnSession:
    """Accepts the session id, the 6-char code, or the full QR payload."""
    ref = ref.strip()
    if ref.startswith(QR_PREFIX):
        ref = ref[len(QR_PREFIX):]
    sess = db.get(TryOnSession, ref)
    if not sess:
        sess = db.exec(select(TryOnSession).where(TryOnSession.code == ref.upper())).first()
    if not sess:
        raise HTTPException(404, "Session not found")
    if sess.status not in CLOSED and sess.expires_at < utcnow():
        sess.status = SessionStatus.expired
        wipe_session_media(db, sess)
        db.commit()
    return sess


def require_open(sess: TryOnSession) -> None:
    if sess.status in CLOSED:
        raise HTTPException(410, f"Session is {sess.status.value}")


def resolve_sarees(db: Session, barcodes: list[str], saree_ids: list[str]) -> list[Saree]:
    found: list[Saree] = []
    missing: list[str] = []
    for code in barcodes:
        s = db.exec(select(Saree).where(Saree.barcode == code.strip(), Saree.active == True)).first()  # noqa: E712
        (found.append(s) if s else missing.append(code))
    for sid in saree_ids:
        s = db.get(Saree, sid)
        (found.append(s) if s and s.active else missing.append(sid))
    if missing:
        raise HTTPException(404, {"message": "Unknown sarees", "missing": missing})
    seen, unique = set(), []
    for s in found:
        if s.id not in seen:
            seen.add(s.id)
            unique.append(s)
    return unique


def check_drape(style: str) -> str:
    if style not in DRAPE_STYLES:
        raise HTTPException(422, f"drape_style must be one of {sorted(DRAPE_STYLES)}")
    return style


# ---------------------------------------------------------------- staff side
@router.post("/api/sessions", response_model=SessionOut, status_code=201)
def create_session(body: SessionCreate, db: Session = Depends(get_db)):
    s = get_settings()
    sarees = resolve_sarees(db, body.barcodes, body.saree_ids)
    if not sarees:
        raise HTTPException(422, "Scan at least one saree")
    if len(sarees) > s.max_sarees_per_session:
        raise HTTPException(422, f"At most {s.max_sarees_per_session} sarees per session")
    sess = TryOnSession(
        expires_at=utcnow() + timedelta(minutes=s.session_ttl_minutes),
        drape_style=check_drape(body.drape_style),
        customer_label=body.customer_label[:60],
    )
    while db.exec(select(TryOnSession).where(TryOnSession.code == sess.code)).first():
        sess.code = new_session_code()
    db.add(sess)
    db.flush()
    for pos, saree in enumerate(sarees):
        db.add(SessionItem(session_id=sess.id, saree_id=saree.id, position=pos))
    db.commit()
    db.refresh(sess)
    return out(db, sess)


@router.get("/api/sessions", response_model=list[SessionOut])
def recent_sessions(limit: int = 20, db: Session = Depends(get_db)):
    rows = db.exec(select(TryOnSession).order_by(col(TryOnSession.created_at).desc()).limit(min(limit, 100))).all()
    return [out(db, r) for r in rows]


@router.get("/api/session-lookup", response_model=SessionOut)
def lookup_session(q: str, db: Session = Depends(get_db)):
    """Kiosk entry point: q = scanned QR payload (pallu://session/CODE) or a typed code."""
    return out(db, find_session(db, q))


@router.get("/api/sessions/{ref}", response_model=SessionOut)
def get_session(ref: str, db: Session = Depends(get_db)):
    return out(db, find_session(db, ref))


@router.post("/api/sessions/{ref}/items", response_model=SessionOut)
def add_item(ref: str, body: AddItem, request: Request, db: Session = Depends(get_db)):
    sess = find_session(db, ref)
    require_open(sess)
    sarees = resolve_sarees(db, [body.barcode] if body.barcode else [], [body.saree_id] if body.saree_id else [])
    if not sarees:
        raise HTTPException(422, "Give a barcode or saree_id")
    existing = rows_for(db, sess)
    if any(s.id == sarees[0].id for _, s in existing):
        return out(db, sess)
    if len(existing) >= get_settings().max_sarees_per_session:
        raise HTTPException(422, "Session is full")
    item = SessionItem(session_id=sess.id, saree_id=sarees[0].id, position=len(existing))
    if sess.photo_file:
        item.status = ItemStatus.queued
    db.add(item)
    refresh_session_status(db, sess)
    db.commit()
    if item.status == ItemStatus.queued:
        request.app.state.worker.enqueue(item.id)
    return out(db, sess)


@router.delete("/api/sessions/{ref}/items/{item_id}", response_model=SessionOut)
def remove_item(ref: str, item_id: str, db: Session = Depends(get_db)):
    sess = find_session(db, ref)
    item = db.get(SessionItem, item_id)
    if not item or item.session_id != sess.id:
        raise HTTPException(404, "Item not found")
    storage.delete_rel(item.result_file)
    db.delete(item)
    db.flush()
    refresh_session_status(db, sess)
    db.commit()
    return out(db, sess)


@router.get("/api/sessions/{ref}/qr.png")
def session_qr(ref: str, db: Session = Depends(get_db)):
    sess = find_session(db, ref)
    img = qrcode.make(QR_PREFIX + sess.code, box_size=10, border=2)
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return Response(buf.getvalue(), media_type="image/png")


# ---------------------------------------------------------------- kiosk side
@router.post("/api/sessions/{ref}/photo", response_model=SessionOut)
async def upload_photo(
    ref: str,
    request: Request,
    photo: UploadFile = File(...),
    consent: bool = Form(False, description="Customer agreed to the photo being processed"),
    mode: str = Form("self", pattern="^(self|gift)$"),
    drape_style: str = Form(""),
    db: Session = Depends(get_db),
):
    if not consent:
        raise HTTPException(400, "Consent is required before a photo can be processed")
    sess = find_session(db, ref)
    require_open(sess)
    jpeg = await read_upload(photo)

    wipe_session_media(db, sess)  # replacing an earlier photo also clears old results
    path = storage.session_dir(sess.id) / "photo.jpg"
    await asyncio.to_thread(storage.write_bytes, path, jpeg)
    sess.photo_file = storage.rel(path)
    sess.photo_deleted_at = None
    sess.consent_at = utcnow()
    sess.mode = mode
    if drape_style:
        sess.drape_style = check_drape(drape_style)

    queued = []
    for item, _ in rows_for(db, sess):
        item.status = ItemStatus.queued
        item.error = ""
        item.result_file = None
        db.add(item)
        queued.append(item.id)
    refresh_session_status(db, sess)
    db.commit()
    for item_id in queued:
        request.app.state.worker.enqueue(item_id)
    db.refresh(sess)
    return out(db, sess)


@router.post("/api/sessions/{ref}/items/{item_id}/retry", response_model=SessionOut)
def retry_item(ref: str, item_id: str, request: Request, db: Session = Depends(get_db)):
    sess = find_session(db, ref)
    require_open(sess)
    if not sess.photo_file:
        raise HTTPException(409, "No customer photo; take a photo first")
    item = db.get(SessionItem, item_id)
    if not item or item.session_id != sess.id:
        raise HTTPException(404, "Item not found")
    if item.status in (ItemStatus.queued, ItemStatus.running):
        return out(db, sess)
    storage.delete_rel(item.result_file)
    item.result_file = None
    item.status = ItemStatus.queued
    item.error = ""
    db.add(item)
    refresh_session_status(db, sess)
    db.commit()
    request.app.state.worker.enqueue(item.id)
    return out(db, sess)


@router.post("/api/sessions/{ref}/items/{item_id}/favorite", response_model=SessionOut)
def set_favorite(ref: str, item_id: str, body: FavoriteIn, db: Session = Depends(get_db)):
    sess = find_session(db, ref)
    item = db.get(SessionItem, item_id)
    if not item or item.session_id != sess.id:
        raise HTTPException(404, "Item not found")
    item.favorite = body.favorite
    db.add(item)
    db.commit()
    return out(db, sess)


@router.post("/api/sessions/{ref}/finish", response_model=SessionOut)
def finish_session(ref: str, db: Session = Depends(get_db)):
    """Customer is done: delete photo + generated images now; keep the shortlist for staff."""
    sess = find_session(db, ref)
    wipe_session_media(db, sess)
    sess.status = SessionStatus.closed
    sess.closed_at = utcnow()
    db.add(sess)
    db.commit()
    return out(db, sess)


@router.delete("/api/sessions/{ref}/photo", response_model=SessionOut)
def delete_photo(ref: str, db: Session = Depends(get_db)):
    sess = find_session(db, ref)
    wipe_session_media(db, sess)
    refresh_session_status(db, sess)
    db.commit()
    return out(db, sess)


@router.get("/api/results/{item_id}/image")
def result_image(item_id: str, db: Session = Depends(get_db)):
    item = db.get(SessionItem, item_id)
    if not item or not item.result_file:
        raise HTTPException(404, "No image (it may have been deleted for privacy)")
    path = storage.resolve(item.result_file)
    if not path.exists():
        raise HTTPException(404, "Image deleted")
    return FileResponse(path, media_type="image/jpeg", headers={"Cache-Control": "private, no-store"})
