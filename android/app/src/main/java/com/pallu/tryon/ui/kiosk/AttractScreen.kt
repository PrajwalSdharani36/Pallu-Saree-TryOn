package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.ui.common.BarcodeScannerView
import com.pallu.tryon.ui.common.BigButton
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.ScanKind
import com.pallu.tryon.ui.common.rememberCameraPermission
import com.pallu.tryon.ui.theme.ZariBorder
import com.pallu.tryon.ui.theme.ZariLight

/** Idle screen of the mirror: waits for the customer's QR code. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttractScreen(
    vm: KioskViewModel,
    storeName: String,
    apiKey: String,
    onSession: () -> Unit,
    onExitKiosk: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val hasCamera = rememberCameraPermission()
    var code by remember { mutableStateOf("") }
    var askExit by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ZariBorder(height = 28.dp)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
            val wide = maxWidth > maxHeight
            val scanner: @Composable (Modifier) -> Unit = { m ->
                Box(
                    m.aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                        .border(4.dp, ZariLight, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasCamera) {
                        BarcodeScannerView(Modifier.fillMaxSize(), ScanKind.QR, useFrontCamera = true) { c ->
                            vm.lookup(c, onSession)
                        }
                    } else {
                        Text("Camera access is off. Type your code instead.", color = Color.White, textAlign = TextAlign.Center)
                    }
                    if (state.lookingUp) CircularProgressIndicator(color = ZariLight)
                }
            }
            val words: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        storeName.ifBlank { "Pallu" },
                        style = MaterialTheme.typography.titleLarge,
                        color = ZariLight,
                        // Hidden staff exit: press and hold the store name.
                        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { askExit = true }),
                    )
                    Text(
                        "See yourself in every saree you picked.",
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Text(
                        "Hold the QR code from our staff up to the camera. No draping needed: one photo and you'll see each saree on you.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.uppercase().take(12) },
                            label = { Text("Or type your code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(onGo = { vm.lookup(code, onSession) }),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.padding(6.dp))
                        BigButton("Start", { vm.lookup(code, onSession) }, enabled = code.length >= 4 && !state.lookingUp)
                    }
                    ErrorNote(state.error)
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    words(Modifier.weight(1f).widthIn(max = 620.dp))
                    scanner(Modifier.weight(0.8f).widthIn(max = 460.dp))
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    words(Modifier.widthIn(max = 620.dp).fillMaxWidth())
                    scanner(Modifier.fillMaxWidth(0.8f).widthIn(max = 460.dp))
                }
            }
        }
        Text(
            "Your photo is used only to show you these sarees and is deleted when you finish.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
        ZariBorder(height = 28.dp)
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
