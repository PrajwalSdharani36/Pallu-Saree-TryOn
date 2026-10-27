package com.pallu.tryon.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pallu.tryon.data.AppContainer
import com.pallu.tryon.data.AppMode
import com.pallu.tryon.data.AppSettings
import com.pallu.tryon.data.KioskLock
import com.pallu.tryon.data.normalizeServerUrl
import com.pallu.tryon.ui.common.BigButton
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.StoreHeader
import kotlinx.coroutines.launch

/** First-run screen: connect to the store server and decide what this device is for. */
@Composable
fun SetupScreen(container: AppContainer, initial: AppSettings, onDone: (AppMode) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var url by remember { mutableStateOf(initial.serverUrl) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var mode by remember { mutableStateOf(initial.mode) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var connectedStore by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        StoreHeader("Set up this device", "Connect to your store's Pallu server")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it; connectedStore = null },
                    label = { Text("Server address") },
                    placeholder = { Text("192.168.1.20:8000") },
                    supportingText = { Text("The computer running the Pallu backend, on the shop's Wi-Fi.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it; connectedStore = null },
                    label = { Text("Store key") },
                    supportingText = { Text("API_KEY from the server's .env file.") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("What is this device for?", style = MaterialTheme.typography.titleMedium)
                ModeCard(
                    icon = Icons.Filled.QrCodeScanner,
                    title = "Staff phone",
                    body = "Scan the tags of the sarees a customer picks and show them a QR code.",
                    selected = mode == AppMode.STAFF,
                    onClick = { mode = AppMode.STAFF },
                )
                ModeCard(
                    icon = Icons.Filled.Storefront,
                    title = "Try-on mirror",
                    body = "The kiosk tablet customers stand in front of. Locks to the customer screens.",
                    selected = mode == AppMode.KIOSK,
                    onClick = { mode = AppMode.KIOSK },
                )

                ErrorNote(error)
                connectedStore?.let {
                    Text("Connected to $it.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                BigButton(
                    text = if (busy) "Connecting…" else "Connect and start",
                    enabled = !busy && url.isNotBlank() && key.isNotBlank() && mode != null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            container.probe(url, key)
                                .onSuccess { health ->
                                    connectedStore = health.storeName
                                    val chosen = mode ?: AppMode.STAFF
                                    container.settingsStore.save(
                                        initial.copy(
                                            serverUrl = normalizeServerUrl(url),
                                            apiKey = key.trim(),
                                            mode = chosen,
                                            storeName = health.storeName,
                                        )
                                    )
                                    KioskLock.setHomeScreen(context, chosen == AppMode.KIOSK)
                                    busy = false
                                    onDone(chosen)
                                }
                                .onFailure { busy = false; error = it.message }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
