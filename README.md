# Pallu: AI saree try-on for stores

Customers pick sarees off the rack, staff scan the tags, and the customer stands in front of a mirror kiosk. One photo later, they see themselves wearing every saree they picked, without anyone draping heavy silk over and over.

Pallu works from **a single photo of each folded saree**. When a saree is added to the catalogue, an AI vision model labels its parts (body, border, pallu, zari, blouse piece). That description goes into the try-on prompt, so the generated image puts the pallu over the shoulder and the border along the hem the way the real saree would fall.

```
 Staff phone (Android)            Pallu server (FastAPI)                 Mirror kiosk (Android tablet)
 ─────────────────────            ───────────────────────                ─────────────────────────────
 scan saree barcodes  ──POST /api/sessions──▶  session + QR code
 show QR to customer                                                     scan QR  ──GET /api/session-lookup
                                                                          consent + drape style + 1 photo
                                  queue one job per saree   ◀──POST /photo──┘
                                  Gemini image model ──▶ looks           swipe through looks as they arrive
 see shortlist live   ◀──GET /api/sessions/{id}──  favourites  ◀──POST /favorite
                                  photo + looks deleted     ◀──POST /finish (or auto after 30 min)
 Admin page (/admin): add sarees from a photo, print barcode tags, today's numbers
```

## What's in this repo

| Folder | What it is |
| --- | --- |
| `backend/` | Python FastAPI server: catalogue, QR sessions, try-on queue, Gemini + offline mock engines, privacy auto-delete, admin web page, tests |
| `android/` | Kotlin + Jetpack Compose app with two modes: **Staff phone** (barcode scanner → QR) and **Try-on mirror** (kiosk) |
| `docker-compose.yml` | One-command server deployment |

## Quick start

### 1. Run the server (Windows, macOS or Linux)

```bash
cd backend
python -m venv .venv
# Windows: .venv\Scripts\activate     macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
copy .env.example .env        # macOS/Linux: cp .env.example .env
```

Edit `.env` and set at least `API_KEY` to a long random string. Leave `ENGINE=mock` for a free offline demo. Then start it:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

- Admin page: http://localhost:8000/admin (sign in with your `API_KEY`)
- API docs: http://localhost:8000/docs

Load 8 demo sarees (barcodes `DEMO001` … `DEMO008`):

```bash
python scripts/seed_demo.py --url http://localhost:8000 --key YOUR_API_KEY
```

### 2. Turn on real AI try-on (Gemini)

