package com.pallu.tryon.ui.kiosk

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import com.pallu.tryon.ui.theme.MirrorTheme
import kotlinx.coroutines.delay

/**
 * Wraps every kiosk screen: dark trial-room theme, screen kept on, system bars hidden,
 * and an idle timer. If a customer walks away mid-session, their photo is deleted and
 * the mirror returns to the start screen.
 */
@Composable
fun KioskFrame(
    vm: KioskViewModel,
    idleSeconds: Int,
    onIdle: () -> Unit,
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
        ) { content() }
    }
}
