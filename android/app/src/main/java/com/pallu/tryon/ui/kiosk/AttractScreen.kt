package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onSession: (alreadyHasPhoto: Boolean) -> Unit,
    onExitKiosk: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val hasCamera = rememberCameraPermission()
    var code by remember { mutableStateOf("") }
    var askExit by remember { mutableStateOf(false) }

    KioskPage {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val k = headlineSize(maxWidth)
            val wide = maxWidth > maxHeight
            val narrow = maxWidth < 560.dp
            val side = if (wide) 72.dp else 64.dp * k.coerceAtLeast(0.6f)

            Column(
                Modifier.fillMaxSize().padding(start = side, end = side, top = 56.dp * k, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp * k),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // Hidden staff exit: press and hold the store name.
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { askExit = true }),
                ) {
                    SareeMark(Modifier.size(30.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(
                        storeName.ifBlank { "Pallu" },
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        color = ZariLight,
                    )
                }

                if (wide) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Headline(k)
                            SareeFan(state.showcase, Modifier.fillMaxWidth().weight(1f))
                        }
                        StartCard(
                            vm = vm, hasCamera = hasCamera, code = code, onCode = { code = it },
                            onSession = onSession, vertical = true, modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(0.42f),
                        )
                    }
                } else {
                    Headline(k)
                    SareeFan(state.showcase, Modifier.fillMaxWidth().height(330.dp * k))
                    Spacer(Modifier.weight(1f))
                    StartCard(vm, hasCamera, code, { code = it }, onSession, vertical = narrow, modifier = Modifier.fillMaxWidth())
                }

                ErrorNote(state.error)
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
    onCode: (String) -> Unit,
    onSession: (Boolean) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scanner: @Composable (Modifier) -> Unit = { m ->
        ScanWindow(m) {
            if (hasCamera) {
                BarcodeScannerView(Modifier.fillMaxSize(), ScanKind.QR, useFrontCamera = true) { c -> vm.lookup(c, onSession) }
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
            Text("Hold up the QR code our staff gave you", fontFamily = Rozha, fontSize = 32.sp, lineHeight = 38.sp, color = OnMirror)
            Muted("Or type the 6-letter code", scale = 0.82f)
            CodeBoxes(code, onCode, onDone = { vm.lookup(code, onSession) }, boxSize = 44.dp)
            GoldButton(
                if (state.lookingUp) "Finding your sarees…" else "Start",
                onClick = { vm.lookup(code, onSession) },
                enabled = code.length == 6 && !state.lookingUp,
            )
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MirrorRaised)
            .border(1.dp, ZariLight.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
            .padding(26.dp),
    ) {
        if (vertical) {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                scanner(Modifier.fillMaxWidth().aspectRatio(1f))
                words(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
                scanner(Modifier.size(236.dp))
                words(Modifier.weight(1f))
            }
        }
    }
}
