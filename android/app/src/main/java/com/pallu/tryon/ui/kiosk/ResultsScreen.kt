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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.data.DRAPES
import com.pallu.tryon.data.SessionItem
import com.pallu.tryon.data.TryOnSession
import com.pallu.tryon.data.drapeLabel
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.common.rupees
import com.pallu.tryon.ui.theme.Glass
import com.pallu.tryon.ui.theme.GlassLine
import com.pallu.tryon.ui.theme.GlassStrong
import com.pallu.tryon.ui.theme.GoldBright
import com.pallu.tryon.ui.theme.Kumkum
import com.pallu.tryon.ui.theme.MirrorStrong
import com.pallu.tryon.ui.theme.OnGold
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.OnMirrorFaint
import com.pallu.tryon.ui.theme.OnMirrorMuted
import com.pallu.tryon.ui.theme.OnPrimary
import com.pallu.tryon.ui.theme.PeacockDeep
import com.pallu.tryon.ui.theme.Primary
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariLight
import com.pallu.tryon.ui.theme.mirrorPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Your looks", Silk Glass style: each look large in the middle of the screen (the whole drape,
 * head to hem, never cropped), the room tinted by the saree, and one glass panel underneath with
 * everything she can do with this look. Arrows as well as swiping, for customers who don't swipe.
 */
@Composable
fun ResultsScreen(vm: KioskViewModel, onRetake: () -> Unit, onFinished: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val session = state.session ?: return
    val items = session.items
    // The pager keeps these lambdas between recompositions, so they must read the LATEST list:
    // a look added by a new drape, "Try more sarees" or the staff phone grows it under them.
    val latest = rememberUpdatedState(items)
    val pager = rememberPagerState(pageCount = { latest.value.size })
    val scope = rememberCoroutineScope()
    var showOriginal by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ResultsSheet?>(null) }
    // a look that was just added (new drape, more sarees): swipe to it once it's in the list
    var goTo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(goTo, items.size) {
        val idx = items.indexOfFirst { it.id == goTo }
        if (idx >= 0) {
            pager.animateScrollToPage(idx)
            goTo = null
        }
    }
    val current = items.getOrNull(pager.currentPage.coerceAtMost(items.lastIndex))
    val go: (Int) -> Unit = { i -> scope.launch { pager.animateScrollToPage(i.coerceIn(0, (items.size - 1).coerceAtLeast(0))) } }

    KioskPage(ambient = current?.let { it.resultUrl ?: it.saree.imageUrl }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                KioskTopBar(
                    left = { OutlineButton("Retake photo", { vm.setPhoto(null); onRetake() }) },
                    right = {
                        GoldButton(
                            text = if (state.finishing) "Finishing…" else "I'm done",
                            onClick = { sheet = ResultsSheet.Done },
                            enabled = !state.finishing,
                        )
                    },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your looks", fontFamily = Rozha, fontSize = 34.sp, lineHeight = 38.sp, color = OnMirror, maxLines = 1)
                        Text(statusLine(session), style = MaterialTheme.typography.titleSmall, color = OnMirrorMuted, maxLines = 1)
                    }
                }
                Segments(items, pager.currentPage, Modifier.padding(start = KioskSide, end = KioskSide, top = 20.dp))

                val gallery: @Composable (Modifier) -> Unit = { m ->
                    Box(m) {
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 96.dp),
                            pageSpacing = 40.dp,
                            key = { latest.value.getOrNull(it)?.id ?: "page-$it" },
                        ) { page ->
                            val item = latest.value.getOrNull(page) ?: return@HorizontalPager
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LookCard(
                                    item = item,
                                    isCurrent = page == pager.currentPage,
                                    original = state.photoPreview?.takeIf { showOriginal && page == pager.currentPage },
                                    onRetry = { vm.retry(item) },
                                    onHold = { showOriginal = it },
                                    modifier = Modifier.aspectRatio(0.75f, matchHeightConstraintsFirst = true),
                                )
                            }
                        }
                        if (items.size > 1) {
                            RoundArrow(back = true, enabled = pager.currentPage > 0, Modifier.align(Alignment.CenterStart).padding(start = 20.dp)) {
                                go(pager.currentPage - 1)
                            }
                            RoundArrow(back = false, enabled = pager.currentPage < items.lastIndex, Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)) {
                                go(pager.currentPage + 1)
                            }
                        }
                    }
                }
                val panel: @Composable (Modifier) -> Unit = { m ->
                    LookPanel(
                        item = current,
                        items = items,
                        currentIndex = pager.currentPage,
                        error = state.error,
                        busy = state.busy,
                        wide = wide,
                        onFavorite = { current?.let(vm::toggleFavorite) },
                        onDrape = { drape ->
                            // a drape she already has for this saree just takes her to that look
                            current?.let { look ->
                                val made = items.firstOrNull { other -> other.saree.id == look.saree.id && other.drapeStyle == drape }
                                if (made != null) goTo = made.id else vm.redrape(look, drape) { id -> goTo = id }
                            }
                        },
                        onThumb = go,
                        onTryMore = { vm.loadCatalog(); sheet = ResultsSheet.More },
                        modifier = m,
                    )
                }

                if (wide) {
                    Row(
                        Modifier.weight(1f).fillMaxWidth().padding(start = 0.dp, end = KioskSide, top = 16.dp, bottom = 26.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            gallery(Modifier.weight(1f).fillMaxWidth())
                            HoldHint(current, Modifier.fillMaxWidth().padding(top = 12.dp))
                        }
                        panel(Modifier.width(480.dp).fillMaxHeight())
                    }
                } else {
                    gallery(Modifier.weight(1f).fillMaxWidth().padding(top = 18.dp))
                    HoldHint(current, Modifier.fillMaxWidth().padding(top = 14.dp))
                    panel(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp))
                }
            }

            when (sheet) {
                ResultsSheet.More -> TryMoreSheet(
                    catalog = state.catalog,
                    loading = state.catalogLoading,
                    alreadyTrying = items.map { it.saree.id }.toSet(),
                    canAdd = MAX_SAREES - items.map { it.saree.id }.distinct().size,
                    busy = state.busy,
                    onAdd = { ids -> vm.addSarees(ids) { id -> goTo = id; sheet = null } },
                    onClose = { sheet = null },
                )
                ResultsSheet.Done -> DoneSheet(
                    shortlisted = items.filter { it.favorite }.map { it.saree.id }.distinct().size,
                    finishing = state.finishing,
                    onTryMore = { vm.loadCatalog(); sheet = ResultsSheet.More },
                    onDone = { vm.finish(onFinished) },
                    onClose = { sheet = null },
                )
                null -> Unit
            }
        }
    }
}

