package com.pallu.tryon.ui.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.data.SessionItem
import com.pallu.tryon.data.TryOnSession
import com.pallu.tryon.ui.common.BigButton
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.QrImage
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.common.StoreHeader
import com.pallu.tryon.ui.common.rupees
import com.pallu.tryon.ui.theme.Arakku
import com.pallu.tryon.ui.theme.Peacock
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.Zari

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
            s?.customerLabel?.ifBlank { null } ?: "Customer",
            s?.let { statusWords(it.status) },
            onBack = onBack,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (s == null) {
                item { Text("Loading…") }
            } else {
                item { Progress(s, Modifier.widthIn(max = 520.dp).fillMaxWidth()) }
                item {
                    Surface(
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (s.isClosed) {
                                Text(
                                    if (s.status == "closed") "This customer has finished. Their photo and looks are deleted."
                                    else "This QR code has expired. Start a new customer to make another.",
                                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                                )
                            } else {
                                QrImage(s.qrPayload, size = 210.dp)
                                Text("Show at the mirror, or type the code", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(s.code, fontFamily = Rozha, fontSize = 40.sp, letterSpacing = 4.sp, color = Arakku)
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text("Looks", fontFamily = Rozha, fontSize = 22.sp, modifier = Modifier.weight(1f))
                        val liked = s.items.count { it.favorite }
                        Text(
                            buildString {
                                if (s.hasPhoto) append("${s.done} of ${s.total} ready") else append("${s.total} sarees")
                                if (liked > 0) append(", $liked liked")
                            },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item { ErrorNote(state.error, Modifier.widthIn(max = 520.dp)) }
                items(s.items, key = { it.id }) { item ->
                    ItemRow(item, Modifier.widthIn(max = 520.dp).animateItem())
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (s != null && !s.isClosed) {
                OutlinedButton(
                    onClick = onScanMore,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                ) { Text("Scan more", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
            BigButton("Next customer", onNewCustomer, Modifier.weight(1.3f))
        }
    }
}

private val STEPS = listOf("Scanned", "At mirror", "Draping", "Viewing", "Finished")

/** Where the customer is: a real sequence, so it is drawn as one. */
@Composable
private fun Progress(s: TryOnSession, modifier: Modifier) {
    val current = when {
        s.status == "closed" -> 5
        s.status == "expired" -> -1
        s.status == "ready" -> 3
        s.status == "generating" -> 2
        else -> 1 // open: waiting at the mirror
    }
    if (current < 0) return
    val line = MaterialTheme.colorScheme.outline
    Row(modifier) {
        STEPS.forEachIndexed { i, label ->
            val done = i < current
            val now = i == current
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val y = size.height / 2
                        if (i > 0) drawLine(if (i <= current) Peacock else line, Offset(0f, y), Offset(size.width / 2, y), 2.dp.toPx())
                        if (i < STEPS.lastIndex) drawLine(if (i < current) Peacock else line, Offset(size.width / 2, y), Offset(size.width, y), 2.dp.toPx())
                    }
                    Box(
                        Modifier.size(24.dp).clip(CircleShape)
                            .background(if (done) Peacock else Color.White)
                            .border(2.dp, when { done -> Peacock; now -> Zari; else -> line }, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        if (now) Box(Modifier.size(8.dp).clip(CircleShape).background(Zari))
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp),
                    fontWeight = if (now) FontWeight.Bold else FontWeight.Medium,
                    color = if (now || done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ItemRow(item: SessionItem, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (item.favorite) Color(0xFFE9A9B5) else MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(start = 7.dp, end = 12.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(item.saree.imageUrl, item.saree.name, Modifier.size(42.dp, 52.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.saree.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.saree.price > 0) {
                    Text(rupees(item.saree.price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusChip(item.status)
            if (item.favorite) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Favorite, contentDescription = "Customer liked this", tint = Color(0xFFC02A44))
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (text, bg, fg) = when (status) {
        "pending" -> Triple("Waiting for photo", Color(0xFFEDE8EF), Color(0xFF5B4A54))
        "queued", "running" -> Triple("Draping…", Color(0xFFFBF0D6), Color(0xFF7A5410))
        "done" -> Triple("Look ready", Color(0xFFDCEFEE), Color(0xFF0B4B4F))
        "failed" -> Triple("Needs a retry", Color(0xFFFBE1E5), Color(0xFF8E1B2E))
        else -> Triple(status, Color(0xFFEDE8EF), Color(0xFF5B4A54))
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp),
        color = fg,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}
