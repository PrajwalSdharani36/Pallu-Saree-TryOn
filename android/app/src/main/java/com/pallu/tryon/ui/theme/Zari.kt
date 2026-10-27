package com.pallu.tryon.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's signature: a woven temple border, like the edge of a Kanjivaram saree.
 * Red bands top and bottom with a row of gold "gopuram" triangles between them.
 * With [sheen], a soft band of light sweeps across it, the way zari catches shop lights.
 * [flip] mirrors it for the bottom edge of a screen.
 */
@Composable
fun ZariBorder(
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    ground: Color = TempleRed,
    band: Color = Arakku,
    gold: Color = ZariGold,
    sheen: Boolean = false,
    flip: Boolean = false,
) {
    val progress = if (sheen) {
        val t = rememberInfiniteTransition(label = "zari-sheen")
        val p by t.animateFloat(
            initialValue = -0.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(4500, easing = FastOutSlowInEasing), RepeatMode.Restart),
            label = "sheen",
        )
        p
    } else {
        null
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (flip) {
            scale(scaleX = 1f, scaleY = -1f) { drawTemple(ground, band, gold) }
        } else {
            drawTemple(ground, band, gold)
        }
        if (progress != null) {
            val w = size.width * 0.4f
            val x = progress * size.width - w / 2
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color(0x61FFECBE),
                    1f to Color.Transparent,
                    startX = x,
                    endX = x + w,
                ),
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
            )
        }
    }
}

private fun DrawScope.drawTemple(ground: Color, band: Color, gold: Color) {
    val h = size.height
    drawRect(ground)
    drawRect(band, size = Size(size.width, h * 0.2f))
    drawRect(band, topLeft = Offset(0f, h * 0.86f), size = Size(size.width, h * 0.14f))
    val top = h * 0.28f
    val bottom = h * 0.80f
    val w = (bottom - top) * 1.5f
    var x = 0f
    while (x < size.width) {
        val big = Path().apply { moveTo(x, bottom); lineTo(x + w / 2, top); lineTo(x + w, bottom); close() }
        drawPath(big, gold)
        val inner = Path().apply {
            moveTo(x + w * 0.32f, bottom); lineTo(x + w / 2, top + (bottom - top) * 0.55f)
            lineTo(x + w * 0.68f, bottom); close()
        }
        drawPath(inner, band)
        x += w
    }
}

/** A small folded-saree mark used next to the store name. */
@Composable
fun SareeMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Arakku, topLeft = Offset(w * 0.13f, h * 0.1f), size = Size(w * 0.74f, h * 0.8f))
        drawRect(Color(0xFF135C4C), topLeft = Offset(w * 0.13f, h * 0.63f), size = Size(w * 0.74f, h * 0.27f))
        val tri = w * 0.74f / 3
        for (i in 0 until 3) {
            val x0 = w * 0.13f + i * tri
            drawPath(Path().apply {
                moveTo(x0, h * 0.87f); lineTo(x0 + tri / 2, h * 0.7f); lineTo(x0 + tri, h * 0.87f); close()
            }, ZariLight)
        }
    }
}

/** Repeating light sweep 0..1, for scan lines and similar small motions. */
@Composable
fun rememberSweep(durationMs: Int, label: String): Float {
    val t = rememberInfiniteTransition(label = label)
    val v by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Reverse),
        label = label,
    )
    return v
}
