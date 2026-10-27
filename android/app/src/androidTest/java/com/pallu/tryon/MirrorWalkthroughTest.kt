package com.pallu.tryon

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.pallu.tryon.data.AppMode
import com.pallu.tryon.data.AppSettings
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Walks through the real app, end to end, against a running Pallu server (demo engine) and saves
 * a screenshot of every screen. CI runs it on an emulator shaped like a Galaxy Tab S6 Lite
 * (1200 x 2000 px, 240 dpi) so the mirror's layout can be checked without the tablet in hand;
 * see android/scripts/ui-check.sh.
 *
 * Instrumentation args: server (default http://10.0.2.2:8000, the host machine) and key.
 */
@RunWith(AndroidJUnit4::class)
class MirrorWalkthroughTest {
    @get:Rule
    val camera: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instr)
    private val context get() = instr.targetContext
    private val app get() = context.applicationContext as PalluApp
    private val args = InstrumentationRegistry.getArguments()
    private val server = (args.getString("server") ?: "http://10.0.2.2:8000").trimEnd('/')
    private val key = args.getString("key") ?: "ci-key"
    private val shots = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
    private var prefix = ""
    private var light = false

    // ------------------------------------------------------------------ tests

    @Test
    fun tabletPortrait() = walk("tab-portrait_") {
        device.setOrientationNatural()
        mirrorVisit()
        giftVisit()
        setupScreen()
    }

    @Test
    fun tabletLight() = walk("tab-light_") {
        device.setOrientationNatural()
        light = true
        mirrorVisit()
        // the corner switch flips the mirror back to dark straight away
        find(By.desc("Dark theme")).click()
        shot("15_switched_to_dark", settle = 1500)
        light = false
    }

    @Test
    fun tabletLandscape() = walk("tab-landscape_") {
        device.setOrientationLeft()
        mirrorVisit()
        giftVisit()
    }

    @Test
    fun phoneStaff() = walk("phone_") {
        device.setOrientationNatural() // the CI script switches the screen to phone size first
        staffVisit()
        setupScreen()
    }

    // ------------------------------------------------------------------ flows

    private fun mirrorVisit() {
        configure(AppMode.KIOSK)
        val s = newSession("Meera", "DEMO001", "DEMO002", "DEMO004", "DEMO006")
        launch()
        find(By.textContains("Show the QR code"), 20_000)
        shot("01_welcome", settle = 3500)

        // tapping the code boxes opens the mirror's own keypad
        find(By.desc("Type the 6-letter code")).click()
        find(By.text("Type the 6-letter code"))
        shot("01b_keypad", settle = 800)
        tapLast("Close")
        SystemClock.sleep(600)

        // a mistyped code shows a friendly error
        typeCode("ZZZZZZ")
        tapLast("Start")
        find(By.textContains("couldn't find that code"))
        shot("02_wrong_code")

        typeCode(s.code)
        shot("03_code_typed", settle = 500)
        tapLast("Start")

        find(By.textContains("Namaste"))
        shot("04_consent")
        clickRetry(By.textContains("Stand in front of the mirror"))
        clickRetry(By.textContains("I agree to a photo"))
        shot("05_consent_ticked")
        find(By.text("Take my photo")).click()

        find(By.textContains("Take my photo in"), 20_000)
        shot("06_camera", settle = 3000)
        find(By.textContains("Take my photo in")).click()
        SystemClock.sleep(1200)
        shot("07_countdown", settle = 0)
        find(By.textContains("Use this photo"), 25_000)
        shot("08_review")
        find(By.textContains("Use this photo")).click()

        find(By.text("Your looks"), 20_000)
        shot("09_draping", settle = 2500)
        findLook(60_000)
        shot("10_first_look")
        find(By.textStartsWith("All 4 are ready"), 90_000)
        tapShortlist()
        shot("11_shortlisted")
        swipePager()
        shot("12_next_look")
        holdToCompare("13_compare_original")

        // same saree, another drape: one tap on the drape row makes a new look right next to it
        clickRetry(By.text("Bengali"))
        find(By.text("Bengali drape"), 20_000)
        shot("13c_new_drape", settle = 1500)

        // one more saree from the collection, with the same photo
        tapText("Try more sarees")
        find(By.textContains("Pick from our collection"), 15_000)
        tapText("Ivory Kasavu, Kerala")
        shot("13d_try_more", settle = 1500)
        find(By.text("Try this saree")).click()
        find(By.textStartsWith("All 6 are ready"), 120_000)
        shot("13e_six_looks", settle = 1500)
        tapShortlist()

        // finishing asks first, because it deletes the photo
        tapText("I'm done")
        find(By.text("All done?"))
        shot("13f_done_check", settle = 800)
        find(By.text("Yes, I'm done")).click()
        find(By.text("Thank you"), 20_000)
        shot("14_thank_you", settle = 2500)
        find(By.textStartsWith("Start over")).click()
        find(By.textContains("Show the QR code"), 20_000)
    }

    private fun giftVisit() {
        configure(AppMode.KIOSK)
        val s = newSession("Token 12", "DEMO003", "DEMO005")
        launch()
        find(By.textContains("Show the QR code"), 20_000)
        typeCode(s.code)
        tapLast("Start")
        find(By.textContains("Send our staff a photo")).click()
        find(By.text("Bengali")).click()
        find(By.textContains("permission to use their photo")).click()
        shot("20_gift_consent")
        find(By.text("Continue")).click()
        find(By.textContains("Send their photo to our staff"), 15_000)
        shot("21_gift_waiting", settle = 3000)

        uploadGiftPhoto(s.id) // what the staff phone does when the customer WhatsApps the photo
        find(By.text("Your looks"), 30_000)
        findLook(60_000)
        shot("22_gift_look")
        find(By.text("I'm done")).click()
        find(By.text("Yes, I'm done")).click()
        find(By.text("Thank you"), 20_000)
        shot("23_gift_thanks", settle = 1500)
    }

    private fun staffVisit() {
        configure(AppMode.STAFF)
        val s = newSession("Anita", "DEMO007", "DEMO008", "DEMO001")
        launch()
        find(By.textContains("Scan a new customer"), 20_000)
        shot("30_staff_home", settle = 2500)
        find(By.textContains("Scan a new customer")).click()
        find(By.textContains("Scan saree tags"), 15_000)
        shot("31_staff_scan", settle = 3000)
        // typing the customer's name folds the camera away so the field stays in view
        find(By.textContains("Customer name")).click()
        SystemClock.sleep(800)
        device.findObject(By.focused(true))?.text = "Saifu"
        shot("31b_staff_typing_name", settle = 1500)
        find(By.desc("Back")).click()
        find(By.textContains("Anita"), 15_000).click()
        find(By.textContains(s.code.take(3)), 15_000)
        shot("32_staff_session", settle = 2500)
        device.pressBack()
    }

    private fun setupScreen() {
        runBlocking { app.container.settingsStore.save(AppSettings()) }
        launch()
        SystemClock.sleep(2500)
        shot("90_setup")
    }

    // ------------------------------------------------------------------ app + server helpers

    private data class NewSession(val id: String, val code: String)

    private fun configure(mode: AppMode) = runBlocking {
        app.container.settingsStore.save(
            AppSettings(serverUrl = server, apiKey = key, mode = mode, storeName = "Lakshmi Silks", kioskIdleSeconds = 900),
        )
        app.container.settingsStore.setKioskLight(light)
    }

    private fun launch() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 15_000)
    }

    private fun newSession(label: String, vararg barcodes: String): NewSession {
        val body = JSONObject().put("customer_label", label).put("barcodes", org.json.JSONArray(barcodes.toList()))
        val r = http("POST", "/api/sessions", body.toString().toByteArray(), "application/json")
        return NewSession(r.getString("id"), r.getString("code"))
    }

    private fun uploadGiftPhoto(sessionId: String) {
        val b = "----pallu${System.nanoTime()}"
        val out = ByteArrayOutputStream()
        fun field(name: String, value: String) =
            out.write("--$b\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
        field("consent", "true")
        field("mode", "gift")
        field("drape_style", "")
        out.write("--$b\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"p.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
        out.write(standingFigureJpeg())
        out.write("\r\n--$b--\r\n".toByteArray())
        http("POST", "/api/sessions/$sessionId/photo", out.toByteArray(), "multipart/form-data; boundary=$b")
    }

    private fun http(method: String, path: String, body: ByteArray?, type: String?): JSONObject {
        val c = URL(server + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        c.setRequestProperty("X-API-Key", key)
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", type)
            c.outputStream.use { it.write(body) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        check(code < 400) { "$method $path -> $code $text" }
        return JSONObject(text)
    }

    /** A plain stand-in for a customer's photo: a figure standing against a wall. */
    private fun standingFigureJpeg(): ByteArray {
        val bmp = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(226, 220, 210))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(176, 128, 98)
        c.drawOval(RectF(390f, 140f, 510f, 290f), p)
        p.color = Color.rgb(70, 74, 92)
        c.drawRoundRect(RectF(330f, 300f, 570f, 1100f), 60f, 60f, p)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    // ------------------------------------------------------------------ device helpers

    /** Waits for something on screen; if it never appears, scrolls the page looking for it. */
    private fun find(sel: BySelector, timeout: Long = 15_000): UiObject2 {
        device.wait(Until.findObject(sel), timeout)?.let { return it }
        repeat(6) {
            // the biggest scrollable is the page; a small one is usually a sideways row of thumbnails
            val list = device.findObjects(By.scrollable(true))
                .maxByOrNull { runCatching { it.visibleBounds.width() * it.visibleBounds.height() }.getOrDefault(0) } ?: return@repeat
            runCatching { list.scroll(Direction.DOWN, 0.7f) }
            SystemClock.sleep(600)
            device.findObject(sel)?.let { return it }
        }
        error("Not on screen after ${timeout / 1000}s: $sel")
    }

    private fun tap(text: String) {
        val o = find(By.text(text))
        device.wait(Until.hasObject(By.text(text).enabled(true)), 5_000)
        o.click()
    }

    /** Types a session code on the mirror's keypad (opening it first, clearing what was there). */
    private fun typeCode(code: String) {
        if (!device.hasObject(By.text("Type the 6-letter code"))) find(By.desc("Type the 6-letter code")).click()
        find(By.text("Type the 6-letter code"))
        repeat(6) { device.findObject(By.desc("Delete"))?.let { runCatching { it.click() } } }
        code.forEach { c -> tapLast(c.toString()) }
        SystemClock.sleep(300)
    }

    /** The keypad sits on top of the welcome card, so when a label appears twice the keypad's is last. */
    private fun tapLast(text: String) {
        find(By.text(text))
        val all = device.findObjects(By.text(text))
        device.wait(Until.hasObject(By.text(text).enabled(true)), 5_000)
        all.last().click()
    }

    /** A finished look shows a Shortlist button (text on wide cards, icon on narrow ones). */
    private fun findLook(timeout: Long) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (device.hasObject(By.textContains("Shortlist")) || device.hasObject(By.desc("Shortlist"))) return
            SystemClock.sleep(500)
        }
        error("No finished look after ${timeout / 1000}s")
    }

    /** Tap the element with exactly this text (UiObject2 taps are correct in landscape too). */
    private fun tapText(text: String) = clickRetry(By.text(text))

    private fun clickRetry(sel: BySelector) {
        repeat(3) { if (runCatching { find(sel).click() }.isSuccess) return else SystemClock.sleep(400) }
        // a node that keeps being redrawn can go stale between finding and clicking: tap where it is
        repeat(4) {
            val c = runCatching { find(sel).visibleCenter }.getOrNull()
            if (c != null) { device.click(c.x, c.y); return }
            SystemClock.sleep(300)
        }
        find(sel).click()
    }

    private fun tapShortlist() {
        repeat(3) { // the pager recomposes while looks arrive, so a found button can go stale
            val ok = runCatching {
                (device.findObject(By.text("Shortlist")) ?: device.findObject(By.desc("Shortlist")))!!.click()
            }.isSuccess
            if (ok) return SystemClock.sleep(900)
            SystemClock.sleep(500)
        }
    }

    private fun swipePager() {
        val w = device.displayWidth
        val h = device.displayHeight
        // left part of the screen: in landscape the right side is the look's panel, not the gallery
        device.swipe((w * 0.55).toInt(), (h * 0.42).toInt(), (w * 0.1).toInt(), (h * 0.42).toInt(), 25)
        SystemClock.sleep(1200)
    }

    private fun holdToCompare(name: String) {
        // the look: centred in portrait, in the left part of the screen in landscape (the panel is on the right)
        val landscape = device.displayWidth > device.displayHeight
        val x = (device.displayWidth * (if (landscape) 0.3 else 0.5)).toInt()
        val y = (device.displayHeight * 0.42).toInt()
        val hold = thread { device.swipe(x, y, x, y, 400) } // ~2 s press in place
        SystemClock.sleep(1000)
        shot(name, settle = 0)
        hold.join()
    }

    private fun shot(name: String, settle: Long = 1500) {
        SystemClock.sleep(settle)
        device.takeScreenshot(File(shots, "$prefix$name.png"))
    }

    /** Runs a flow; on failure saves a screenshot and the screen's UI tree so CI shows where it stopped. */
    private fun walk(prefix: String, flow: () -> Unit) {
        this.prefix = prefix
        try {
            flow()
        } catch (t: Throwable) {
            runCatching { device.takeScreenshot(File(shots, "${prefix}zz_FAILED.png")) }
            runCatching { device.dumpWindowHierarchy(File(shots, "${prefix}zz_FAILED_tree.xml")) }
            runCatching { File(shots, "${prefix}zz_FAILED.txt").writeText(t.stackTraceToString()) }
            throw t
        } finally {
            runCatching { device.setOrientationNatural() }
        }
    }
}
