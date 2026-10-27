package com.pallu.tryon.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.data.TryOnSession
import com.pallu.tryon.ui.common.BigButton
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.StoreHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffHomeScreen(
    vm: StaffViewModel,
    storeName: String,
    onNewCustomer: () -> Unit,
    onOpenSession: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshRecent() }

    Column(Modifier.fillMaxSize()) {
        StoreHeader("Pallu", storeName.ifBlank { "Staff" }, actions = {
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Device settings", tint = Color.White)
            }
        })
        PullToRefreshBox(
            isRefreshing = state.loadingRecent,
            onRefresh = { vm.refreshRecent() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(
                            "When a customer has picked their sarees, scan the tags and hand them the QR code for the mirror.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        BigButton("Scan a new customer's sarees", onNewCustomer, Modifier.fillMaxWidth())
                        ErrorNote(state.error)
                        state.engineNote?.let { EngineNote(it) }
                        Text(
                            "Today's customers",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        if (state.recent.isEmpty() && !state.loadingRecent) {
                            Text(
                                "No customers yet. Pull down to refresh.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(state.recent, key = { it.id }) { s ->
                    Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) { SessionRow(s) { onOpenSession(s.id) } }
                }
                item { Box(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun SessionRow(s: TryOnSession, onClick: () -> Unit) {
    val favs = s.items.filter { it.favorite }
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.code, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    listOf(s.customerLabel.ifBlank { null }, if (s.mode == "gift") "gift" else null)
                        .filterNotNull().joinToString(", ").ifBlank { "Customer" },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${statusWords(s.status)}. ${s.total} sarees.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (favs.isNotEmpty()) {
                    Text(
                        "Liked: " + favs.joinToString(", ") { it.saree.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (favs.isNotEmpty()) {
                Icon(Icons.Filled.Favorite, contentDescription = "${favs.size} liked", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Demo mode, or a problem with the Google key: tells staff why looks may not be real. */
@Composable
private fun EngineNote(note: com.pallu.tryon.data.EngineStatus) {
    val problem = note.state == "error" || note.state == "warning"
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (problem) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            note.message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )
    }
}
