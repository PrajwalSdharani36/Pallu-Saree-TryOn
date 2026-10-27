package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.ui.common.BarcodeScannerView
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.ScanKind
import com.pallu.tryon.ui.common.rememberCameraPermission
import com.pallu.tryon.ui.theme.Glass
import com.pallu.tryon.ui.theme.GlassLine
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.OnMirrorFaint
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.SareeMark
import com.pallu.tryon.ui.theme.ZariLight

/** Idle screen of the mirror: waits for the customer's QR code. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttractScreen(
    vm: KioskViewModel,
    storeName: String,
    apiKey: String,
    scanFront: Boolean,
    onScanFront: (Boolean) -> Unit,
    onSession: (alreadyHasPhoto: Boolean) -> Unit,
    onExitKiosk: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val hasCamera = rememberCameraPermission()
    var code by remember { mutableStateOf("") }
    var askExit by remember { mutableStateOf(false) }
    // The mirror's own keypad for typing the code (never the Android keyboard).
    var keypad by remember { mutableStateOf(false) }

    KioskPage(ambient = state.showcase.firstOrNull()?.imageUrl) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val k = headlineSize(maxWidth)
            val wide = maxWidth > maxHeight
            // Phones (and very narrow tablets): one scrolling column with everything sized down.
            val compact = !wide && maxWidth < 600.dp
            val roomForFan = maxHeight > 640.dp

            when {
                compact -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    StoreName(storeName) { askExit = true }
                    Headline(0.55f, center = false)
                    if (roomForFan) SareeFan(state.showcase, Modifier.fillMaxWidth().height(200.dp))
                    StartCard(
                        vm, hasCamera, code, onSession, scanFront, onScanFront,
                        onOpenKeypad = { keypad = true }, vertical = true, compact = true, modifier = Modifier.fillMaxWidth(),
                    )
                    ErrorNote(state.error)
                    PrivacyLine()
                }

                wide -> Row(
                    Modifier.fillMaxSize().padding(start = KioskSide, end = KioskSide, bottom = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                ) {
                    Column(Modifier.weight(1f).padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        StoreName(storeName) { askExit = true }
                        Headline(0.78f, center = false)
                        Steps()
                        SareeFan(state.showcase, Modifier.fillMaxWidth().weight(1f))
                        ErrorNote(state.error)
                        PrivacyLine()
                    }
                    // below the light/dark switch; scrolls if a small screen can't fit it all
                    StartCard(
                        vm = vm, hasCamera = hasCamera, code = code,
                        onSession = onSession, scanFront = scanFront, onScanFront = onScanFront, onOpenKeypad = { keypad = true },
                        vertical = true,
                        modifier = Modifier.padding(top = 108.dp).width(460.dp).verticalScroll(rememberScrollState()),
                    )
                }

                else -> Column(
                    Modifier.fillMaxSize().padding(start = KioskSide, end = KioskSide, bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.fillMaxWidth().padding(top = 30.dp, end = ThemeSwitchRoom - KioskSide)) {
                        StoreName(storeName) { askExit = true }
                    }
                    Spacer(Modifier.height(24.dp))
                    // takes whatever height is left, so the card, errors and privacy line always fit below
                    SareeFan(state.showcase, Modifier.fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(30.dp))
                    Headline(k, center = true)
                    Spacer(Modifier.height(28.dp))
                    Steps()
                    Spacer(Modifier.height(32.dp))
                    StartCard(
                        vm, hasCamera, code, onSession, scanFront, onScanFront,
                        onOpenKeypad = { keypad = true }, vertical = false, modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.error != null) {
                        Spacer(Modifier.height(16.dp))
                        ErrorNote(state.error)
                    }
                    Spacer(Modifier.height(22.dp))
                    PrivacyLine()
                }
            }
            if (keypad) {
                CodeKeypad(
                    code = code,
                    onCode = { code = it },
                    onStart = { keypad = false; vm.lookup(code, onSession) },
                    onClose = { keypad = false },
                    busy = state.lookingUp,
                )
            }
        }
    }

    if (askExit) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text("Staff only") },
            text = {
                Column {
                    Text("Enter the store key to leave mirror mode.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = typed, onValueChange = { typed = it }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { if (typed == apiKey) { askExit = false; onExitKiosk() } },
                    enabled = typed.isNotEmpty(),
                ) { Text("Leave mirror mode") }
            },
            dismissButton = { TextButton(onClick = { askExit = false }) { Text("Cancel") } },
        )
    }
}

/** Store name in a glass pill; pressing and holding it is the hidden staff exit from mirror mode. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreName(storeName: String, onLongPress: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Glass)
            .border(1.dp, GlassLine, RoundedCornerShape(50))
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .heightIn(min = 60.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        SareeMark(Modifier.size(28.dp))
        Spacer(Modifier.size(14.dp))
        Text(
            storeName.ifBlank { "Pallu" },
            style = MaterialTheme.typography.titleLarge,
            color = OnMirror,
        )
    }
}

@Composable
private fun PrivacyLine() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = OnMirrorFaint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(
            "Your photo is only used to show you these sarees, and is deleted when you finish.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnMirrorFaint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Headline(k: Float, center: Boolean) {
    val accent = ZariLight
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            buildAnnotatedString {
                append("Every saree you picked, ")
                withStyle(SpanStyle(color = accent)) { append("on you.") }
            },
            fontFamily = Rozha,
            fontSize = (68 * k).sp,
            lineHeight = (76 * k).sp,
            color = OnMirror,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.widthIn(max = 680.dp),
        )
        Muted(
            "One photo, no changing room. Each look is ready in under a minute.",
            scale = k.coerceAtLeast(0.85f),
            align = if (center) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.widthIn(max = 620.dp),
        )
    }
}

/** The three steps, so a first-time customer knows what's coming. */
@Composable
private fun Steps() {
    Row(horizontalArrangement = Arrangement.spacedBy(34.dp), verticalAlignment = Alignment.CenterVertically) {
        StepChip(1, "Show your code")
        StepChip(2, "One photo")
        StepChip(3, "See them on you")
    }
}

