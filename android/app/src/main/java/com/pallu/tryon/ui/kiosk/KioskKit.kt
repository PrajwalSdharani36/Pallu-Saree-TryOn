package com.pallu.tryon.ui.kiosk

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pallu.tryon.data.Saree
import com.pallu.tryon.ui.common.LocalRepository
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.theme.Glass
import com.pallu.tryon.ui.theme.GlassLine
import com.pallu.tryon.ui.theme.GlassStrong
import com.pallu.tryon.ui.theme.OnGold
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.OnMirrorMuted
import com.pallu.tryon.ui.theme.OnPrimary
import com.pallu.tryon.ui.theme.Primary
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariLight
import com.pallu.tryon.ui.theme.mirrorPalette
import com.pallu.tryon.ui.theme.rememberSweep

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Space kept free at the top-right of every mirror screen for the light/dark switch (see KioskFrame). */
val ThemeSwitchRoom = 112.dp

/** Side margin of mirror screens: generous, so nothing feels crowded. */
val KioskSide = 40.dp

/**
 * Every kiosk screen: the saree in [ambient] fills the room, blurred, under a soft scrim, so the
 * mirror quietly takes on the colour of what she's looking at. Content sits on top in glass.
 */
@Composable
fun KioskPage(ambient: String? = null, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Ambient(ambient)
        content()
    }
}

/** The blurred-saree background. A tiny decode scaled up is already soft; Android 12+ blurs it further. */
@Composable
fun Ambient(path: String?, modifier: Modifier = Modifier) {
    val p = mirrorPalette
    val repo = LocalRepository.current
    val context = LocalContext.current
    Box(modifier.fillMaxSize().background(p.mirror)) {
        Crossfade(targetState = path, animationSpec = tween(900), label = "ambient") { url ->
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(repo.url(url)).size(72).build(),
                    imageLoader = repo.imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }
                        .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(40.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(p.scrim))
    }
}

/** A frosted panel. [strong] for panels that sit over a photo, where text needs more backing. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    shape: Shape = RoundedCornerShape(32.dp),
    padding: PaddingValues = PaddingValues(28.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(if (strong) GlassStrong else Glass)
            .border(1.dp, GlassLine, shape)
            .padding(padding),
        content = content,
    )
}

/**
 * The bar at the top of a mirror screen: something on the left, a title in the middle, the main
 * action on the right, and room kept for the light/dark switch.
 */
@Composable
fun KioskTopBar(
    modifier: Modifier = Modifier,
    left: @Composable RowScope.() -> Unit = {},
    right: @Composable RowScope.() -> Unit = {},
    center: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = KioskSide, end = ThemeSwitchRoom, top = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        left()
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { center() }
        right()
    }
}

/** Display sizes scale with the tablet so a 10-inch mirror reads from a metre away. */
fun headlineSize(maxWidth: Dp): Float = when {
    maxWidth >= 760.dp -> 1f
    maxWidth >= 600.dp -> 0.82f
    else -> 0.62f
}

/** The one main action on a screen: a big pill (white on the dark mirror, maroon on the light one). */
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 34.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Primary,
            contentColor = OnPrimary,
            disabledContainerColor = Primary.copy(alpha = 0.32f),
            disabledContentColor = OnPrimary.copy(alpha = 0.7f),
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

/** A secondary action: a glass pill. [gold] makes it stand out a little (e.g. "Try more sarees"). */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 60.dp),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(if (gold) 1.5.dp else 1.dp, if (gold) ZariLight.copy(alpha = 0.8f) else GlassLine),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (gold) ZariLight.copy(alpha = 0.12f) else Glass),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (gold) ZariLight else OnMirror, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (gold) ZariLight else OnMirror)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = OnMirrorMuted)
    }
}

/** The store's sarees as a little stack of cards: one in front, two tilted behind it. */
@Composable
fun SareeFan(sarees: List<Saree>, modifier: Modifier = Modifier) {
    if (sarees.isEmpty()) return
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // sized by the width AND the height it's given, so the cards never spill over the text below
        val byWidth = (maxWidth.value * 0.3f).coerceIn(120f, 250f)
        val byHeight = if (maxHeight.value.isFinite()) (maxHeight.value - 30f) / 1.45f else byWidth
        val cardW = minOf(byWidth, byHeight).coerceAtLeast(64f).dp
        val cardH = cardW * 1.45f
        val n = sarees.size.coerceAtMost(3)
        // back cards first so the front one sits on top
        val slots = when (n) {
            1 -> listOf(0)
            2 -> listOf(-1, 0)
            else -> listOf(-1, 1, 0)
        }
        slots.forEachIndexed { i, slot ->
            val saree = sarees[i]
            val front = slot == 0
            val w = if (front) cardW else cardW * 0.9f
            val h = if (front) cardH else cardH * 0.9f
            Box(
                Modifier
                    .offset(x = cardW * 0.72f * slot, y = if (front) 0.dp else 16.dp)
                    .graphicsLayer { rotationZ = slot * 9f }
                    .size(w, h)
                    .shadow(if (front) 30.dp else 18.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .border(if (front) 1.5.dp else 0.dp, if (front) Color.White.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(26.dp)),
            ) {
                RemoteImage(saree.imageUrl, null, Modifier.fillMaxSize())
            }
        }
    }
}

