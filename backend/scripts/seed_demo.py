"""Fill a running Pallu server with demo sarees (procedurally drawn folded sarees).

    python scripts/seed_demo.py --url http://localhost:8000 --key <API_KEY>

Barcodes are DEMO001..DEMO008 so you can type them in the app, or print the tags
from the admin page and scan them.
"""
import argparse
import io
import math
import random
import sys

import httpx
from PIL import Image, ImageDraw

DEMO = [
    ("Maroon Kanjivaram, peacock pallu", "Pure silk", "Maroon and gold", 18500, (122, 18, 40), (196, 150, 52), (16, 90, 70)),
    ("Peacock blue Banarasi", "Katan silk", "Peacock blue", 14200, (14, 74, 110), (205, 160, 70), (120, 20, 60)),
    ("Mustard Chanderi with silver buta", "Silk cotton", "Mustard", 6400, (214, 160, 40), (190, 190, 200), (130, 40, 30)),
    ("Bottle green Paithani", "Silk", "Green and magenta", 22000, (20, 92, 56), (200, 155, 60), (170, 20, 110)),
    ("Ivory Kasavu, Kerala", "Cotton", "Ivory and gold", 3900, (238, 230, 210), (196, 156, 60), (238, 230, 210)),
    ("Rani pink Bandhani", "Georgette", "Rani pink", 5200, (196, 24, 98), (240, 200, 60), (230, 120, 30)),
    ("Indigo Ikat, Pochampally", "Cotton silk", "Indigo", 7800, (36, 40, 96), (210, 70, 60), (36, 40, 96)),
    ("Lavender organza, floral", "Organza", "Lavender", 4600, (180, 160, 210), (220, 200, 160), (150, 110, 190)),
]


def folded_saree(body, zari, pallu, seed: int) -> bytes:
    rnd = random.Random(seed)
    W, H = 900, 1100
    img = Image.new("RGB", (W, H), (236, 232, 226))
    d = ImageDraw.Draw(img)
    # shadow
    d.rectangle([110, 130, 820, 1010], fill=(205, 200, 193))
    # folded body
    x0, y0, x1, y1 = 90, 100, 800, 980
    d.rectangle([x0, y0, x1, y1], fill=body)
    # buta motifs
    for yy in range(y0 + 60, y1 - 260, 70):
        for xx in range(x0 + 50 + (yy // 70 % 2) * 35, x1 - 40, 70):
            r = 9
            d.ellipse([xx - r, yy - r, xx + r, yy + r], fill=zari)
    # pallu panel showing on the top fold
    d.rectangle([x0, y0, x1, y0 + 230], fill=pallu)
    for i in range(12):
        cx = x0 + 30 + i * 60
        d.polygon([(cx, y0 + 200), (cx + 28, y0 + 120), (cx + 56, y0 + 200)], fill=zari)
    d.line([x0, y0 + 230, x1, y0 + 230], fill=zari, width=10)
    # border along the bottom edge and one side
    bw = 110
    d.rectangle([x0, y1 - bw, x1, y1], fill=pallu)
    for i in range(0, x1 - x0, 36):
        d.polygon([(x0 + i, y1 - 18), (x0 + i + 18, y1 - bw + 22), (x0 + i + 36, y1 - 18)], fill=zari)
    d.rectangle([x0, y1 - bw - 8, x1, y1 - bw], fill=zari)
    d.rectangle([x1 - 60, y0, x1, y1], fill=pallu)
    d.rectangle([x1 - 66, y0, x1 - 60, y1], fill=zari)
    # fold lines
    for k in range(1, 4):
        y = y0 + 230 + k * (y1 - bw - y0 - 230) // 4
        d.line([x0, y, x1, y], fill=tuple(max(0, c - 30) for c in body), width=3)
    # soft fabric sheen
    sheen = Image.new("L", (W, H), 0)
    sd = ImageDraw.Draw(sheen)
    for j in range(0, H, 6):
        sd.line([0, j, W, j], fill=int(18 + 14 * math.sin(j / 40 + rnd.random())))
    img = Image.composite(Image.new("RGB", (W, H), (255, 255, 255)), img, sheen.point(lambda v: v // 3))
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=90)
    return buf.getvalue()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://localhost:8000")
    ap.add_argument("--key", required=True)
    args = ap.parse_args()
    with httpx.Client(base_url=args.url, headers={"X-API-Key": args.key}, timeout=60) as c:
        for n, (name, fabric, color, price, body, zari, pallu) in enumerate(DEMO, 1):
            barcode = f"DEMO{n:03d}"
            if c.get(f"/api/sarees/barcode/{barcode}").status_code == 200:
                print(f"skip {barcode} (exists)")
                continue
            r = c.post(
                "/api/sarees",
                data={"name": name, "fabric": fabric, "color": color, "price": str(price), "barcode": barcode},
                files={"image": (f"{barcode}.jpg", folded_saree(body, zari, pallu, n), "image/jpeg")},
            )
            r.raise_for_status()
            print(f"added {barcode}  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
