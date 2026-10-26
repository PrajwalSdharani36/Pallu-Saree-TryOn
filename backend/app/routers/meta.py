from datetime import timedelta

from fastapi import APIRouter, Depends, Request
from sqlmodel import Session, func, select

from ..config import get_settings
from ..db import get_db
from ..models import ItemStatus, Saree, SessionItem, TryOnSession, utcnow
from ..prompts import DRAPE_STYLES
from ..schemas import DrapeStyleOut
from ..security import require_key

router = APIRouter(tags=["meta"])


@router.get("/api/health")
def health(request: Request):
    s = get_settings()
    engine = request.app.state.worker.engine
    engine_status = getattr(engine, "status", None) or {
        "state": "demo", "message": "Demo mode: looks are placeholders. Set ENGINE=openrouter or ENGINE=gemini for real try-ons.",
    }
    return {"status": "ok", "store_name": s.store_name, "engine": engine.name, "engine_status": engine_status}


@router.get("/api/drape-styles", response_model=list[DrapeStyleOut])
def drape_styles():
    return [DrapeStyleOut(id=k, description=v) for k, v in DRAPE_STYLES.items()]


@router.get("/api/stats", dependencies=[Depends(require_key)])
def stats(days: int = 1, db: Session = Depends(get_db)):
    """Numbers the store owner cares about: visits, try-ons, speed, favourites."""
    since = utcnow() - timedelta(days=max(1, days))
    sessions = db.exec(select(func.count()).select_from(TryOnSession).where(TryOnSession.created_at >= since)).one()
    with_photo = db.exec(
        select(func.count()).select_from(TryOnSession).where(
            TryOnSession.created_at >= since, TryOnSession.consent_at.is_not(None)
        )
    ).one()
    items = db.exec(
        select(SessionItem).join(TryOnSession, SessionItem.session_id == TryOnSession.id).where(
            TryOnSession.created_at >= since
        )
    ).all()
    done = [i for i in items if i.status == ItemStatus.done and i.started_at and i.finished_at]
    secs = [(i.finished_at - i.started_at).total_seconds() for i in done]
    by_saree: dict[str, dict] = {}
    for i in items:
        row = by_saree.setdefault(i.saree_id, {"tryons": 0, "favorites": 0})
        row["tryons"] += 1 if i.status == ItemStatus.done else 0
        row["favorites"] += 1 if i.favorite else 0
    names = {s.id: s.name for s in db.exec(select(Saree).where(Saree.id.in_(list(by_saree) or [""]))).all()}
    top = sorted(by_saree.items(), key=lambda kv: (kv[1]["favorites"], kv[1]["tryons"]), reverse=True)[:10]
    return {
        "days": days,
        "sessions": sessions,
        "sessions_with_photo": with_photo,
        "tryons_done": len(done),
        "tryons_failed": sum(1 for i in items if i.status == ItemStatus.failed),
        "favorites": sum(1 for i in items if i.favorite),
        "avg_generation_s": round(sum(secs) / len(secs), 1) if secs else None,
        "catalog_size": db.exec(select(func.count()).select_from(Saree).where(Saree.active == True)).one(),  # noqa: E712
        "top_sarees": [{"saree_id": k, "name": names.get(k, "?"), **v} for k, v in top],
    }
