package com.pallu.tryon.ui.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.data.SessionItem
import com.pallu.tryon.ui.common.BigButton
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.QrImage
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.common.StoreHeader
import com.pallu.tryon.ui.common.rupees

@Composable
fun SessionScreen(
    vm: StaffViewModel,
    sessionId: String,
    onBack: () -> Unit,
    onScanMore: () -> Unit,
    onNewCustomer: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    DisposableEffect(sessionId) {
        vm.watch(sessionId)
        onDispose { vm.stopWatching() }
    }
    val s = state.session?.takeIf { it.id == sessionId }

    Column(Modifier.fillMaxSize()) {
        StoreHeader(
            s?.customerLabel?.ifBlank { null } ?: "Customer QR code",
            s?.let { statusWords(it.status) },
            onBack = onBack,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (s == null) {
                item { Text("Loading…") }
            } else {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 520.dp)) {
                        if (s.isClosed) {
                            Text(
                                if (s.status == "closed") "This customer has finished. Their photos are deleted."
                                else "This QR code has expired. Start a new customer to make another.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else {
                            QrImage(s.qrPayload)
                            Spacer(Modifier.size(12.dp))
                            Text("Show this at the mirror, or type the code", style = MaterialTheme.typography.bodyLarge)
                            Text(s.code, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        if (s.total > 0 && s.hasPhoto) {
                            Text(
                                "${s.done} of ${s.total} looks ready" + if (s.failed > 0) ", ${s.failed} need a retry" else "",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                item { ErrorNote(state.error, Modifier.widthIn(max = 520.dp)) }
                items(s.items, key = { it.id }) { item ->
                    ItemRow(item, Modifier.widthIn(max = 520.dp))
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (s != null && !s.isClosed) {
                OutlinedButton(onClick = onScanMore, modifier = Modifier.fillMaxWidth()) { Text("Scan more sarees for this customer") }
            }
            BigButton("Next customer", onNewCustomer, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ItemRow(item: SessionItem, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (item.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(item.saree.imageUrl, item.saree.name, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.saree.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOf(rupees(item.saree.price), itemWords(item.status)).filter { it.isNotBlank() }.joinToString(". "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.favorite) Icon(Icons.Filled.Favorite, contentDescription = "Customer liked this", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun itemWords(status: String) = when (status) {
    "pending" -> "Waiting for photo"
    "queued", "running" -> "Draping…"
    "done" -> "Look ready"
    "failed" -> "Couldn't create this look"
    else -> status
}
