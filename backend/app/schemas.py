import json
from datetime import datetime, timedelta, timezone
from typing import Optional

from pydantic import BaseModel, Field

from .config import get_settings
from .models import Saree, SessionItem, TryOnSession

QR_PREFIX = "pallu://session/"


def iso(dt: Optional[datetime]) -> Optional[str]:
    if not dt:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class SareeOut(BaseModel):
    id: str
    barcode: str
    name: str
    fabric: str
    color: str
    price: int
    description: str
    image_url: str
    label_url: str
    analysis_status: str
    analysis: Optional[dict] = None
    active: bool

    @classmethod
    def of(cls, s: Saree, with_analysis: bool = True) -> "SareeOut":
        analysis = None
        if with_analysis and s.analysis_json:
            try:
                analysis = json.loads(s.analysis_json)
            except json.JSONDecodeError:
                analysis = None
        return cls(
            id=s.id,
            barcode=s.barcode,
            name=s.name,
            fabric=s.fabric,
            color=s.color,
            price=s.price,
            description=s.description,
            image_url=f"/api/sarees/{s.id}/image",
            label_url=f"/api/sarees/{s.id}/label.png",
            analysis_status=s.analysis_status.value,
            analysis=analysis,
            active=s.active,
        )


class SareeUpdate(BaseModel):
    name: Optional[str] = None
    barcode: Optional[str] = None
    fabric: Optional[str] = None
    color: Optional[str] = None
    price: Optional[int] = Field(default=None, ge=0)
    description: Optional[str] = None
    active: Optional[bool] = None


class ItemOut(BaseModel):
    id: str
    position: int
    status: str
    drape_style: str
    favorite: bool
    error: str
    result_url: Optional[str]
    saree: SareeOut


class SessionOut(BaseModel):
    id: str
    code: str
    qr_payload: str
    qr_url: str
    status: str
    mode: str
    drape_style: str
    customer_label: str
    created_at: str
    expires_at: str
    has_photo: bool
    photo_expires_at: Optional[str]
    photo_ttl_minutes: int
    total: int
    done: int
    failed: int
    items: list[ItemOut]

    @classmethod
    def of(cls, sess: TryOnSession, rows: list[tuple[SessionItem, Saree]]) -> "SessionOut":
        ttl = get_settings().photo_ttl_minutes
        photo_exp = None
        if sess.photo_file and sess.consent_at:
            photo_exp = min(sess.consent_at + timedelta(minutes=ttl), sess.expires_at)
        items = [
            ItemOut(
                id=i.id,
                position=i.position,
                status=i.status.value,
                drape_style=i.drape_style or sess.drape_style,
                favorite=i.favorite,
                error=i.error,
                result_url=(
                    f"/api/results/{i.id}/image?v={int(i.finished_at.timestamp())}"
                    if i.result_file and i.finished_at
                    else None
                ),
                saree=SareeOut.of(s, with_analysis=False),
            )
            for i, s in rows
        ]
        return cls(
            id=sess.id,
            code=sess.code,
            qr_payload=QR_PREFIX + sess.code,
            qr_url=f"/api/sessions/{sess.id}/qr.png",
            status=sess.status.value,
            mode=sess.mode,
            drape_style=sess.drape_style,
            customer_label=sess.customer_label,
            created_at=iso(sess.created_at),
            expires_at=iso(sess.expires_at),
            has_photo=bool(sess.photo_file),
            photo_expires_at=iso(photo_exp),
            photo_ttl_minutes=ttl,
            total=len(items),
            done=sum(1 for i in items if i.status == "done"),
            failed=sum(1 for i in items if i.status == "failed"),
            items=items,
        )


class SessionCreate(BaseModel):
    barcodes: list[str] = []
    saree_ids: list[str] = []
    drape_style: str = "nivi"
    customer_label: str = ""


class SessionUpdate(BaseModel):
    drape_style: Optional[str] = None
    mode: Optional[str] = Field(default=None, pattern="^(self|gift)$")
    customer_label: Optional[str] = Field(default=None, max_length=60)


class AddItem(BaseModel):
    barcode: Optional[str] = None
    saree_id: Optional[str] = None


class RedrapeIn(BaseModel):
    drape_style: str


class FavoriteIn(BaseModel):
    favorite: bool


class DrapeStyleOut(BaseModel):
    id: str
    description: str
