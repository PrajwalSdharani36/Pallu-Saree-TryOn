from collections.abc import Iterator

from sqlalchemy import event, inspect, text
from sqlmodel import Session, SQLModel, create_engine

from .config import get_settings

_engine = None


def get_engine():
    global _engine
    if _engine is None:
        url = get_settings().db_url
        kwargs = {}
        if url.startswith("sqlite"):
            kwargs["connect_args"] = {"check_same_thread": False}
        _engine = create_engine(url, **kwargs)
        if url.startswith("sqlite"):

            @event.listens_for(_engine, "connect")
            def _sqlite_pragmas(dbapi_conn, _):  # pragma: no cover - trivial
                cur = dbapi_conn.cursor()
                cur.execute("PRAGMA journal_mode=WAL")
                cur.execute("PRAGMA foreign_keys=ON")
                cur.close()

    return _engine


def reset_engine() -> None:
    """Used by tests to point at a fresh database."""
    global _engine
    if _engine is not None:
        _engine.dispose()
    _engine = None


def init_db() -> None:
    from . import models  # noqa: F401  (register tables)

    engine = get_engine()
    SQLModel.metadata.create_all(engine)
    _add_missing_columns(engine)


# Columns added after the first release. create_all() makes new tables but never changes old ones,
# so an existing pallu.db gets these added here (all nullable, so old rows stay valid).
_LATER_COLUMNS = {"sessionitem": {"drape_style": "VARCHAR"}}


def _add_missing_columns(engine) -> None:
    insp = inspect(engine)
    with engine.begin() as conn:
        for table, cols in _LATER_COLUMNS.items():
            if not insp.has_table(table):
                continue
            have = {c["name"] for c in insp.get_columns(table)}
            for name, sql_type in cols.items():
                if name not in have:
                    conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {name} {sql_type}"))


def get_db() -> Iterator[Session]:
    with Session(get_engine()) as session:
        yield session
