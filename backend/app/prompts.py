"""Prompt templates for saree analysis and virtual try-on.

The try-on works from ONE photo of a folded saree. The analysis step first labels
its components (body, border, pallu, zari, blouse piece), and that structured
description is fed into the try-on prompt so the model knows which visible
pattern belongs where once the saree is draped.
"""
import json
from dataclasses import dataclass, field

DRAPE_STYLES: dict[str, str] = {
    "nivi": (
        "classic Nivi drape: neat front pleats tucked at the waist, the pallu taken across the chest "
        "and over the LEFT shoulder, falling down the back to about knee length"
    ),
    "bengali": (
        "traditional Bengali (Athpourey) drape: box pleats at the front, the pallu brought over the "
        "LEFT shoulder from back to front and resting on the RIGHT shoulder, wide border clearly visible"
    ),
    "gujarati": (
        "Gujarati Seedha Pallu drape: the pallu brought from behind over the RIGHT shoulder and spread "
        "across the front of the body so its full design is displayed, pleats at the front"
    ),
    "nauvari": (
        "Maharashtrian Nauvari drape: kashta style tucked at the back like a dhoti, the pallu over the "
        "LEFT shoulder, ankle-length, practical and elegant"
    ),
    "modern": (
        "modern pre-pleated drape: slim neat pleats, pallu pinned in narrow pleats over the LEFT shoulder, "
        "contemporary cocktail look"
    ),
}

DEFAULT_DRAPE = "nivi"

ANALYSIS_PROMPT = """You are a textile expert in an Indian saree shop.
The image shows ONE saree, folded or laid flat, photographed in the store.
Identify its parts so a stylist could drape it correctly. Answer ONLY with JSON in this shape:
{
  "body": {"color": "", "pattern": "", "motifs": ""},
  "border": {"color": "", "width": "none|narrow|medium|wide", "pattern": ""},
  "pallu": {"color": "", "pattern": "", "description": ""},
  "zari": {"present": true, "color": "gold|silver|copper|none", "where": ""},
  "blouse_piece": {"visible": false, "color": "", "pattern": ""},
  "fabric_guess": "",
  "weave_or_region_guess": "",
  "sheen": "matte|soft sheen|high sheen",
  "occasion": "",
  "summary": "one sentence a shopper would understand"
}
If a part is not visible in the photo, give your best guess from the visible portion and say "not visible" in pattern.
"""


@dataclass
class SareeInfo:
    name: str
    fabric: str = ""
    color: str = ""
    description: str = ""
    analysis: dict = field(default_factory=dict)


def _describe_analysis(a: dict) -> str:
    if not a:
        return "(no component analysis available; infer the body, border and pallu from the photo)"
    lines = []

    def part(label: str, d: dict | None, keys: tuple[str, ...]) -> None:
        if not isinstance(d, dict):
            return
        bits = [str(d[k]) for k in keys if d.get(k) not in (None, "", "not visible")]
        if bits:
            lines.append(f"- {label}: " + ", ".join(bits))

    part("Body (main drape)", a.get("body"), ("color", "pattern", "motifs"))
    part("Border (runs along the hem and edges)", a.get("border"), ("color", "width", "pattern"))
    part("Pallu (decorative end piece)", a.get("pallu"), ("color", "pattern", "description"))
    z = a.get("zari")
    if isinstance(z, dict) and z.get("present"):
        lines.append(f"- Zari: {z.get('color', '')} {z.get('where', '')}".rstrip())
    bp = a.get("blouse_piece")
    if isinstance(bp, dict) and bp.get("visible"):
        lines.append(f"- Blouse piece: {bp.get('color', '')} {bp.get('pattern', '')}".rstrip())
    for key, label in (("fabric_guess", "Fabric"), ("sheen", "Sheen"), ("weave_or_region_guess", "Weave")):
        if a.get(key):
            lines.append(f"- {label}: {a[key]}")
    return "\n".join(lines) if lines else json.dumps(a)[:800]


def blouse_instruction(a: dict) -> str:
    bp = a.get("blouse_piece") if isinstance(a, dict) else None
    if isinstance(bp, dict) and bp.get("visible") and bp.get("color"):
        return f"a fitted short-sleeve blouse made from the saree's own blouse piece ({bp['color']})"
    border = a.get("border") if isinstance(a, dict) else None
    if isinstance(border, dict) and border.get("color"):
        return f"a simple fitted short-sleeve blouse in a colour matching the border ({border['color']})"
    return "a simple fitted short-sleeve blouse in a colour that complements the saree"


def tryon_prompt(saree: SareeInfo, drape_style: str = DEFAULT_DRAPE, mode: str = "self") -> str:
    drape = DRAPE_STYLES.get(drape_style, DRAPE_STYLES[DEFAULT_DRAPE])
    catalog = ", ".join(x for x in (saree.name, saree.fabric, saree.color) if x)
    extra = f"\nStore description: {saree.description}" if saree.description else ""
    gift_note = (
        "\n- The second photo may be a casual phone photo or only show the upper body. "
        "Extend it naturally to a full-length standing view while keeping the person's identity."
        if mode == "gift"
        else ""
    )
    return f"""Virtual saree try-on for an Indian saree store.

IMAGE 1 is a single saree photographed folded / laid flat on the shop counter.
IMAGE 2 is the customer.

Create ONE photorealistic, full-length photograph of the SAME person from image 2 wearing exactly this saree, draped in the {drape}.

Saree: {catalog}{extra}
Component analysis of the saree:
{_describe_analysis(saree.analysis)}

Rules:
- Identity: keep the person's face, skin tone, hairstyle, body shape, height, weight and age exactly as in image 2. Do not beautify, slim, lighten skin or change facial features.
- Saree fidelity: reproduce the saree's real colours, weave, motifs, zari and border from image 1. The border runs along the bottom hem and the pallu edge; the pallu shows its own distinct design; the pleats show the body fabric. Do not invent new patterns or change colours.
- Fabric behaviour: drape and fall should match the fabric ({saree.fabric or "infer from photo"}), with natural folds, pleats and gravity. Pleats are even and fan out from the waist; the saree reaches the ankles; metallic zari catches the light the way real zari does.
- Blouse: {blouse_instruction(saree.analysis)}. Keep any jewellery the person is already wearing; add nothing else.
- Pose and framing: standing, facing the camera, whole body from head to feet visible, centred, portrait orientation.
- Background: clean, softly lit, warm neutral studio backdrop like a boutique trial room.
- Photography: looks like a real photo taken in the store, soft even light, sharp focus on the fabric texture, natural colours (no filters, no painterly or illustrated look).
- Output only the photograph: no text, logos, watermarks, borders or extra people.{gift_note}
"""
