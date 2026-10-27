package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pallu.tryon.data.Saree
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.theme.GoldBright
import com.pallu.tryon.ui.theme.Mirror
import com.pallu.tryon.ui.theme.MirrorDeep
import com.pallu.tryon.ui.theme.MirrorLine
import com.pallu.tryon.ui.theme.OnGold
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.OnMirrorMuted
import com.pallu.tryon.ui.theme.PeacockDeep
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariBorder
import com.pallu.tryon.ui.theme.ZariLight
import com.pallu.tryon.ui.theme.mirrorPalette
import com.pallu.tryon.ui.theme.rememberSweep
import kotlinx.coroutines.delay

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Every kiosk screen sits between two woven borders; the top one catches the light. */
@Composable
fun KioskPage(content: @Composable () -> Unit) {
    val p = mirrorPalette
    val h = if (p.light) 24.dp else 30.dp
    Column(Modifier.fillMaxSize().background(Mirror)) {
        ZariBorder(height = h, ground = p.borderGround, band = p.borderBand, gold = p.borderGold, sheen = !p.light)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        ZariBorder(height = h, ground = p.borderGround, band = p.borderBand, gold = p.borderGold, flip = true)
    }
}

/** Display sizes scale with the tablet so a 10-inch mirror reads from a metre away. */
fun headlineSize(maxWidth: Dp): Float = when {
    maxWidth >= 760.dp -> 1f
    maxWidth >= 600.dp -> 0.82f
    else -> 0.62f
}

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
        modifier = modifier.heightIn(min = 60.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 30.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ZariLight,
            contentColor = OnGold,
            disabledContainerColor = ZariLight.copy(alpha = 0.3f),
            disabledContentColor = OnGold.copy(alpha = 0.6f),
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, gold: Boolean = false, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (gold) ZariLight else MirrorLine),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = OnMirror)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = OnMirrorMuted)
    }
}

/** The store's sarees fanned out like they are on the counter, the centre one framed in gold. */
@Composable
fun SareeFan(sarees: List<Saree>, modifier: Modifier = Modifier) {
    if (sarees.isEmpty()) return
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // sized by the width AND the height it's given, so the fanned cards never spill over the text below
        val byWidth = (maxWidth.value * 0.27f).coerceIn(120f, 220f)
        val byHeight = if (maxHeight.value.isFinite()) (maxHeight.value - 56f) / 1.24f / 1.06f else byWidth
        val cardW = minOf(byWidth, byHeight).coerceAtLeast(64f).dp
        val cardH = cardW * 1.24f
        // order: outer cards first so the centre one sits on top
        val n = sarees.size.coerceAtMost(5)
        val slots = when (n) {
            1 -> listOf(0)
            2 -> listOf(-1, 1)
            3 -> listOf(-1, 1, 0)
            4 -> listOf(-2, 2, -1, 1)
            else -> listOf(-2, 2, -1, 1, 0)
        }
        slots.forEachIndexed { i, slot ->
            val saree = sarees[i]
            val centre = slot == 0
            Box(
                Modifier
                    .offset(x = cardW * 0.62f * slot, y = (if (kotlin.math.abs(slot) == 2) 34 else if (slot == 0) 0 else 14).dp)
                    .graphicsLayer { rotationZ = slot * 7.5f }
                    .size(cardW, cardH)
                    .shadow(if (centre) 26.dp else 16.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .border(if (centre) 2.dp else 1.dp, if (centre) ZariLight else ZariLight.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
            ) {
                RemoteImage(saree.imageUrl, null, Modifier.fillMaxSize())
            }
        }
    }
}

/** Black camera window with gold corner brackets and a moving scan line. */
@Composable
fun ScanWindow(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val sweep = rememberSweep(1400, "scan")
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(MirrorDeep)) {
        content()
        Canvas(Modifier.fillMaxSize()) {
            val inset = size.minDimension * 0.08f
            val arm = size.minDimension * 0.17f
            val stroke = 4.dp.toPx()
            val c = ZariLight
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(c, Offset(x, y), Offset(x + dx * arm, y), stroke, StrokeCap.Round)
                drawLine(c, Offset(x, y), Offset(x, y + dy * arm), stroke, StrokeCap.Round)
            }
            corner(inset, inset, 1f, 1f)
            corner(size.width - inset, inset, -1f, 1f)
            corner(inset, size.height - inset, 1f, -1f)
            corner(size.width - inset, size.height - inset, -1f, -1f)
            val y = lerp(size.height * 0.15f, size.height * 0.85f, sweep)
            drawLine(GoldBright.copy(alpha = 0.35f), Offset(inset * 1.6f, y), Offset(size.width - inset * 1.6f, y), 10.dp.toPx())
            drawLine(GoldBright, Offset(inset * 1.6f, y), Offset(size.width - inset * 1.6f, y), 2.dp.toPx())
        }
    }
}

