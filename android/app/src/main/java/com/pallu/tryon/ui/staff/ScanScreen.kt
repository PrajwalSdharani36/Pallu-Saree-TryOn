package com.pallu.tryon.ui.staff

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.data.Saree
import com.pallu.tryon.ui.common.BarcodeScannerView
import com.pallu.tryon.ui.common.BigButton
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.common.ScanKind
import com.pallu.tryon.ui.common.StoreHeader
import com.pallu.tryon.ui.common.rememberCameraPermission
import com.pallu.tryon.ui.common.rupees
import com.pallu.tryon.ui.theme.Peacock
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariGold
import com.pallu.tryon.ui.theme.rememberSweep
import kotlinx.coroutines.delay

private data class Toast(val text: String, val ok: Boolean, val id: Long = System.nanoTime())

@Composable
fun ScanScreen(vm: StaffViewModel, onBack: () -> Unit, onSessionReady: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val hasCamera = rememberCameraPermission()
    var typed by remember { mutableStateOf("") }
    var torch by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var lastToast by remember { mutableStateOf<Toast?>(null) }
    var newest by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val adding = state.addingTo != null
    // While a name or barcode is being typed, fold the camera away so the field stays above the keyboard.
    var nameFocused by remember { mutableStateOf(false) }
    var codeFocused by remember { mutableStateOf(false) }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val typing = imeOpen && (nameFocused || codeFocused)

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            toast = when (e) {
                is ScanEvent.Added -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    newest = e.saree.id
                    Toast("Added ${e.saree.name}", ok = true)
                }
                is ScanEvent.Duplicate -> Toast("${e.name} is already on the list", ok = false)
                is ScanEvent.NotFound -> Toast("Tag ${e.code} isn't in the catalogue", ok = false)
            }
        }
    }
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            lastToast = toast
            delay(2200)
            toast = null
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        StoreHeader(
            if (adding) "Add more sarees" else "Scan saree tags",
            if (adding) "They appear at the mirror straight away" else "Point the camera at each barcode",
            onBack = onBack,
        )

        // ---- viewfinder (clipped: the camera picture must never spill over the form below)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val full = maxWidth * 10f / 16f
        // 1dp rather than 0 so the camera stays attached and comes straight back
        val h by animateDpAsState(if (typing) 1.dp else full, tween(250), label = "viewfinder")
        Box(Modifier.fillMaxWidth().height(h).clipToBounds().background(Color(0xFF1A1216))) {
            if (hasCamera) {
                BarcodeScannerView(Modifier.fillMaxSize(), ScanKind.PRODUCT_BARCODES, torch = torch, onCode = vm::onCode)
                ScanFrame(Modifier.fillMaxSize())
                IconButton(
                    onClick = { torch = !torch },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(CircleShape).background(Color(0x99000000)),
                ) {
                    Icon(
                        if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        contentDescription = if (torch) "Turn torch off" else "Turn torch on",
                        tint = Color.White,
                    )
                }
            } else {
                Text(
                    "Allow camera access to scan tags, or type the barcode below.",
                    color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = toast != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            ) {
                val t = toast ?: lastToast
                if (t != null) ToastChip(t.text, if (t.ok) Icons.Filled.Check else Icons.Filled.Info, if (t.ok) Peacock else Color(0xFF9A5B00))
            }
        }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!adding) {
                item {
                    OutlinedTextField(
                        value = state.customerLabel, onValueChange = vm::setLabel,
                        label = { Text("Customer name or token (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { nameFocused = it.isFocused },
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it },
                    label = { Text("Type a barcode instead") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.onCode(typed); typed = "" }),
                    trailingIcon = {
                        TextButton(onClick = { vm.onCode(typed); typed = "" }, enabled = typed.isNotBlank()) { Text("Add") }
                    },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { codeFocused = it.isFocused },
                )
            }
            item { ErrorNote(state.error) }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        when (state.scanned.size) {
                            0 -> "No sarees yet"
                            1 -> "1 saree picked"
                            else -> "${state.scanned.size} sarees picked"
                        },
                        fontFamily = Rozha,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    val total = state.scanned.sumOf { it.price }
                    if (total > 0) {
                        Text("${rupees(total)} in total", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.scanned.isEmpty()) {
                item {
                    Text(
                        "Scan the tag on each saree the customer wants to try. They'll show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // newest first, highlighted
            items(state.scanned.asReversed(), key = { it.id }) { s ->
                ScannedRow(
                    s,
                    highlight = s.id == newest,
                    onRemove = if (adding) null else ({ vm.remove(s) }),
                    modifier = Modifier.animateItem(),
                )
            }
        }

        Surface(shadowElevation = 8.dp) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp)) {
                if (adding) {
                    BigButton("Done adding", onClick = { state.addingTo?.let(onSessionReady) }, modifier = Modifier.fillMaxWidth())
                } else {
                    BigButton(
                        text = when {
                            state.busy -> "Creating QR code…"
                            state.scanned.size == 1 -> "Create QR code for 1 saree"
                            else -> "Create QR code for ${state.scanned.size} sarees"
                        },
                        enabled = state.scanned.isNotEmpty() && !state.busy,
                        onClick = { vm.createSession(onSessionReady) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Dimmed surround, gold target frame and a moving red laser line. */
@Composable
private fun ScanFrame(modifier: Modifier) {
    val sweep = rememberSweep(1200, "laser")
    Canvas(modifier) {
        val fw = size.width * 0.7f
        val fh = fw / 2.6f
        val left = (size.width - fw) / 2
        val top = (size.height - fh) / 2
        val dim = Color(0x73000000)
        drawRect(dim, size = androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(dim, topLeft = Offset(0f, top + fh), size = androidx.compose.ui.geometry.Size(size.width, size.height - top - fh))
        drawRect(dim, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, fh))
        drawRect(dim, topLeft = Offset(left + fw, top), size = androidx.compose.ui.geometry.Size(size.width - left - fw, fh))
        drawRoundRect(
            ZariGold, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(fw, fh),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()),
        )
        val y = top + fh * (0.15f + 0.7f * sweep)
        drawLine(Color(0x66FF5A6E), Offset(left + 16.dp.toPx(), y), Offset(left + fw - 16.dp.toPx(), y), 8.dp.toPx())
        drawLine(Color(0xFFFF5A6E), Offset(left + 16.dp.toPx(), y), Offset(left + fw - 16.dp.toPx(), y), 2.dp.toPx())
    }
}

@Composable
private fun ToastChip(text: String, icon: ImageVector, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color(0xFF24121A), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ScannedRow(s: Saree, highlight: Boolean, onRemove: (() -> Unit)?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) Color(0xFFFBF3DF) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (highlight) ZariGold else MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(s.imageUrl, s.name, Modifier.size(52.dp, 66.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(s.fabric, rupees(s.price), s.barcode).filter { it.isNotBlank() }.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove ${s.name}") }
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}
