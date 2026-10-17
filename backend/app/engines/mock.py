"""Offline engine for demos, development and tests (no API key, no cost).

It does NOT do real try-on. It composites the saree's texture onto a rough drape
silhouette over the customer photo, so the whole store flow can be exercised
end to end on a laptop.
"""
import asyncio
import io

from PIL import Image, ImageDraw, ImageFilter, ImageOps

from .base import TryOnRequest

W, H = 768, 1024


def dominant_colors(img: Image.Image, n: int = 3) -> list[str]:
    small = img.convert("RGB").resize((64, 64))
    q = small.quantize(colors=n, method=Image.Quantize.MEDIANCUT)
    pal = q.getpalette()[: n * 3]
    counts = sorted(q.getcolors(), reverse=True)
    out = []
    for _, idx in counts:
        r, g, b = pal[idx * 3 : idx * 3 + 3]
        out.append(f"#{r:02x}{g:02x}{b:02x}")
    return out


class MockEngine:
    name = "mock"

    def __init__(self, delay_s: float = 1.5):
        self.delay_s = delay_s

    async def generate(self, req: TryOnRequest) -> bytes:
        await asyncio.sleep(self.delay_s)
        return await asyncio.to_thread(self._compose, req)

    def _compose(self, req: TryOnRequest) -> bytes:
        person = ImageOps.fit(Image.open(io.BytesIO(req.person_jpeg)).convert("RGB"), (W, H))
        saree = Image.open(io.BytesIO(req.saree_jpeg)).convert("RGB")

        # tile the saree photo as a texture
        tile = ImageOps.fit(saree, (256, 256))
        texture = Image.new("RGB", (W, H))
        for x in range(0, W, 256):
            for y in range(0, H, 256):
                texture.paste(tile, (x, y))

        # rough Nivi silhouette: skirt + diagonal pallu over left shoulder
        mask = Image.new("L", (W, H), 0)
        d = ImageDraw.Draw(mask)
        d.polygon([(W * 0.33, H * 0.45), (W * 0.67, H * 0.45), (W * 0.78, H * 0.97), (W * 0.22, H * 0.97)], fill=230)
        d.polygon([(W * 0.62, H * 0.24), (W * 0.72, H * 0.27), (W * 0.42, H * 0.50), (W * 0.33, H * 0.46)], fill=230)
        mask = mask.filter(ImageFilter.GaussianBlur(6))
        out = Image.composite(texture, person, mask)

        d = ImageDraw.Draw(out)
        d.rectangle([0, H - 44, W, H], fill=(20, 12, 16))
        d.text((16, H - 32), f"PREVIEW (mock engine) - {req.saree.name[:40]}", fill=(232, 200, 120))
        buf = io.BytesIO()
        out.save(buf, "JPEG", quality=88)
        return buf.getvalue()

    async def analyze_saree(self, saree_jpeg: bytes) -> dict:
        img = Image.open(io.BytesIO(saree_jpeg))
        w, h = img.size
        body = dominant_colors(img.crop((w * 0.2, h * 0.2, w * 0.8, h * 0.8)), 2)
        edge = dominant_colors(img.crop((0, h * 0.85, w, h)), 1)
        return {
            "body": {"color": body[0], "pattern": "from photo", "motifs": ""},
            "border": {"color": edge[0], "width": "medium", "pattern": "from photo"},
            "pallu": {"color": body[-1], "pattern": "not visible", "description": ""},
            "zari": {"present": False, "color": "none", "where": ""},
            "blouse_piece": {"visible": False, "color": "", "pattern": ""},
            "fabric_guess": "",
            "summary": "Colours estimated offline by the mock engine.",
            "_engine": "mock",
        }

    async def aclose(self) -> None:
        return None
