"""Database tables."""
import secrets
import string
import uuid
from datetime import datetime, timezone
from enum import Enum
from typing import Optional

from sqlmodel import Field, SQLModel


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def new_id() -> str:
    return uuid.uuid4().hex[:16]


_CODE_ALPHABET = "".join(c for c in string.ascii_uppercase + string.digits if c not in "O0I1L")


def new_session_code(length: int = 6) -> str:
    """Short, unambiguous code customers can also type in by hand."""
    return "".join(secrets.choice(_CODE_ALPHABET) for _ in range(length))


class AnalysisStatus(str, Enum):
    pending = "pending"
    done = "done"
    failed = "failed"


class Saree(SQLModel, table=True):
    id: str = Field(default_factory=new_id, primary_key=True)
    barcode: str = Field(index=True, unique=True)
    name: str
    fabric: str = ""
    color: str = ""
    price: int = 0  # rupees
    description: str = ""
    image_file: str = ""
    analysis_json: str = ""
    analysis_status: AnalysisStatus = AnalysisStatus.pending
    active: bool = True
    created_at: datetime = Field(default_factory=utcnow)


class SessionStatus(str, Enum):
    open = "open"  # created by staff, waiting at kiosk
    generating = "generating"
    ready = "ready"  # all items finished (done or failed)
    closed = "closed"  # customer finished; photos deleted
    expired = "expired"


class TryOnSession(SQLModel, table=True):
    id: str = Field(default_factory=new_id, primary_key=True)
    code: str = Field(default_factory=new_session_code, index=True, unique=True)
    status: SessionStatus = SessionStatus.open
    mode: str = "self"  # self | gift
    drape_style: str = "nivi"
    customer_label: str = ""  # optional, e.g. "Mrs. Rao" or token number
    created_at: datetime = Field(default_factory=utcnow)
    expires_at: datetime
    consent_at: Optional[datetime] = None
    photo_file: Optional[str] = None
    photo_deleted_at: Optional[datetime] = None
    closed_at: Optional[datetime] = None


class ItemStatus(str, Enum):
    pending = "pending"  # waiting for customer photo
    queued = "queued"
    running = "running"
    done = "done"
    failed = "failed"


class SessionItem(SQLModel, table=True):
    id: str = Field(default_factory=new_id, primary_key=True)
    session_id: str = Field(index=True, foreign_key="tryonsession.id")
    saree_id: str = Field(index=True, foreign_key="saree.id")
    position: int = 0
    status: ItemStatus = ItemStatus.pending
    favorite: bool = False
    result_file: Optional[str] = None
    error: str = ""
    attempts: int = 0
    started_at: Optional[datetime] = None
    finished_at: Optional[datetime] = None