/** Matches the server's MAX_SAREES_PER_SESSION default; the server has the final say. */
private const val MAX_SAREES = 12

private enum class ResultsSheet { More, Done }

private fun statusLine(s: TryOnSession): String = when {
    s.total == 1 && s.done == 1 -> "Your look is ready"
    s.done == s.total -> "All ${s.total} are ready"
    else -> "${s.done} of ${s.total} ready"
}

/** One short bar per look, like stories: bright when ready, the current one in gold. */
@Composable
private fun Segments(items: List<SessionItem>, current: Int, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, item ->
            val color = when {
                i == current -> ZariLight
                item.isDone -> OnMirror.copy(alpha = 0.75f)
                item.isFailed -> Kumkum
                else -> OnMirror.copy(alpha = 0.22f)
            }
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

@Composable
private fun RoundArrow(back: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(64.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(GlassStrong)
            .border(1.dp, GlassLine, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = if (back) "Previous look" else "Next look", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (back) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = if (back) "Previous look" else "Next look",
            tint = OnMirror, modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun HoldHint(item: SessionItem?, modifier: Modifier) {
    Row(modifier.alpha(if (item?.isDone == true) 1f else 0f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.TouchApp, contentDescription = null, tint = OnMirrorMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text("Press and hold the look to see your own photo", style = MaterialTheme.typography.bodyMedium, color = OnMirrorMuted)
    }
}

@Composable
private fun LookCard(
    item: SessionItem,
    isCurrent: Boolean,
    original: android.graphics.Bitmap?,
    onRetry: () -> Unit,
    onHold: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier
            .shadow(if (isCurrent) 34.dp else 12.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(GlassStrong)
            .border(if (isCurrent) 1.5.dp else 1.dp, if (isCurrent) Color.White.copy(alpha = 0.4f) else GlassLine, RoundedCornerShape(32.dp))
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
                done -> RemoteImage(item.resultUrl, "You wearing ${item.saree.name}, ${drapeLabel(item.drapeStyle)} drape", Modifier.fillMaxSize(), ContentScale.Crop)
                item.isFailed -> Failed(item, onRetry)
                else -> Draping(item)
            }
        }
        if (original != null) {
            Text(
                "Your photo",
                style = MaterialTheme.typography.titleMedium,
                color = OnMirror,
                modifier = Modifier.align(Alignment.TopCenter).padding(18.dp)
                    .clip(RoundedCornerShape(50)).background(GlassStrong).padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Everything she can do with the look on screen: its name and price, shortlist it, see it in
 * another drape, jump to another look, or add more sarees.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookPanel(
    item: SessionItem?,
    items: List<SessionItem>,
    currentIndex: Int,
    error: String?,
    busy: Boolean,
    wide: Boolean,
    onFavorite: () -> Unit,
    onDrape: (String) -> Unit,
    onThumb: (Int) -> Unit,
    onTryMore: () -> Unit,
    modifier: Modifier,
) {
    GlassPanel(modifier, strong = true, padding = PaddingValues(26.dp)) {
        Column(
            Modifier.then(if (wide) Modifier.fillMaxHeight().verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ErrorNote(error)
            if (item != null) {
                val meta = listOf(item.saree.fabric, rupees(item.saree.price)).filter { it.isNotBlank() }.joinToString("  ·  ")
                val heading: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            item.saree.name, fontFamily = Rozha, fontSize = 32.sp, lineHeight = 38.sp,
                            color = OnMirror, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = OnMirrorMuted, maxLines = 1)
                    }
                }
                if (wide) {
                    heading()
                    ShortlistButton(item, onFavorite, Modifier.fillMaxWidth())
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { heading() }
                        Spacer(Modifier.width(18.dp))
                        ShortlistButton(item, onFavorite)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (busy) "Starting your new look…" else "See this saree in another drape",
                        style = MaterialTheme.typography.titleSmall, color = OnMirrorMuted,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DRAPES.forEach { d ->
                            val made = items.any { it.saree.id == item.saree.id && it.drapeStyle == d.id }
                            DrapeChip(
                                label = d.label,
                                selected = item.drapeStyle == d.id,
                                made = made,
                                enabled = !busy && (item.isDone || made),
                            ) { if (item.drapeStyle != d.id) onDrape(d.id) }
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(GlassLine))

            val thumbs: @Composable (Modifier) -> Unit = { m ->
                val strip = rememberLazyListState()
                LaunchedEffect(currentIndex) { if (items.isNotEmpty()) strip.animateScrollToItem(currentIndex.coerceAtMost(items.lastIndex)) }
                LazyRow(m, state = strip, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(items, key = { _, it -> it.id }) { index, look -> Thumb(look, index == currentIndex) { onThumb(index) } }
                }
            }
            if (wide) {
                thumbs(Modifier.fillMaxWidth())
                OutlineButton("Try more sarees", onTryMore, gold = true, icon = Icons.Filled.Add, modifier = Modifier.fillMaxWidth())
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    thumbs(Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    OutlineButton("Try more sarees", onTryMore, gold = true, icon = Icons.Filled.Add)
                }
            }
        }
    }
}

/** Big, labelled heart: "Shortlist" / "Shortlisted". Words as well as the icon, so nobody has to guess. */
@Composable
private fun ShortlistButton(item: SessionItem, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    if (!item.isDone) {
        Text(
            if (item.isFailed) "Not ready" else "Draping…",
            style = MaterialTheme.typography.titleMedium, color = OnMirrorMuted,
            modifier = modifier.clip(RoundedCornerShape(50)).background(Glass).padding(horizontal = 24.dp, vertical = 16.dp),
            textAlign = TextAlign.Center,
        )
        return
    }
    var bump by remember { mutableIntStateOf(0) }
    val heartScale by animateFloatAsState(
        targetValue = if (bump % 2 == 1) 1.3f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        finishedListener = { if (bump % 2 == 1) bump += 1 },
        label = "heart",
    )
    Button(
        onClick = { if (!item.favorite) bump += 1; onFavorite() },
        shape = RoundedCornerShape(50),
        modifier = modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (item.favorite) Kumkum else Glass,
            // white only on the red "Shortlisted"; theme text otherwise, so it reads in light mode
            contentColor = if (item.favorite) Color.White else OnMirror,
        ),
        border = if (item.favorite) null else androidx.compose.foundation.BorderStroke(1.dp, GlassLine),
    ) {
        Icon(
            if (item.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(26.dp).scale(heartScale),
        )
        Spacer(Modifier.width(10.dp))
        Text(if (item.favorite) "Shortlisted" else "Shortlist", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DrapeChip(label: String, selected: Boolean, made: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 54.dp)
            .alpha(if (enabled || selected) 1f else 0.45f)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Primary else Glass)
            .border(1.dp, if (selected) Primary else GlassLine, RoundedCornerShape(50))
            .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            selected -> {
                Icon(Icons.Filled.Check, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            made -> {
                Box(Modifier.size(9.dp).clip(CircleShape).background(PeacockDeep.copy(alpha = 1f)).border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape))
                Spacer(Modifier.width(9.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) OnPrimary else OnMirror)
    }
}

@Composable
private fun Thumb(item: SessionItem, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp, 84.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(if (selected) 3.dp else 1.dp, if (selected) ZariLight else GlassLine, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Show this look", onClick = onClick)
            .alpha(if (selected) 1f else 0.8f),
    ) {
        RemoteImage(item.resultUrl ?: item.saree.imageUrl, item.saree.name, Modifier.fillMaxSize())
        if (item.isWorking) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        when {
            item.favorite -> Badge(Kumkum, Icons.Filled.Favorite, Modifier.align(Alignment.TopEnd).padding(5.dp), size = 22.dp, tint = Color.White)
            item.isDone -> Badge(ZariLight, Icons.Filled.Check, Modifier.align(Alignment.TopEnd).padding(5.dp), size = 20.dp)
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val roomy = maxHeight > 600.dp
        RemoteImage(
            item.saree.imageUrl, null,
            Modifier.fillMaxSize().then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(26.dp) else Modifier).alpha(0.55f),
        )
        Box(Modifier.fillMaxSize().background(mirrorPalette.scrim))
        Column(
            Modifier.fillMaxSize().padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (roomy) 26.dp else 16.dp, Alignment.CenterVertically),
        ) {
            DrapingLoom(Modifier.size(if (roomy) 190.dp else 130.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (item.status == "running") "Now draping" else "Up next",
                    style = MaterialTheme.typography.titleMedium, color = ZariLight,
                )
                Text(
                    item.saree.name, fontFamily = Rozha, fontSize = 32.sp, lineHeight = 38.sp,
                    color = OnMirror, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Text("${drapeLabel(item.drapeStyle)} drape", style = MaterialTheme.typography.titleMedium, color = OnMirrorMuted)
            }
            if (item.status == "running" && roomy) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(max = 420.dp)) {
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
            Modifier.size(30.dp).clip(CircleShape)
                .background(if (done) ZariLight else Color.Transparent)
                .border(2.dp, if (done || now) ZariLight else GlassLine, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = OnGold, modifier = Modifier.size(17.dp))
            if (now) Box(Modifier.size(12.dp).clip(CircleShape).background(GoldBright.copy(alpha = lerp(0.45f, 1f, pulse))))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = when { now -> MirrorStrong; done -> OnMirror; else -> OnMirrorFaint },
        )
    }
}

@Composable
private fun Failed(item: SessionItem, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        RemoteImage(item.saree.imageUrl, item.saree.name, Modifier.size(150.dp, 190.dp).clip(RoundedCornerShape(20.dp)))
        Text("We couldn't drape this one", fontFamily = Rozha, fontSize = 30.sp, lineHeight = 36.sp, color = OnMirror, textAlign = TextAlign.Center)
        Muted("This sometimes happens with an unusual photo. Try once more.", scale = 0.85f, align = TextAlign.Center)
        GoldButton("Try again", onRetry, icon = Icons.Filled.Refresh)
    }
}
