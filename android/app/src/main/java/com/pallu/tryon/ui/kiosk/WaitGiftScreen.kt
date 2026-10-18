package com.pallu.tryon.ui.kiosk

import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.Rozha
import com.pallu.tryon.ui.theme.ZariLight

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

    KioskPage {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val k = headlineSize(maxWidth)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 56.dp * k.coerceAtLeast(0.6f), vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically),
            ) {
                DrapingLoom(Modifier.size(200.dp))
                Text(
                    "Send their photo to our staff",
                    fontFamily = Rozha, fontSize = (60 * k).sp, lineHeight = (66 * k).sp,
                    color = OnMirror, textAlign = TextAlign.Center,
                )
                Muted(
                    "WhatsApp a clear photo of them to the staff member who helped you. " +
                        "It appears here automatically and their looks start straight away.",
                    scale = k, align = TextAlign.Center, modifier = Modifier.widthIn(max = 640.dp),
                )
                state.session?.let { s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        s.items.take(6).forEach { item ->
                            RemoteImage(
                                item.saree.imageUrl, item.saree.name,
                                Modifier.size(84.dp, 106.dp).clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, ZariLight.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                    Muted("Your code is ${s.code}", scale = 0.8f)
                }
                ErrorNote(state.error, Modifier.widthIn(max = 560.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    QuietButton("Cancel") { vm.stopWaiting(); onCancel() }
                    OutlineButton("Choose a photo on this screen instead", { vm.stopWaiting(); onUseThisDevice() })
                }
            }
        }
    }
}
