"""File storage for saree photos, customer photos and generated try-ons."""
import io
import shutil
from pathlib import Path

from PIL import Image, ImageOps, UnidentifiedImageError

from .config import get_settings

MAX_SIDE = 1536


class BadImage(ValueError):
    pass


def data_dir() -> Path:
    return get_settings().data_dir


def saree_dir() -> Path:
    p = data_dir() / "sarees"
    p.mkdir(parents=True, exist_ok=True)
    return p


def session_dir(session_id: str) -> Path:
    p = data_dir() / "sessions" / session_id
    p.mkdir(parents=True, exist_ok=True)
    return p


def normalize_image(raw: bytes, max_side: int = MAX_SIDE, quality: int = 90) -> bytes:
    """Fix EXIF rotation, strip metadata (incl. GPS), downscale and re-encode as JPEG."""
    if not raw:
        raise BadImage("Empty file")
    try:
        img = Image.open(io.BytesIO(raw))
        img.load()
    except (UnidentifiedImageError, OSError) as e:
        raise BadImage("Not a valid image") from e
    img = ImageOps.exif_transpose(img)
    if img.mode not in ("RGB", "L"):
        bg = Image.new("RGB", img.size, (255, 255, 255))
        if img.mode in ("RGBA", "LA", "P"):
            img = img.convert("RGBA")
            bg.paste(img, mask=img.split()[-1])
            img = bg
        else:
            img = img.convert("RGB")
    elif img.mode == "L":
        img = img.convert("RGB")
    img.thumbnail((max_side, max_side), Image.LANCZOS)
    out = io.BytesIO()
    img.save(out, "JPEG", quality=quality, optimize=True)  # no exif= → metadata dropped
    return out.getvalue()


def write_bytes(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_bytes(data)
    tmp.replace(path)


def resolve(rel: str) -> Path:
    """Stored paths are relative to data_dir; refuse anything that escapes it."""
    base = data_dir().resolve()
    p = (base / rel).resolve()
    if base not in p.parents and p != base:
        raise ValueError("Path escapes data dir")
    return p


def rel(path: Path) -> str:
    return path.resolve().relative_to(data_dir().resolve()).as_posix()


def delete_rel(rel_path: str | None) -> None:
    if not rel_path:
        return
    try:
        resolve(rel_path).unlink(missing_ok=True)
    except ValueError:
        pass


def delete_session_files(session_id: str) -> None:
    p = data_dir() / "sessions" / session_id
    if p.exists():
        shutil.rmtree(p, ignore_errors=True)
