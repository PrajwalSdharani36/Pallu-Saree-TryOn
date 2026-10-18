package com.pallu.tryon.ui.kiosk

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.data.SessionItem
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.common.rupees
import com.pallu.tryon.ui.theme.GoldBright
import com.pallu.tryon.ui.theme.Kumkum
import com.pallu.tryon.ui.theme.Mirror
import com.pallu.tryon.ui.theme.MirrorLine
import com.pallu.tryon.ui.theme.MirrorRaised
import com.pallu.tryon.ui.theme.MirrorRaised2
import com.pallu.tryon.ui.theme.OnGold
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.OnMirrorFaint
import com.pallu.tryon.ui.theme.OnMirrorMuted
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun ResultsScreen(vm: KioskViewModel, onRetake: () -> Unit, onFinished: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session = state.session ?: return
    val items = session.items
    val pager = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    var showOriginal by remember { mutableStateOf(false) }
    val strip = rememberLazyListState()
    LaunchedEffect(pager.currentPage) { if (items.isNotEmpty()) strip.animateScrollToItem(pager.currentPage.coerceAtMost(items.lastIndex)) }

    KioskPage {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val short = maxHeight < 900.dp // landscape tablet: give the looks every bit of height
            val k = headlineSize(maxWidth) * (if (short) 0.8f else 1f)
            val side = 56.dp * k.coerceAtLeast(0.6f)
            Column(Modifier.fillMaxSize()) {
                // ---- header
                Row(
                    Modifier.fillMaxWidth().padding(start = side, end = side, top = if (short) 20.dp else 34.dp * k),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your looks", fontFamily = Rozha, fontSize = (54 * k).sp, lineHeight = (58 * k).sp, color = OnMirror)
                        Muted(
                            when {
                                session.total == 1 && session.done == 1 -> "Your look is ready. Shortlist it if you love it."
                                session.done == session.total -> "All ${session.total} are ready. Swipe through and shortlist the ones you love."
                                else -> "${session.done} of ${session.total} ready. The rest appear as soon as they're draped."
                            },
                            scale = 0.9f * k,
                        )
                    }
                    if (items.isNotEmpty()) {
                        Text("${pager.currentPage + 1} / ${items.size}", fontFamily = Rozha, fontSize = 30.sp, color = ZariLight)
                    }
                }
                if (session.done < session.total) {
                    ReadyBar(session.done, session.total, Modifier.padding(start = side, end = side, top = 14.dp))
                }

                // ---- the gallery: neighbours peek in, smaller and dimmer. Each look keeps the photo's
                // 3:4 portrait shape, so on a landscape screen the drape isn't cropped to a strip.
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = if (short) 10.dp else 20.dp)) {
                val minPeek = (maxWidth * 0.16f).coerceIn(40.dp, 140.dp)
                val pageW = minOf(maxWidth - minPeek * 2, (maxHeight - 12.dp) * 0.75f)
                val peek = (maxWidth - pageW) / 2
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = peek, vertical = 6.dp),
                    pageSpacing = 18.dp,
                    key = { items[it].id },
                ) { page ->
                    val item = items[page]
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    LookCard(
                        item = item,
                        isCurrent = page == pager.currentPage,
                        original = state.photoPreview?.takeIf { showOriginal && page == pager.currentPage },
                        onRetry = { vm.retry(item) },
                        onFavorite = { vm.toggleFavorite(item) },
                        onHold = { showOriginal = it },
                        modifier = Modifier.graphicsLayer {
                            val s = lerp(1f, 0.86f, offset)
                            scaleX = s
                            scaleY = s
                            alpha = lerp(1f, 0.42f, offset)
                        },
                    )
                }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = if (short) 8.dp else 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, tint = OnMirrorMuted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Press and hold the look to compare it with your photo", style = MaterialTheme.typography.bodyLarge, color = OnMirrorMuted)
                }

                // ---- thumbnails
                LazyRow(
                    state = strip,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = side, vertical = if (short) 8.dp else 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                        val selected = index == pager.currentPage
                        Box(
                            Modifier
                                .size(if (short) 52.dp else 72.dp, if (short) 68.dp else 94.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(if (selected) 3.dp else 1.dp, if (selected) ZariLight else MirrorLine, RoundedCornerShape(10.dp))
                                .clickable { scope.launch { pager.animateScrollToPage(index) } }
                                .alpha(if (selected) 1f else 0.72f),
                        ) {
                            RemoteImage(item.resultUrl ?: item.saree.imageUrl, item.saree.name, Modifier.fillMaxSize())
                            if (item.isWorking) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                            when {
                                item.favorite -> Badge(Kumkum, Icons.Filled.Favorite, Modifier.align(Alignment.TopEnd).padding(5.dp), size = 22.dp, tint = Color.White)
                                item.isDone -> Badge(ZariLight, Icons.Filled.Check, Modifier.align(Alignment.TopEnd).padding(5.dp), size = 20.dp)
                            }
                        }
                    }
                }

                ErrorNote(state.error, Modifier.padding(horizontal = side))
                Row(
                    Modifier.fillMaxWidth().padding(start = side, end = side, bottom = if (short) 12.dp else 22.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlineButton("Retake photo", { vm.setPhoto(null); onRetake() })
                    Spacer(Modifier.weight(1f))
                    GoldButton(
                        text = if (state.finishing) "Deleting your photos…" else "I'm done",
                        onClick = { vm.finish(onFinished) },
                        enabled = !state.finishing,
                        modifier = Modifier.widthIn(min = 220.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    val target = if (total == 0) 0f else done.toFloat() / total
    val p by animateFloatAsState(target, tween(600), label = "ready")
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MirrorRaised2)) {
        Box(Modifier.fillMaxWidth(p).height(8.dp).clip(RoundedCornerShape(4.dp)).background(ZariLight))
    }
}

@Composable
private fun LookCard(
    item: SessionItem,
    isCurrent: Boolean,
    original: android.graphics.Bitmap?,
    onRetry: () -> Unit,
    onFavorite: () -> Unit,
    onHold: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .shadow(24.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(MirrorRaised)
            .border(if (isCurrent) 2.dp else 0.dp, ZariLight.copy(alpha = if (isCurrent) 0.55f else 0f), RoundedCornerShape(26.dp))
            .pointerInput(item.id) {
                detectTapGestures(onPress = {
                    onHold(true)
                    tryAwaitRelease()
                    onHold(false)
                })
            },
    ) {
        // A finished look fades in over the draping state.
        Crossfade(targetState = item.isDone, animationSpec = tween(700), label = "reveal") { done ->
            when {
                original != null -> Image(original.asImageBitmap(), "Your original photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                done -> RemoteImage(item.resultUrl, "You wearing ${item.saree.name}", Modifier.fillMaxSize(), ContentScale.Crop)
                item.isFailed -> Failed(item, onRetry)
                else -> Draping(item)
            }
        }
        if (item.isDone && original == null) {
            InfoPanel(item, onFavorite, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
        if (original != null) {
            Text(
                "Your photo",
                style = MaterialTheme.typography.titleMedium,
                color = OnMirror,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
                    .clip(RoundedCornerShape(50)).background(Mirror.copy(alpha = 0.75f)).padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

/** Frosted strip over the bottom of a finished look: what it is, what it costs, shortlist. */
@Composable
private fun InfoPanel(item: SessionItem, onFavorite: () -> Unit, modifier: Modifier) {
    var bump by remember { mutableIntStateOf(0) }
    val heartScale by animateFloatAsState(
        targetValue = if (bump % 2 == 1) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        finishedListener = { if (bump % 2 == 1) bump += 1 },
        label = "heart",
    )
    val tap = { if (!item.favorite) bump += 1; onFavorite() }
    val meta = listOf(item.saree.fabric, rupees(item.saree.price)).filter { it.isNotBlank() }.joinToString(", ")
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 330.dp
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Mirror.copy(alpha = 0.84f))
                .border(1.dp, ZariLight.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                .padding(horizontal = if (narrow) 14.dp else 20.dp, vertical = if (narrow) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.saree.name, fontFamily = Rozha,
                    fontSize = if (narrow) 19.sp else 26.sp, lineHeight = if (narrow) 23.sp else 30.sp,
                    color = OnMirror, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = OnMirrorMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            if (narrow) {
                // icon-only on small screens so the saree name keeps its room
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(if (item.favorite) Kumkum else MirrorRaised2)
                        .clickable(onClickLabel = if (item.favorite) "Remove from shortlist" else "Shortlist") { tap() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (item.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (item.favorite) "Shortlisted" else "Shortlist",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp).scale(heartScale),
                    )
                }
            } else {
                Button(
                    onClick = tap,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (item.favorite) Kumkum else MirrorRaised2,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        if (item.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp).scale(heartScale),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(if (item.favorite) "Shortlisted" else "Shortlist", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private val DRAPE_STEPS = listOf(
    "Reading the border and pallu",
    "Pleating at the waist",
    "Placing the pallu on your shoulder",
    "Final touches",
)

/** While a look is being made: the saree glows behind a weaving loom, with step-by-step progress. */
@Composable
private fun Draping(item: SessionItem) {
    var step by remember(item.id) { mutableIntStateOf(0) }
    LaunchedEffect(item.id, item.status) {
        if (item.status == "running") {
            while (step < DRAPE_STEPS.lastIndex) {
                delay(6000)
                step += 1
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        RemoteImage(item.saree.imageUrl, null, Modifier.fillMaxSize().blur(28.dp).alpha(0.45f))
        Box(Modifier.fillMaxSize().background(Mirror.copy(alpha = 0.45f)))
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically),
        ) {
            DrapingLoom(Modifier.size(220.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (item.status == "running") "Now draping" else "Up next",
                    style = MaterialTheme.typography.titleMedium, color = ZariLight,
                )
                Text(
                    item.saree.name, fontFamily = Rozha, fontSize = 34.sp, lineHeight = 38.sp,
                    color = OnMirror, textAlign = TextAlign.Center,
                )
            }
            if (item.status == "running") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 420.dp)) {
                    DRAPE_STEPS.forEachIndexed { i, label -> StepRow(label, done = i < step, now = i == step) }
                }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, now: Boolean) {
    val pulse = com.pallu.tryon.ui.theme.rememberSweep(800, "step-pulse")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(if (done) ZariLight else Color.Transparent)
                .border(2.dp, if (done || now) ZariLight else MirrorLine, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = OnGold, modifier = Modifier.size(16.dp))
            if (now) Box(Modifier.size(11.dp).clip(CircleShape).background(GoldBright.copy(alpha = lerp(0.45f, 1f, pulse))))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
            color = when { now -> Color(0xFFFFF6E6); done -> OnMirror; else -> OnMirrorFaint },
        )
    }
}

@Composable
private fun Failed(item: SessionItem, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        RemoteImage(item.saree.imageUrl, item.saree.name, Modifier.size(150.dp, 186.dp).clip(RoundedCornerShape(12.dp)))
        Text("We couldn't drape this one", fontFamily = Rozha, fontSize = 30.sp, color = OnMirror, textAlign = TextAlign.Center)
        Muted("This sometimes happens with an unusual photo. Try once more.", scale = 0.85f, align = TextAlign.Center)
        GoldButton("Try again", onRetry, icon = Icons.Filled.Refresh)
    }
}
