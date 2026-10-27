package com.pallu.tryon.ui.kiosk

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pallu.tryon.ui.theme.Mirror
import com.pallu.tryon.ui.theme.MirrorLine
import com.pallu.tryon.ui.theme.MirrorRaised
import com.pallu.tryon.ui.theme.MirrorTheme
import com.pallu.tryon.ui.theme.ZariLight
import kotlinx.coroutines.delay

/**
 * Wraps every kiosk screen: the mirror theme (dark by default, light on request), screen kept on, system bars hidden,
 * and an idle timer. If a customer walks away mid-session, their photo is deleted and
 * the mirror returns to the start screen.
 */
@Composable
fun KioskFrame(
    vm: KioskViewModel,
    idleSeconds: Int,
    onIdle: () -> Unit,
    light: Boolean = false,
    onToggleLight: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val latestOnIdle = rememberUpdatedState(onIdle)
    // Customers use the on-screen buttons; the system back gesture does nothing on the mirror.
    BackHandler(enabled = true) { vm.touch() }

    DisposableEffect(view) {
        view.keepScreenOn = true
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(idleSeconds) {
        vm.touch()
        while (true) {
            delay(1000)
            val st = vm.state.value
            val idleFor = System.currentTimeMillis() - vm.lastTouch
            if (st.session != null && !st.anyWorking && !st.uploading && idleFor > idleSeconds * 1000L) {
                vm.reset()
                latestOnIdle.value()
            }
        }
    }

    MirrorTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Mirror)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            vm.touch()
                        }
                    }
                },
        ) {
            // Never draw under the status/navigation bars or a camera cut-out when Android shows them.
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
                content()
                if (onToggleLight != null) {
                    ThemeSwitch(light, onToggleLight, Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 12.dp))
                }
            }
        }
    }
}

/** Small sun / moon button in the top corner: switches the mirror between dark and light. */
@Composable
private fun ThemeSwitch(light: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MirrorRaised.copy(alpha = 0.92f))
            .border(1.dp, MirrorLine, CircleShape)
            .clickable(onClickLabel = if (light) "Switch to dark" else "Switch to light", onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (light) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
            contentDescription = if (light) "Dark theme" else "Light theme",
            tint = ZariLight,
            modifier = Modifier.size(22.dp),
        )
    }
}
