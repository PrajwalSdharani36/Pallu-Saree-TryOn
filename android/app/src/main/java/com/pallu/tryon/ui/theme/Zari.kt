package com.pallu.tryon.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's signature: a woven temple border, like the edge of a Kanjivaram saree.
 * Red bands top and bottom with a row of gold "gopuram" triangles between them.
 */
@Composable
fun ZariBorder(
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    ground: Color = ArakkuDeep,
    band: Color = Arakku,
    gold: Color = ZariLight,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val h = size.height
        drawRect(ground)
        drawRect(band, size = Size(size.width, h * 0.22f))
        drawRect(band, topLeft = Offset(0f, h * 0.86f), size = Size(size.width, h * 0.14f))
        val top = h * 0.30f
        val bottom = h * 0.86f
        val w = (bottom - top) * 1.6f
        var x = 0f
        while (x < size.width) {
            val big = Path().apply {
                moveTo(x, bottom); lineTo(x + w / 2, top); lineTo(x + w, bottom); close()
            }
            drawPath(big, gold)
            val inner = Path().apply {
                moveTo(x + w * 0.32f, bottom); lineTo(x + w / 2, top + (bottom - top) * 0.45f)
                lineTo(x + w * 0.68f, bottom); close()
            }
            drawPath(inner, band)
            x += w
        }
    }
}