/** Camera window with soft white corner brackets and a gently moving scan line. */
@Composable
fun ScanWindow(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val sweep = rememberSweep(1600, "scan")
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.6f))) {
        content()
        Canvas(Modifier.fillMaxSize()) {
            val inset = size.minDimension * 0.12f
            val arm = size.minDimension * 0.16f
            val stroke = 4.dp.toPx()
            val c = Color.White
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(c, Offset(x, y), Offset(x + dx * arm, y), stroke, StrokeCap.Round)
                drawLine(c, Offset(x, y), Offset(x, y + dy * arm), stroke, StrokeCap.Round)
            }
            corner(inset, inset, 1f, 1f)
            corner(size.width - inset, inset, -1f, 1f)
            corner(inset, size.height - inset, 1f, -1f)
            corner(size.width - inset, size.height - inset, -1f, -1f)
            val y = lerp(size.height * 0.2f, size.height * 0.8f, sweep)
            val gold = Color(0xFFF0D08A)
            drawLine(gold.copy(alpha = 0.35f), Offset(inset * 1.4f, y), Offset(size.width - inset * 1.4f, y), 12.dp.toPx())
            drawLine(gold, Offset(inset * 1.4f, y), Offset(size.width - inset * 1.4f, y), 2.5.dp.toPx())
        }
    }
}

/**
 * Signature loader: a little loom. Warp threads stand still while coloured weft threads are woven
 * in one by one.
 */
@Composable
fun DrapingLoom(modifier: Modifier = Modifier, weft: List<Color> = DefaultWeft) {
    val t = rememberInfiniteTransition(label = "loom")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart),
        label = "weave",
    )
    Canvas(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(GlassStrong)
            .border(1.dp, GlassLine, RoundedCornerShape(28.dp)),
    ) {
        val pad = size.minDimension * 0.14f
        val left = pad
        val right = size.width - pad
        val top = pad * 0.9f
        val bottom = size.height - pad * 0.9f
        val warps = 10
        for (i in 0 until warps) {
            val x = lerp(left + 4f, right - 4f, i / (warps - 1f))
            drawLine(ZariLight.copy(alpha = 0.5f), Offset(x, top), Offset(x, bottom), 2.dp.toPx())
        }
        val rows = weft.size
        val stroke = ((bottom - top) / rows) * 0.45f
        val fade = if (p > 0.88f) 1f - (p - 0.88f) / 0.12f else 1f
        for (r in 0 until rows) {
            val start = r * (0.55f / rows)
            val local = ((p - start) / 0.12f).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val y = lerp(top + stroke, bottom - stroke, r / (rows - 1f))
            val x2 = lerp(left, right, local)
            drawLine(weft[r].copy(alpha = fade), Offset(left, y), Offset(x2, y), stroke, StrokeCap.Round)
        }
    }
}

val DefaultWeft = listOf(
    Color(0xFFC0304A), Color(0xFFF0D08A), Color(0xFFC0304A), Color(0xFFC0304A), Color(0xFFC0304A),
    Color(0xFFC0304A), Color(0xFFF0D08A), Color(0xFF2E8B72), Color(0xFF2E8B72),
)

/** Small round badge used on thumbnails: check (ready) or heart (shortlisted). */
@Composable
fun Badge(color: Color, icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 26.dp, tint: Color = OnGold) {
    Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
fun SectionTitle(text: String, scale: Float = 1f) {
    Text(text, fontFamily = Rozha, fontSize = (36 * scale).sp, lineHeight = (44 * scale).sp, color = OnMirror)
}

/** Supporting text. Never smaller than 18sp on the mirror. */
@Composable
fun Muted(text: String, scale: Float = 1f, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = (22 * scale).coerceAtLeast(18f).sp,
            lineHeight = (33 * scale).coerceAtLeast(27f).sp,
        ),
        color = OnMirrorMuted,
        textAlign = align,
        modifier = modifier,
    )
}

/** A numbered step label: "1  Show your code". Helps customers see where they are. */
@Composable
fun StepChip(number: Int, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, ZariLight, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", fontFamily = Rozha, fontSize = 19.sp, color = ZariLight)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = OnMirror)
    }
}

/** Column helper: glass card with a title, used by sheets and choice sections. */
@Composable
fun GlassColumn(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    spacing: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPanel(modifier, strong = strong) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}
