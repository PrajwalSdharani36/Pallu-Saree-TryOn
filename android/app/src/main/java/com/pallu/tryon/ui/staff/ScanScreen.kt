package com.pallu.tryon.ui.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import com.pallu.tryon.ui.theme.ZariLight
import kotlinx.coroutines.launch

@Composable
fun ScanScreen(vm: StaffViewModel, onBack: () -> Unit, onSessionReady: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val hasCamera = rememberCameraPermission()
    var typed by remember { mutableStateOf("") }
    val adding = state.addingTo != null

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            val msg = when (e) {
                is ScanEvent.Added -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    "Added ${e.saree.name}"
                }
                is ScanEvent.Duplicate -> "${e.name} is already on the list"
                is ScanEvent.NotFound -> "Tag ${e.code} isn't in the catalogue. Add it from the admin page."
            }
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(msg) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            StoreHeader(
                if (adding) "Add more sarees" else "Scan saree tags",
                if (adding) "They appear at the mirror straight away" else "Point the camera at each saree's barcode",
                onBack = onBack,
            )

            // Viewfinder
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (hasCamera) {
                    BarcodeScannerView(Modifier.fillMaxSize(), ScanKind.PRODUCT_BARCODES, onCode = vm::onCode)
                    Box(
                        Modifier.fillMaxWidth(0.72f).aspectRatio(2.6f)
                            .border(3.dp, ZariLight, RoundedCornerShape(12.dp))
                    )
                } else {
                    Text(
                        "Allow camera access to scan tags, or type the barcode below.",
                        color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp),
                    )
                }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = typed, onValueChange = { typed = it },
                            label = { Text("Type a barcode") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { vm.onCode(typed); typed = "" }),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.onCode(typed); typed = "" }, enabled = typed.isNotBlank()) { Text("Add") }
                    }
                }
                if (!adding) {
                    item {
                        OutlinedTextField(
                            value = state.customerLabel, onValueChange = vm::setLabel,
                            label = { Text("Customer name or token (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item { ErrorNote(state.error) }
                item {
                    Text(
                        when (state.scanned.size) {
                            0 -> "No sarees scanned yet."
                            1 -> "1 saree"
                            else -> "${state.scanned.size} sarees"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(state.scanned, key = { it.id }) { s -> ScannedRow(s, onRemove = if (adding) null else ({ vm.remove(s) })) }
            }

            Surface(shadowElevation = 6.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    if (adding) {
                        BigButton("Done adding", onClick = { state.addingTo?.let(onSessionReady) }, modifier = Modifier.fillMaxWidth())
                    } else {
                        BigButton(
                            text = if (state.busy) "Creating QR code…" else "Create QR code for ${state.scanned.size} sarees",
                            enabled = state.scanned.isNotEmpty() && !state.busy,
                            onClick = { vm.createSession(onSessionReady) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun ScannedRow(s: Saree, onRemove: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(s.imageUrl, s.name, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall)
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