1. Get an API key from [Google AI Studio](https://aistudio.google.com/apikey).
2. In `backend/.env` set `ENGINE=gemini` and `GEMINI_API_KEY=...`.
3. Turn on billing for the key's Google Cloud project. Image models have no free tier.
4. Restart the server. It checks the key for free and the admin page shows the result: a green dot means Google accepted the key. Sarees added in demo mode are re-read automatically ("Re-reading N saree(s)" in the server window).

If Google renames the model or endpoint, the server tries the other API style and then `gemini-3.1-flash-image` by itself, and logs which one worked. The server window and the staff app explain the common errors in plain words, such as a wrong key or billing not switched on.

**Google billing won't go through? Use OpenRouter instead.** OpenRouter resells the same Google image model (Nano Banana 2.1) on a prepaid balance you top up once with a card.
1. Sign up at [openrouter.ai](https://openrouter.ai), go to **Credits**, and add $5–10.
2. Under **Keys**, create a key.
3. In `backend/.env` set `ENGINE=openrouter` and `OPENROUTER_API_KEY=...`, then restart the server. The admin page shows whether the key was accepted and how much credit is left.

Model settings live in `.env`:

| Setting | Default | Notes |
| --- | --- | --- |
| `GEMINI_IMAGE_MODEL` | `gemini-nano-banana-2.1` | The image model that drapes the saree. `gemini-3-pro-image` is higher quality and slower; `gemini-2.5-flash-image` is the older model. |
| `GEMINI_API_STYLE` | `interactions` | Google's current Interactions API, tried first. `generate_content` is the classic API (tried automatically if the first fails). |
| `GEMINI_TEXT_MODEL` | `gemini-flash-latest` | Reads the folded saree photo into body / border / pallu parts. |
| `IMAGE_SIZE` | `1K` | `2K` looks better on a big mirror and costs more. |
| `MAX_CONCURRENT_GENERATIONS` | `3` | How many sarees are draped in parallel. |

Each try-on is one image-generation call, plus one cheap text call per saree when it's added. Check current prices on Google's pricing page before you set the image size.

### 3. Get the Android app

**Easiest: download the APK GitHub builds for you.** Every push to `main` builds the app automatically.
On GitHub open the repo, then **Actions**, the latest green **Build** run, and download **pallu-debug-apk** under *Artifacts*.
Unzip it and copy `app-debug.apk` to the phone, tablet or kiosk. To install it, allow "Install unknown apps" for your file manager.

**Or build it yourself:** open the `android/` folder in **Android Studio** (Ladybug or newer) and press Run.

On first launch, enter the server address (for example `192.168.1.20:8000`, the PC's address on the shop Wi-Fi) and the store key, then choose **Staff phone** or **Try-on mirror**.

### 4. Setting up the mirror (tablet or big kiosk)

- **Any Android 8+ screen works.** That includes the big vertical 43"–65" touch kiosks, which mostly run Android inside. Layouts scale to the screen, and the AI runs on Google's servers, so a basic kiosk board is fast enough. Google Play is not needed.
- **Camera:** a tablet's front camera works. On a big kiosk, use a 1080p USB webcam at roughly chest height; the app finds USB cameras automatically. The customer stands about 2–2.5 m back so their whole body fits the outline.
- **Tablet on a stand (for example a Galaxy Tab S6 Lite):** mount it **upright (portrait)** so the front camera sits at the top centre, with the camera at about chest height (120–130 cm). The customer stands 1.5–2 m back. Face a window or a soft light, not with a window behind the customer. The countdown beeps each second so customers don't need to read the screen from a distance. Turn the media volume up.
- **Lock it down:** when you choose *Try-on mirror*, Pallu can become the device's home screen. Press the Home button, pick **Pallu** and choose **Always**. The kiosk then boots straight into the mirror, and Home and Back can't take customers out.
- **Leaving mirror mode (staff):** press and hold the store name on the start screen and enter the store key.
- **Windows kiosks** can't run the Android app.

## The store flow

1. **Add sarees** on the admin page: one photo of the folded saree with the pallu and border visible, plus a name and price. No barcode on the saree? Leave the field empty and click **Print tag**.
2. **Staff phone**: tap *Scan a new customer's sarees*, scan each tag, then *Create QR code*.
3. **Mirror**: the customer shows the QR (or types the 6-letter code), chooses *Me* or *Someone else*, picks a drape style (Nivi, Bengali, Gujarati, Nauvari, Modern) and agrees to the photo.
4. **Me:** a 4-second countdown takes one full-length photo.
   **Someone else (gift):** the mirror asks the customer to WhatsApp a photo of the person to the staff member. The staff member taps **Add photo** on that customer's QR screen and confirms permission, and the looks start on the mirror automatically.
5. The looks appear one by one. Customers swipe, press and hold to compare with the original photo, and tap **Shortlist** on the ones they like.
   - **Change** (top corner of a look) makes a new look of the same saree in another drape, next to the original, so the two can be compared.
   - **Try more sarees** opens the store's collection; the picked sarees are draped with the same photo, no new QR or photo needed.
6. **I'm done** first asks *All done?* (with a last chance to try more), then deletes the photo and every generated image right away. The shortlist stays, so staff see it live on their phone and on the admin page.

Staff can also scan more sarees into a session while the customer is at the mirror (open the customer on the staff phone, then **Scan more**). They're draped straight away.

## Privacy

This was the main concern raised about Cloakroom, so it's built in:

- No photo is processed without the customer ticking the consent box. The server rejects uploads without it.
- Photos are re-encoded on upload, which strips EXIF data including GPS location.
- The customer photo and all generated looks are deleted when the customer taps *I'm done*, when the mirror is left idle (90 s), or automatically after `PHOTO_TTL_MINUTES` (30 by default), whichever comes first.
- Only the shortlist (saree IDs) is kept, for the staff and the store's stats.
- Photos are sent to Google's Gemini API to generate the image. Google's terms treat free-tier and paid-tier data differently (free-tier inputs may be used to improve their products), so use a paid key in a real store, re-check the current terms, and tell customers which provider is used.

## API overview

All endpoints except `/api/health` and `/api/drape-styles` need the `X-API-Key` header (or `?key=` for image URLs).

| Method & path | Used by | Purpose |
| --- | --- | --- |
| `GET /api/sarees`, `POST /api/sarees` | admin | List / add sarees (multipart: image, name, barcode, fabric, color, price, description) |
| `GET /api/sarees/barcode/{code}` | staff | Look up a scanned tag |
| `GET /api/sarees/{id}/label.png` | admin | Printable Code-128 tag |
| `POST /api/sessions` | staff | Create a customer session from barcodes → code + QR payload |
| `POST /api/sessions/{id}/items` | staff | Add a saree to a live session |
| `PATCH /api/sessions/{id}` | kiosk | Save drape style / gift mode before the photo arrives |
| `GET /api/session-lookup?q=` | kiosk | Find a session from the scanned QR or typed code |
| `POST /api/sessions/{id}/photo` | kiosk, staff | Upload photo (needs `consent=true`), start generation; staff use it for gift photos |
| `GET /api/sessions/{id}` | both | Poll status and results |
| `POST /api/sessions/{id}/items/{item}/favorite` · `/retry` | kiosk | Shortlist / regenerate one look |
| `POST /api/sessions/{id}/items/{item}/redrape` | kiosk | New look of the same saree in another drape (`{"drape_style": "bengali"}`) |
| `POST /api/sessions/{id}/finish` | kiosk | Customer finished: delete photos, keep shortlist |
| `GET /api/stats` | admin | Today's customers, try-ons, speed, most-loved sarees |

## Development

**UI walkthrough on a tablet emulator.** The *UI check* workflow starts a demo server, then walks the real app on an emulator sized like a Galaxy Tab S6 Lite, in portrait and landscape, plus a phone for the staff screens. It screenshots every screen into the `ci-shots` branch. The test is `android/app/src/androidTest/.../MirrorWalkthroughTest.kt`.

```bash
cd backend
pip install -r requirements-dev.txt
pytest -q
```

The tests run the full store flow against the mock engine, and check the Gemini request and response handling with a fake HTTP transport.

The engine is a small interface (`backend/app/engines/base.py`). To try a self-hosted model later (for example a fine-tuned open-source VTON model on your own GPU), add a class with `generate()` and `analyze_saree()` and register it in `engines/__init__.py`.

## Ideas for next

- Men's try-on (kurtas, sherwanis) and hairstyles, a common request on the Cloakroom video.
- Send the shortlist to the customer on WhatsApp (with consent).
- Blouse-design choice and jewellery add-ons in the prompt.
- Multi-store support with a store ID per device.

## Credits

Inspired by Sohan Rai's *Cloakroom* saree kiosk demo, and by the mirror-kiosk layout of [Insta-Fit AI Virtual Trial Room](https://github.com/Abhi2790/Insta-Fit-AI-Virtual-Trial-Room). Pallu is an independent project and shares no code with either.

Fonts: [Fraunces](https://fonts.google.com/specimen/Fraunces) (Undercase Type, soft SemiBold for headings), [Rozha One](https://fonts.google.com/specimen/Rozha+One) (Indian Type Foundry) and [Mukta](https://fonts.google.com/specimen/Mukta) (Ek Type), SIL Open Font License; see `android/fonts-license/`.