/** The "start here" card: QR camera window plus the typed-code fallback. */
@Composable
private fun StartCard(
    vm: KioskViewModel,
    hasCamera: Boolean,
    code: String,
    onSession: (Boolean) -> Unit,
    scanFront: Boolean,
    onScanFront: (Boolean) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onOpenKeypad: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scanner: @Composable (Modifier) -> Unit = { m ->
        ScanWindow(m) {
            if (hasCamera) {
                BarcodeScannerView(Modifier.fillMaxSize(), ScanKind.QR, useFrontCamera = scanFront) { c -> vm.lookup(c, onSession) }
            } else {
                Text(
                    "Camera is off. Type your code instead.",
                    color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                )
            }
            if (state.lookingUp) CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
    val words: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Show the QR code from our staff",
                fontFamily = Rozha,
                fontSize = if (compact) 26.sp else 36.sp,
                lineHeight = if (compact) 32.sp else 43.sp,
                color = OnMirror,
            )
            Muted("Or tap the boxes to type the 6-letter code", scale = 0.86f)
            CodeBoxes(code, onClick = onOpenKeypad, boxSize = if (compact) 40.dp else 52.dp)
            Spacer(Modifier.height(2.dp))
            GoldButton(
                if (state.lookingUp) "Finding your sarees…" else "Start",
                onClick = { if (code.length == 6) vm.lookup(code, onSession) else onOpenKeypad() },
                enabled = !state.lookingUp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    GlassPanel(modifier, padding = PaddingValues(if (compact) 20.dp else 30.dp)) {
        val switch: @Composable () -> Unit = {
            if (hasCamera) CameraSwitch(front = scanFront, onChange = onScanFront)
        }
        if (vertical) {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // capped so the camera window never pushes the code boxes off the screen
                scanner(Modifier.widthIn(max = if (compact) 260.dp else 210.dp).fillMaxWidth(if (compact) 0.8f else 1f).aspectRatio(1f))
                switch()
                words(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    scanner(Modifier.size(224.dp))
                    switch()
                }
                words(Modifier.weight(1f))
            }
        }
    }
}
