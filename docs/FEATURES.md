# Pallu feature checklist

Everything the app does today. A redesign changes how these look, never whether they are there.
The emulator walkthrough (`android/app/src/androidTest/.../MirrorWalkthroughTest.kt`) taps through
the ones marked ✓ on every push.

## Mirror (tablet, kiosk mode)

**Always**
- ✓ Dark theme by default; sun/moon switch in the top corner flips to light, remembered per tablet
- Screen stays on; Android's status and navigation bars are hidden on every mirror screen
- The system Back gesture does nothing (customers use the on-screen buttons)
- Idle timeout: if a customer walks away mid-session, the photo is deleted and the mirror resets
- Long-press the store name on the welcome screen, then type the store key, to leave mirror mode (staff only)
- Portrait and landscape layouts

**Welcome**
- ✓ Store name, headline, a fan of the store's own saree photos
- ✓ QR camera that reads the staff's QR code, with a Front / Back camera switch (remembered)
- ✓ Tap the code boxes to type the 6-letter code on Pallu's own QWERTY keypad (0, 1, I, L, O faded: codes never use them)
- ✓ Friendly errors: wrong code, code already used
- ✓ Privacy line: the photo is only used for these sarees and deleted at the end

**Who and drape**
- ✓ "Namaste, <name>" (skipped for token numbers), the sarees she picked
- ✓ Me, or Someone else (gift)
- ✓ Drape style: Nivi, Bengali, Gujarati, Nauvari, Modern, each with a one-line description
- ✓ Consent checkbox (different wording for gifts), Cancel, Take my photo / Continue

**Photo**
- ✓ Live camera with a body outline, Back, Use front/back camera (remembered)
- ✓ 4-second countdown with beeps, shutter sound, white flash and a ring
- ✓ Review: Retake, Use this photo, sending state
- Gift: choose a photo on the mirror itself

**Gift photo from staff**
- ✓ "Send their photo to our staff": waits, then starts by itself when staff upload the photo
- ✓ Shows the sarees and her code; Cancel; Choose a photo on this screen instead

**Your looks**
- ✓ Looks appear one by one; ready count and progress
- ✓ Swipe between looks; thumbnails to jump; badges for ready and shortlisted
- ✓ Press and hold a look to compare with her original photo
- ✓ Shortlist (heart) per look
- ✓ Draping state: loom animation, the four steps, "Now draping" / "Up next", the drape name
- ✓ Failed look: "Try again"
- ✓ Change drape on a finished look: a new look of the same saree in another drape, next to the original; drapes already made say "See it"
- ✓ Try more sarees: the store's collection, pick several, draped with the same photo ("Already trying" marked; limit 12 sarees)
- Sarees the staff scan in from their phone appear on their own
- ✓ Retake photo
- ✓ I'm done → "All done?": shortlist count, Keep looking, Try more sarees, Yes I'm done

**Thank you**
- ✓ Shortlisted sarees with prices, "staff will bring these out"
- ✓ "Your photo and the looks have been deleted"
- ✓ Start over, with a 20-second countdown

## Staff phone
- ✓ Home: Scan a new customer, today's customers (code, name, status, liked sarees), pull to refresh, AI engine warnings, settings
- ✓ Scan: barcode camera with torch, typed barcode, customer name (camera folds away while typing), list with remove, total price, Create QR code
- ✓ Customer: QR code + 6-letter code, each look's status and drape, liked sarees, Scan more (add sarees mid-session), Add photo (gift), New customer
- ✓ Setup: server address, store key, staff or mirror mode, connection check

## Admin (web page from the server)
- Sign in with the store key
- Today's numbers, AI engine status dot
- Add a saree (photo, name, fabric, price, colour, barcode or auto barcode, notes), AI reads border, pallu and blouse
- Catalogue and Recent customers on separate tabs (the last tab is remembered)
- Catalogue with search and a count, printable barcode labels, remove; two per row on a phone
- Recent customers with their shortlists and start time (date shown for older ones), Show more (15 at a time, up to 100)

## Server
- OpenRouter (default), Gemini or demo engine; startup credit/key check shown on the admin page
- Saree analysis with lenient JSON and retry; re-reads demo-analysed sarees when a real engine is switched on
- Try-on prompt: drape style, gift mode, blouse matching (blouse piece → border colour → complementary), realism lines
- Per-look drape (re-drape endpoint), 12 sarees / 24 looks per customer
- Privacy: photos and looks deleted on finish, on idle, and after PHOTO_TTL_MINUTES; only the shortlist is kept
- Old databases are upgraded automatically on start
