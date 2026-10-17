"""Pallu — AI saree try-on backend.

Run:  uvicorn app.main:app --host 0.0.0.0 --port 8000
Docs: http://<host>:8000/docs     Admin: http://<host>:8000/admin
"""
import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import RedirectResponse
from fastapi.staticfiles import StaticFiles

from .config import get_settings
from .db import init_db
from .engines import build_engine
from .routers import meta, sarees, sessions
from .worker import Worker

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("pallu")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    init_db()
    engine = build_engine(settings)
    if settings.api_key == "change-me":
        log.warning("API_KEY is still the default 'change-me'. Set it in .env before using this in a store.")
    worker = Worker(engine, settings)
    app.state.worker = worker
    await worker.start()
    log.info("Pallu ready: store=%r engine=%s", settings.store_name, engine.name)
    try:
        yield
    finally:
        await worker.stop()


app = FastAPI(title="Pallu — AI Saree Try-On", version="0.1.0", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
app.include_router(meta.router)
app.include_router(sarees.router)
app.include_router(sessions.router)

_static = Path(__file__).parent / "static" / "admin"
app.mount("/admin", StaticFiles(directory=_static, html=True), name="admin")


@app.get("/", include_in_schema=False)
def root():
    return RedirectResponse("/admin/")
