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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.ui.common.BarcodeScannerView
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.ScanKind
import com.pallu.tryon.ui.common.rememberCameraPermission
import com.pallu.tryon.ui.theme.MirrorRaised
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

    KioskPage {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val k = headlineSize(maxWidth)
            val wide = maxWidth > maxHeight
            // Phones (and very narrow tablets): one scrolling column with everything sized down.
            val compact = !wide && maxWidth < 600.dp
            val roomForFan = maxHeight > 640.dp
            val side = if (wide) 72.dp else 64.dp * k.coerceAtLeast(0.6f)

            if (compact) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    StoreName(storeName) { askExit = true }
                    Headline(0.5f)
                    if (roomForFan) SareeFan(state.showcase, Modifier.fillMaxWidth().height(190.dp))
                    StartCard(
                        vm, hasCamera, code, onSession, scanFront, onScanFront,
                        onOpenKeypad = { keypad = true }, vertical = true, compact = true, modifier = Modifier.fillMaxWidth(),
                    )
                    ErrorNote(state.error)
                    PrivacyLine()
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(start = side, end = side, top = 56.dp * k, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp * k),
                ) {
                    StoreName(storeName) { askExit = true }
                    if (wide) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Headline(k * 0.72f) // landscape is short: leave room for the saree fan
                                SareeFan(state.showcase, Modifier.fillMaxWidth().weight(1f))
                            }
                            StartCard(
                                vm = vm, hasCamera = hasCamera, code = code,
                                onSession = onSession, scanFront = scanFront, onScanFront = onScanFront, onOpenKeypad = { keypad = true }, vertical = true,
                                // fixed width: six code boxes + Start must always fit
                                modifier = Modifier.width(400.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                    } else {
                        Headline(k)
                        // takes whatever height is left, so the card, errors and privacy line always fit below
                        SareeFan(state.showcase, Modifier.fillMaxWidth().weight(1f))
                        StartCard(
                            vm, hasCamera, code, onSession, scanFront, onScanFront,
                            onOpenKeypad = { keypad = true }, vertical = false, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ErrorNote(state.error)
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

/** Store name; pressing and holding it is the hidden staff exit from mirror mode. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreName(storeName: String, onLongPress: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        SareeMark(Modifier.size(30.dp))
        Spacer(Modifier.size(12.dp))
        Text(
            storeName.ifBlank { "Pallu" },
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            color = ZariLight,
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
        Icon(Icons.Filled.Lock, contentDescription = null, tint = OnMirrorFaint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            "Your photo is only used to show you these sarees, and is deleted when you finish.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnMirrorFaint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Headline(k: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "See yourself in every saree you picked.",
            fontFamily = Rozha,
            fontSize = (88 * k).sp,
            lineHeight = (90 * k).sp,
            color = OnMirror,
            modifier = Modifier.widthIn(max = 660.dp),
        )
        Muted("One photo, no draping. Each saree appears on you in under a minute.", scale = k, modifier = Modifier.widthIn(max = 580.dp))
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
                    color = OnMirrorFaint, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                )
            }
            if (state.lookingUp) CircularProgressIndicator(color = ZariLight, modifier = Modifier.align(Alignment.Center))
        }
    }
    val words: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Hold up the QR code our staff gave you",
                fontFamily = Rozha,
                fontSize = if (compact) 24.sp else 32.sp,
                lineHeight = if (compact) 29.sp else 38.sp,
                color = OnMirror,
            )
            Muted("Or tap the boxes to type the 6-letter code", scale = 0.82f)
            CodeBoxes(code, onClick = onOpenKeypad, boxSize = if (compact) 38.dp else 44.dp)
            GoldButton(
                if (state.lookingUp) "Finding your sarees…" else "Start",
                onClick = { if (code.length == 6) vm.lookup(code, onSession) else onOpenKeypad() },
                enabled = !state.lookingUp,
                modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
            )
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MirrorRaised)
            .border(1.dp, ZariLight.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
            .padding(if (compact) 18.dp else 26.dp),
    ) {
        val switch: @Composable () -> Unit = {
            if (hasCamera) CameraSwitch(front = scanFront, onChange = onScanFront)
        }
        if (vertical) {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // capped so the camera window never pushes the code boxes off a phone screen
                scanner(Modifier.widthIn(max = if (compact) 260.dp else 170.dp).fillMaxWidth(if (compact) 0.8f else 1f).aspectRatio(1f))
                switch()
                words(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    scanner(Modifier.size(236.dp))
                    switch()
                }
                words(Modifier.weight(1f))
            }
        }
    }
}
