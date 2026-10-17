package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.data.SessionItem
import com.pallu.tryon.ui.common.BigButton
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.common.rupees
import com.pallu.tryon.ui.theme.Mirror
import com.pallu.tryon.ui.theme.MirrorRaised
import com.pallu.tryon.ui.theme.ZariBorder
import com.pallu.tryon.ui.theme.ZariLight
import kotlinx.coroutines.launch

@Composable
fun ResultsScreen(vm: KioskViewModel, onRetake: () -> Unit, onFinished: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session = state.session ?: return
    val items = session.items
    val pager = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    var showOriginal by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Mirror)) {
        ZariBorder()
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your looks", style = MaterialTheme.typography.headlineLarge)
                Text(
                    when {
                        session.done == session.total -> "All ${session.total} are ready. Tap the heart on the ones you love."
                        else -> "${session.done} of ${session.total} ready. The rest appear as soon as they're draped."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (session.done < session.total) {
            LinearProgressIndicator(
                progress = { if (session.total == 0) 0f else session.done.toFloat() / session.total },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                color = ZariLight,
                trackColor = MirrorRaised,
            )
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
            pageSpacing = 20.dp,
            key = { items[it].id },
        ) { page ->
            val item = items[page]
            LookCard(
                item = item,
                original = state.photoPreview?.takeIf { showOriginal && page == pager.currentPage },
                onRetry = { vm.retry(item) },
                onHold = { showOriginal = it },
            )
        }

        val current = items.getOrNull(pager.currentPage)
        if (current != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(current.saree.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOf(current.saree.fabric, rupees(current.saree.price)).filter { it.isNotBlank() }.joinToString(", "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = { vm.toggleFavorite(current) }, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)) {
                    Icon(
                        if (current.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (current.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (current.favorite) "Shortlisted" else "Shortlist", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                val selected = index == pager.currentPage
                Box(
                    Modifier
                        .size(width = 64.dp, height = 84.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(if (selected) 3.dp else 1.dp, if (selected) ZariLight else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .clickable { scope.launch { pager.animateScrollToPage(index) } },
                ) {
                    RemoteImage(item.resultUrl ?: item.saree.imageUrl, item.saree.name, Modifier.fillMaxSize())
                    if (item.isWorking) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    if (item.favorite) {
                        Icon(
                            Icons.Filled.Favorite, contentDescription = "Shortlisted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                        )
                    }
                }
            }
        }

        ErrorNote(state.error, Modifier.padding(horizontal = 24.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { vm.setPhoto(null); onRetake() }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Text("Retake photo", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            BigButton(
                text = if (state.finishing) "Deleting your photos…" else "I'm done",
                onClick = { vm.finish(onFinished) },
                enabled = !state.finishing,
                modifier = Modifier.widthIn(min = 220.dp),
            )
        }
        Text(
            "Press and hold a look to compare it with your photo. Tapping I'm done deletes your photo and these looks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 24.dp, end = 24.dp),
        )
        ZariBorder()
    }
}

@Composable
private fun LookCard(
    item: SessionItem,
    original: android.graphics.Bitmap?,
    onRetry: () -> Unit,
    onHold: (Boolean) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(MirrorRaised)
            .pointerInput(item.id) {
                detectTapGestures(onPress = {
                    onHold(true)
                    tryAwaitRelease()
                    onHold(false)
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            original != null -> Image(original.asImageBitmap(), "Your original photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            item.isDone -> RemoteImage(item.resultUrl, "You wearing ${item.saree.name}", Modifier.fillMaxSize(), ContentScale.Fit)
            item.isFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                RemoteImage(item.saree.imageUrl, item.saree.name, Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.size(16.dp))
                Text("We couldn't drape this one.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    "This sometimes happens with unusual photos. Try once more.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(12.dp))
                BigButton("Try again", onRetry)
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RemoteImage(item.saree.imageUrl, item.saree.name, Modifier.fillMaxSize().alpha(0.35f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Mirror.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ZariLight)
                    }
                    Spacer(Modifier.size(16.dp))
                    Text(
                        if (item.status == "running") "Draping ${item.saree.name}…" else "Waiting to drape ${item.saree.name}…",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}
