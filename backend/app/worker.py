"""Background work: generating try-ons, analysing sarees, and purging photos."""
import asyncio
import json
import logging
from datetime import timedelta

from sqlmodel import Session, select

from .config import Settings
from .db import get_engine
from .engines import EngineError, TryOnEngine, TryOnRequest
from .models import (
    AnalysisStatus,
    ItemStatus,
    Saree,
    SessionItem,
    SessionStatus,
    TryOnSession,
    utcnow,
)
from .prompts import SareeInfo
from . import storage

log = logging.getLogger("pallu.worker")

ACTIVE_ITEM = (ItemStatus.pending, ItemStatus.queued, ItemStatus.running)


def saree_info(s: Saree) -> SareeInfo:
    try:
        analysis = json.loads(s.analysis_json) if s.analysis_json else {}
    except json.JSONDecodeError:
        analysis = {}
    return SareeInfo(name=s.name, fabric=s.fabric, color=s.color, description=s.description, analysis=analysis)


def refresh_session_status(db: Session, sess: TryOnSession) -> None:
    if sess.status in (SessionStatus.closed, SessionStatus.expired):
        return
    items = db.exec(select(SessionItem).where(SessionItem.session_id == sess.id)).all()
    if not sess.photo_file:
        sess.status = SessionStatus.open
    elif any(i.status in ACTIVE_ITEM for i in items):
        sess.status = SessionStatus.generating
    else:
        sess.status = SessionStatus.ready
    db.add(sess)


def wipe_session_media(db: Session, sess: TryOnSession) -> None:
    """Delete the customer photo and every generated image for a session."""
    storage.delete_session_files(sess.id)
    if sess.photo_file:
        sess.photo_file = None
        sess.photo_deleted_at = utcnow()
    for item in db.exec(select(SessionItem).where(SessionItem.session_id == sess.id)).all():
        if item.result_file or item.status in ACTIVE_ITEM:
            item.result_file = None
            if item.status in ACTIVE_ITEM:
                item.status = ItemStatus.pending
            db.add(item)
    db.add(sess)