/** Six letter boxes for the session code, backed by one text field (so the keyboard just works). */
@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
fun CodeBoxes(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit, boxSize: Dp = 48.dp) {
    // When the keyboard opens, scroll the boxes into view so customers can see what they type.
    val bring = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboardUp = WindowInsets.isImeVisible
    LaunchedEffect(focused, keyboardUp) {
        if (focused && keyboardUp) {
            delay(250) // let the keyboard finish sliding in
            bring.bringIntoView()
        }
    }
    BasicTextField(
        modifier = Modifier.bringIntoViewRequester(bring).onFocusChanged { focused = it.isFocused },
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isLetterOrDigit() }.uppercase().take(6)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onDone() }),
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
        decorationBox = { inner ->
            Box {
                Box(Modifier.size(1.dp)) { inner() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(6) { i ->
                        val ch = value.getOrNull(i)?.toString().orEmpty()
                        val active = i == value.length
                        Box(
                            Modifier
                                .size(boxSize, boxSize * 1.3f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Mirror)
                                .border(if (active) 2.dp else 1.dp, if (active) ZariLight else MirrorLine, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(ch, fontFamily = Rozha, fontSize = (boxSize.value * 0.58f).sp, color = OnMirror)
                        }
                    }
                }
            }
        },
    )
}

/**
 * Signature loader: a little loom. Gold warp threads stand still while coloured weft threads
 * are woven in one by one, in the colours of the saree being draped.
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
            .clip(RoundedCornerShape(20.dp))
            .background(MirrorDeep.copy(alpha = 0.6f))
            .border(1.dp, ZariLight.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
    ) {
        val pad = size.minDimension * 0.12f
        val left = pad
        val right = size.width - pad
        val top = pad * 0.9f
        val bottom = size.height - pad * 0.9f
        val warps = 10
        for (i in 0 until warps) {
            val x = lerp(left + 4f, right - 4f, i / (warps - 1f))
            drawLine(ZariLight.copy(alpha = 0.55f), Offset(x, top), Offset(x, bottom), 2.dp.toPx())
        }
        val rows = weft.size
        val stroke = ((bottom - top) / rows) * 0.45f
        // weave in during the first 55% of the cycle, hold, then fade out together
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
    Color(0xFFB3243C), ZariLight, Color(0xFFB3243C), Color(0xFFB3243C), Color(0xFFB3243C),
    Color(0xFFB3243C), ZariLight, PeacockDeep, PeacockDeep,
)

/** Small round badge used on thumbnails: gold check (ready) or heart (shortlisted). */
@Composable
fun Badge(color: Color, icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = OnGold) {
    Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
fun SectionTitle(text: String, scale: Float = 1f) {
    Text(text, fontFamily = Rozha, fontSize = (36 * scale).sp, lineHeight = (42 * scale).sp, color = OnMirror)
}

@Composable
fun Muted(text: String, scale: Float = 1f, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = (22 * scale).coerceAtLeast(16f).sp, lineHeight = (32 * scale).coerceAtLeast(24f).sp),
        color = OnMirrorMuted,
        textAlign = align,
        modifier = modifier,
    )
}
