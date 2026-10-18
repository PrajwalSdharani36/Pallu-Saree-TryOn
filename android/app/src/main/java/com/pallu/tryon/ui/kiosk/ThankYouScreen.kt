package com.pallu.tryon.ui.kiosk

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.common.rupees
import com.pallu.tryon.ui.theme.Arakku
import com.pallu.tryon.ui.theme.Kumkum
import com.pallu.tryon.ui.theme.MirrorLine
import com.pallu.tryon.ui.theme.MirrorRaised
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.OnMirrorMuted
import com.pallu.tryon.ui.theme.PeacockDeep
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariLight
import kotlinx.coroutines.delay

private const val RESET_SECONDS = 20

@Composable
fun ThankYouScreen(vm: KioskViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var seconds by remember { mutableIntStateOf(RESET_SECONDS) }
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds -= 1
        }
        onDone()
    }
    val ring by animateFloatAsState(seconds / RESET_SECONDS.toFloat(), tween(1000, easing = LinearEasing), label = "reset-ring")

    KioskPage {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // landscape tablets are short: smaller title and thumbnails so "Start over" stays on screen
            val short = maxHeight < 900.dp
            val k = headlineSize(maxWidth) * (if (short) 0.72f else 1f)
            val thumbW = if (short) 140.dp else 200.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 56.dp * k.coerceAtLeast(0.6f), vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (short) 14.dp else 24.dp, Alignment.CenterVertically),
            ) {
                TempleMotif(Modifier.size(120.dp * k, 64.dp * k))
                Text("Thank you", fontFamily = Rozha, fontSize = (96 * k).sp, lineHeight = (100 * k).sp, color = OnMirror)
                Text(
                    "Your selections have been saved.",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = (28 * k).sp),
                    color = OnMirror, textAlign = TextAlign.Center,
                )

                if (state.shortlist.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(MirrorRaised)
                            .border(1.dp, ZariLight.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
                            .padding(if (short) 18.dp else 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (short) 12.dp else 20.dp),
                    ) {
                        Muted("Our staff have your shortlist and will bring these out for you", scale = 0.9f, align = TextAlign.Center)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                            items(state.shortlist, key = { it.id }) { s ->
                                Column(Modifier.width(thumbW), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box {
                                        RemoteImage(
                                            s.imageUrl, s.name,
                                            Modifier.size(thumbW, thumbW * 1.24f).clip(RoundedCornerShape(12.dp))
                                                .border(2.dp, ZariLight, RoundedCornerShape(12.dp)),
                                        )
                                        Badge(Kumkum, Icons.Filled.Favorite, Modifier.align(Alignment.TopEnd).padding(10.dp), size = 34.dp, tint = Color.White)
                                    }
                                    Text(
                                        s.name, fontFamily = Rozha, fontSize = 21.sp, lineHeight = 25.sp, color = OnMirror,
                                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    if (s.price > 0) Text(rupees(s.price), style = MaterialTheme.typography.bodyLarge, color = OnMirrorMuted)
                                }
                            }
                        }
                    }
                } else {
                    Muted("Our staff can help you choose, or bring out more sarees to try.", align = TextAlign.Center)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(PeacockDeep, Icons.Filled.Check, size = 28.dp, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Your photo and the looks have been deleted from this mirror.",
                        style = MaterialTheme.typography.bodyLarge, color = OnMirrorMuted,
                    )
                }

                OutlinedButton(
                    onClick = onDone,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ZariLight),
                    modifier = Modifier.widthIn(min = 260.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                ) {
                    Canvas(Modifier.size(28.dp)) {
                        val stroke = 3.dp.toPx()
                        drawCircle(MirrorLine, style = Stroke(stroke))
                        drawArc(
                            ZariLight, -90f, 360f * ring, false,
                            topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("Start over ($seconds)", style = MaterialTheme.typography.titleLarge, color = OnMirror)
                }
            }
        }
    }
}

/** Three temple-border gopurams, echoing the woven edge. */
@Composable
private fun TempleMotif(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width / 3
        val h = size.height
        for (i in 0 until 3) {
            val x = i * w
            val top = if (i == 1) 0f else h * 0.22f
            drawPath(Path().apply { moveTo(x, h); lineTo(x + w / 2, top); lineTo(x + w, h); close() }, ZariLight)
            drawPath(Path().apply {
                moveTo(x + w * 0.3f, h); lineTo(x + w / 2, top + (h - top) * 0.5f); lineTo(x + w * 0.7f, h); close()
            }, Arakku)
        }
    }
}
