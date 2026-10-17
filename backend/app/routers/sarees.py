import asyncio
import io
import secrets

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse, Response
from PIL import Image, ImageDraw, ImageFont
from sqlmodel import Session, col, or_, select

from .. import storage
from ..config import get_settings
from ..db import get_db
from ..models import AnalysisStatus, Saree
from ..schemas import SareeOut, SareeUpdate
from ..security import require_key

router = APIRouter(prefix="/api/sarees", tags=["sarees"], dependencies=[Depends(require_key)])


async def read_upload(f: UploadFile) -> bytes:
    limit = get_settings().max_upload_mb * 1024 * 1024
    data = await f.read(limit + 1)
    if len(data) > limit:
        raise HTTPException(413, f"Image larger than {get_settings().max_upload_mb} MB")
    try:
        return await asyncio.to_thread(storage.normalize_image, data)
    except storage.BadImage as e:
        raise HTTPException(400, str(e)) from e


def get_saree(db: Session, saree_id: str) -> Saree:
    s = db.get(Saree, saree_id)
    if not s:
        raise HTTPException(404, "Saree not found")
    return s


def unique_barcode(db: Session) -> str:
    while True:
        code = "PS" + "".join(secrets.choice("0123456789") for _ in range(8))
        if not db.exec(select(Saree).where(Saree.barcode == code)).first():
            return code


@router.get("", response_model=list[SareeOut])
def list_sarees(q: str = "", include_inactive: bool = False, limit: int = 200, db: Session = Depends(get_db)):
    stmt = select(Saree)
    if not include_inactive:
        stmt = stmt.where(Saree.active == True)  # noqa: E712
    if q:
        like = f"%{q}%"
        stmt = stmt.where(
            or_(col(Saree.name).ilike(like), col(Saree.barcode).ilike(like), col(Saree.fabric).ilike(like),
                col(Saree.color).ilike(like))
        )
    stmt = stmt.order_by(col(Saree.created_at).desc()).limit(min(limit, 500))
    return [SareeOut.of(s, with_analysis=False) for s in db.exec(stmt).all()]


@router.post("", response_model=SareeOut, status_code=201)
async def create_saree(
    request: Request,
    image: UploadFile = File(..., description="Photo of the folded saree"),
    name: str = Form(...),
    barcode: str = Form(""),
    fabric: str = Form(""),
    color: str = Form(""),
    price: int = Form(0),
    description: str = Form(""),
    db: Session = Depends(get_db),
):
    barcode = barcode.strip() or unique_barcode(db)
    if db.exec(select(Saree).where(Saree.barcode == barcode)).first():
        raise HTTPException(409, f"Barcode {barcode} already exists")
    jpeg = await read_upload(image)
    saree = Saree(barcode=barcode, name=name.strip(), fabric=fabric, color=color, price=max(0, price),
                  description=description)
    path = storage.saree_dir() / f"{saree.id}.jpg"
    storage.write_bytes(path, jpeg)
    saree.image_file = storage.rel(path)
    db.add(saree)
    db.commit()
    db.refresh(saree)
    request.app.state.worker.analyze_later(saree.id)
    return SareeOut.of(saree)


@router.get("/barcode/{barcode}", response_model=SareeOut)
def by_barcode(barcode: str, db: Session = Depends(get_db)):
    s = db.exec(select(Saree).where(Saree.barcode == barcode.strip())).first()
    if not s or not s.active:
        raise HTTPException(404, f"No saree with barcode {barcode}")
    return SareeOut.of(s, with_analysis=False)


@router.get("/{saree_id}", response_model=SareeOut)
def get_one(saree_id: str, db: Session = Depends(get_db)):
    return SareeOut.of(get_saree(db, saree_id))


@router.patch("/{saree_id}", response_model=SareeOut)
def update_saree(saree_id: str, body: SareeUpdate, db: Session = Depends(get_db)):
    s = get_saree(db, saree_id)
    data = body.model_dump(exclude_unset=True)
    if "barcode" in data and data["barcode"] != s.barcode:
        if db.exec(select(Saree).where(Saree.barcode == data["barcode"])).first():
            raise HTTPException(409, "Barcode already in use")
    for k, v in data.items():
        setattr(s, k, v)
    db.add(s)
    db.commit()
    db.refresh(s)
    return SareeOut.of(s)


@router.put("/{saree_id}/image", response_model=SareeOut)
async def replace_image(saree_id: str, request: Request, image: UploadFile = File(...), db: Session = Depends(get_db)):
    s = get_saree(db, saree_id)
    jpeg = await read_upload(image)
    storage.write_bytes(storage.saree_dir() / f"{s.id}.jpg", jpeg)
    s.analysis_status = AnalysisStatus.pending
    s.analysis_json = ""
    db.add(s)
    db.commit()
    request.app.state.worker.analyze_later(s.id)
    return SareeOut.of(s)


@router.post("/{saree_id}/analyze", response_model=SareeOut)
async def reanalyze(saree_id: str, request: Request, db: Session = Depends(get_db)):
    s = get_saree(db, saree_id)
    await request.app.state.worker.analyze(s.id)
    db.refresh(s)
    return SareeOut.of(s)


@router.delete("/{saree_id}", status_code=204)
def deactivate(saree_id: str, db: Session = Depends(get_db)):
    s = get_saree(db, saree_id)
    s.active = False
    db.add(s)
    db.commit()


@router.get("/{saree_id}/image")
def saree_image(saree_id: str, db: Session = Depends(get_db)):
    s = get_saree(db, saree_id)
    return FileResponse(storage.resolve(s.image_file), media_type="image/jpeg",
                        headers={"Cache-Control": "private, max-age=300"})


def _font(size: int):
    for name in ("DejaVuSans-Bold.ttf", "Arial Bold.ttf", "arialbd.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


@router.get("/{saree_id}/label.png")
def barcode_label(saree_id: str, db: Session = Depends(get_db)):
    """Printable Code-128 tag (about 50x30 mm at 203 dpi) for sarees that have no barcode yet."""
    import barcode
    from barcode.writer import ImageWriter

    s = get_saree(db, saree_id)
    buf = io.BytesIO()
    barcode.get("code128", s.barcode, writer=ImageWriter()).write(
        buf, options={"module_height": 9, "font_size": 7, "text_distance": 3, "quiet_zone": 2, "dpi": 203}
    )
    code_img = Image.open(io.BytesIO(buf.getvalue())).convert("RGB")
    W = max(400, code_img.width + 20)
    label = Image.new("RGB", (W, code_img.height + 70), "white")
    d = ImageDraw.Draw(label)
    d.text((10, 8), s.name[:34], fill="black", font=_font(20))
    price = f"Rs {s.price:,}" if s.price else ""
    d.text((10, 34), " · ".join(x for x in (s.fabric, price) if x)[:40], fill="black", font=_font(16))
    label.paste(code_img, ((W - code_img.width) // 2, 62))
    out = io.BytesIO()
    label.save(out, "PNG")
    return Response(out.getvalue(), media_type="image/png")