class Worker:
    def __init__(self, engine: TryOnEngine, settings: Settings):
        self.engine = engine
        self.s = settings
        self.queue: asyncio.Queue[str] = asyncio.Queue()
        self._tasks: list[asyncio.Task] = []
        self._side: set[asyncio.Task] = set()

    # ---------- lifecycle ----------
    async def start(self) -> None:
        for n in range(max(1, self.s.max_concurrent_generations)):
            self._tasks.append(asyncio.create_task(self._loop(), name=f"tryon-{n}"))
        self._tasks.append(asyncio.create_task(self._purge_loop(), name="purge"))
        self._requeue_unfinished()

    async def stop(self) -> None:
        for t in self._tasks + list(self._side):
            t.cancel()
        await asyncio.gather(*self._tasks, *self._side, return_exceptions=True)
        close = getattr(self.engine, "aclose", None)
        if close:
            await close()

    def _requeue_unfinished(self) -> None:
        with Session(get_engine()) as db:
            rows = db.exec(
                select(SessionItem).where(SessionItem.status.in_([ItemStatus.queued, ItemStatus.running]))
            ).all()
            for item in rows:
                item.status = ItemStatus.queued
                db.add(item)
                self.queue.put_nowait(item.id)
            db.commit()
        if rows:
            log.info("Re-queued %d unfinished try-ons", len(rows))

    # ---------- public ----------
    def enqueue(self, item_id: str) -> None:
        self.queue.put_nowait(item_id)

    def analyze_later(self, saree_id: str) -> None:
        t = asyncio.create_task(self.analyze(saree_id))
        self._side.add(t)
        t.add_done_callback(self._side.discard)

    async def analyze(self, saree_id: str) -> None:
        with Session(get_engine()) as db:
            saree = db.get(Saree, saree_id)
            if not saree or not saree.image_file:
                return
            image = storage.resolve(saree.image_file).read_bytes()
        try:
            result = await self.engine.analyze_saree(image)
            status, payload = AnalysisStatus.done, json.dumps(result, ensure_ascii=False)
        except Exception as e:  # noqa: BLE001 - never crash the worker
            log.warning("Saree %s analysis failed: %s", saree_id, e)
            status, payload = AnalysisStatus.failed, ""
        with Session(get_engine()) as db:
            saree = db.get(Saree, saree_id)
            if saree:
                saree.analysis_status = status
                if payload:
                    saree.analysis_json = payload
                db.add(saree)
                db.commit()

    # ---------- try-on ----------
    async def _loop(self) -> None:
        while True:
            item_id = await self.queue.get()
            try:
                await self.process(item_id)
            except asyncio.CancelledError:
                raise
            except Exception:  # noqa: BLE001
                log.exception("Unexpected error processing %s", item_id)
            finally:
                self.queue.task_done()

    async def process(self, item_id: str) -> None:
        with Session(get_engine()) as db:
            item = db.get(SessionItem, item_id)
            if not item or item.status != ItemStatus.queued:
                return
            sess = db.get(TryOnSession, item.session_id)
            saree = db.get(Saree, item.saree_id)
            if not sess or not sess.photo_file or not saree:
                item.status = ItemStatus.pending
                db.add(item)
                db.commit()
                return
            item.status = ItemStatus.running
            item.started_at = utcnow()
            item.attempts += 1
            item.error = ""
            db.add(item)
            refresh_session_status(db, sess)
            db.commit()
            req = TryOnRequest(
                person_jpeg=storage.resolve(sess.photo_file).read_bytes(),
                saree_jpeg=storage.resolve(saree.image_file).read_bytes(),
                saree=saree_info(saree),
                drape_style=sess.drape_style,
                mode=sess.mode,
            )
            session_id = sess.id

        result: bytes | None = None
        error = ""
        try:
            raw = await self.engine.generate(req)
            result = await asyncio.to_thread(storage.normalize_image, raw, 2048, 92)
        except EngineError as e:
            error = str(e)
        except Exception as e:  # noqa: BLE001
            log.exception("Engine crashed for item %s", item_id)
            error = f"Unexpected error: {e}"

        with Session(get_engine()) as db:
            item = db.get(SessionItem, item_id)
            sess = db.get(TryOnSession, session_id)
            if not item or not sess:
                return
            # The customer may have deleted their photo while we were generating.
            if not sess.photo_file or sess.status in (SessionStatus.closed, SessionStatus.expired):
                item.status = ItemStatus.pending
                db.add(item)
                db.commit()
                return
            if result is not None:
                path = storage.session_dir(session_id) / "results" / f"{item.id}.jpg"
                storage.write_bytes(path, result)
                item.result_file = storage.rel(path)
                item.status = ItemStatus.done
            else:
                item.status = ItemStatus.failed
                item.error = error[:500]
            item.finished_at = utcnow()
            db.add(item)
            refresh_session_status(db, sess)
            db.commit()

    # ---------- privacy ----------
    async def _purge_loop(self) -> None:
        while True:
            try:
                await asyncio.to_thread(self.purge_once)
            except Exception:  # noqa: BLE001
                log.exception("Purge failed")
            await asyncio.sleep(self.s.purge_interval_s)

    def purge_once(self) -> int:
        now = utcnow()
        photo_cutoff = now - timedelta(minutes=self.s.photo_ttl_minutes)
        purged = 0
        with Session(get_engine()) as db:
            with_photo = db.exec(select(TryOnSession).where(TryOnSession.photo_file.is_not(None))).all()
            for sess in with_photo:
                if (sess.consent_at and sess.consent_at < photo_cutoff) or sess.expires_at < now:
                    wipe_session_media(db, sess)
                    if sess.status not in (SessionStatus.closed,):
                        sess.status = SessionStatus.expired
                    purged += 1
            stale = db.exec(
                select(TryOnSession).where(
                    TryOnSession.status == SessionStatus.open, TryOnSession.expires_at < now
                )
            ).all()
            for sess in stale:
                sess.status = SessionStatus.expired
                db.add(sess)
            db.commit()
        if purged:
            log.info("Auto-deleted photos for %d session(s)", purged)
        return purged
