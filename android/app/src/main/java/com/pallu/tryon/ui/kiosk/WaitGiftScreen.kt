package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pallu.tryon.ui.common.ErrorNote
import com.pallu.tryon.ui.common.RemoteImage
import com.pallu.tryon.ui.theme.GlassLine
import com.pallu.tryon.ui.theme.GlassStrong
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.Rozha

/**
 * Gift mode on the mirror. The person the saree is for isn't here, and their photo is on the
 * customer's phone, not on the kiosk. So the customer sends it to the staff member (WhatsApp or
 * similar), who adds it from the staff app; the looks start here as soon as it arrives.
 */
@Composable
fun WaitGiftScreen(
    vm: KioskViewModel,
    onArrived: () -> Unit,
    onUseThisDevice: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        vm.waitForGiftPhoto(onArrived)
        onDispose { }
    }

    KioskPage(ambient = state.session?.items?.firstOrNull()?.saree?.imageUrl) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val k = headlineSize(maxWidth)
            val wide = maxWidth > maxHeight
            val story: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
                    DrapingLoom(Modifier.size(if (wide) 150.dp else 180.dp))
                    Text(
                        "Send their photo to our staff",
                        fontFamily = Rozha, fontSize = (54 * k * (if (wide) 0.85f else 1f)).sp, lineHeight = (60 * k * (if (wide) 0.85f else 1f)).sp,
                        color = OnMirror, textAlign = TextAlign.Center,
                    )
                    Muted(
                        "WhatsApp a clear photo of them to the staff member who helped you. " +
                            "It appears here by itself and their looks start straight away.",
                        scale = k * 0.95f, align = TextAlign.Center,
                    )
                }
            }
            val actions: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    state.session?.let { s ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            s.items.take(5).forEach { item ->
                                RemoteImage(
                                    item.saree.imageUrl, item.saree.name,
                                    Modifier.size(84.dp, 108.dp).clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, GlassLine, RoundedCornerShape(16.dp)),
                                )
                            }
                        }
                        Text(
                            "Your code is ${s.code}",
                            style = MaterialTheme.typography.titleMedium, color = OnMirror,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(GlassStrong)
                                .padding(horizontal = 22.dp, vertical = 10.dp),
                        )
                    }
                    ErrorNote(state.error)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlineButton("Choose a photo on this screen instead", { vm.stopWaiting(); onUseThisDevice() })
                        QuietButton("Cancel") { vm.stopWaiting(); onCancel() }
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = KioskSide, vertical = if (wide) 100.dp else 110.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (wide) {
                    // landscape: side by side, so the buttons are on screen without scrolling
                    GlassPanel(Modifier.widthIn(max = 1120.dp), padding = PaddingValues(horizontal = 44.dp, vertical = 40.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { story() }
                            Box(Modifier.weight(1f)) { actions() }
                        }
                    }
                } else {
                    GlassPanel(Modifier.widthIn(max = 720.dp), padding = PaddingValues(horizontal = 40.dp, vertical = 44.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                            story()
                            actions()
                        }
                    }
                }
            }
        }
    }
}
