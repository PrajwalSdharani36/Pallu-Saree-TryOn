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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pallu.tryon.ui.theme.Mirror
import com.pallu.tryon.ui.theme.GlassLine
import com.pallu.tryon.ui.theme.GlassStrong
import com.pallu.tryon.ui.theme.OnMirror
import com.pallu.tryon.ui.theme.MirrorTheme
import kotlinx.coroutines.delay

/**
 * Wraps every kiosk screen: the mirror theme (dark by default, light on request), screen kept on,
 * and an idle timer (the status and navigation bars are hidden by [SystemBars]). If a customer walks away mid-session, their photo is deleted and
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
        onDispose { view.keepScreenOn = false }
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
                    // lines up with KioskTopBar, which keeps ThemeSwitchRoom free on the right
                    ThemeSwitch(light, onToggleLight, Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = KioskSide))
                }
            }
        }
    }
}

/**
 * One place that decides the system bars for the whole app. On the mirror they're hidden (a swipe
 * shows them briefly, with icons that suit the theme); on the staff phone they're shown with light
 * icons over the dark header. Re-applied whenever the app comes back to the front, because Android
 * shows the bars again after another app or the lock screen.
 */
@Composable
fun SystemBars(kiosk: Boolean, light: Boolean) {
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(kiosk, light, lifecycle) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val c = WindowCompat.getInsetsController(window, view)
        fun apply() {
            if (kiosk) {
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                c.show(WindowInsetsCompat.Type.systemBars())
            }
            c.isAppearanceLightStatusBars = kiosk && light
            c.isAppearanceLightNavigationBars = kiosk && light
        }
        apply()
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) apply() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
}

/** Small sun / moon button in the top corner: switches the mirror between dark and light. */
@Composable
private fun ThemeSwitch(light: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(GlassStrong)
            .border(1.dp, GlassLine, CircleShape)
            .clickable(onClickLabel = if (light) "Switch to dark" else "Switch to light", onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (light) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
            contentDescription = if (light) "Dark theme" else "Light theme",
            tint = OnMirror,
            modifier = Modifier.size(26.dp),
        )
    }
}
