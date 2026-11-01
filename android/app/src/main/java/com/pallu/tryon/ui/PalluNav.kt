package com.pallu.tryon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pallu.tryon.data.AppContainer
import com.pallu.tryon.data.AppMode
import com.pallu.tryon.data.AppSettings
import com.pallu.tryon.ui.common.LocalRepository
import com.pallu.tryon.ui.kiosk.AttractScreen
import com.pallu.tryon.ui.kiosk.CaptureScreen
import com.pallu.tryon.ui.kiosk.ConsentScreen
import com.pallu.tryon.ui.kiosk.KioskFrame
import com.pallu.tryon.ui.kiosk.KioskViewModel
import com.pallu.tryon.ui.kiosk.ResultsScreen
import com.pallu.tryon.ui.kiosk.SystemBars
import com.pallu.tryon.ui.kiosk.ThankYouScreen
import com.pallu.tryon.ui.kiosk.TryOnMode
import com.pallu.tryon.ui.kiosk.WaitGiftScreen
import com.pallu.tryon.ui.setup.SetupScreen
import com.pallu.tryon.ui.staff.ScanScreen
import com.pallu.tryon.ui.staff.SessionScreen
import com.pallu.tryon.ui.staff.StaffHomeScreen
import com.pallu.tryon.ui.staff.StaffViewModel
import com.pallu.tryon.ui.theme.DarkMirror
import com.pallu.tryon.ui.theme.LightMirror
import com.pallu.tryon.ui.theme.PalluTheme
import com.pallu.tryon.ui.theme.mirrorPalette
import kotlinx.coroutines.launch
import com.pallu.tryon.ui.theme.Silk

object Routes {
    const val SETUP = "setup"
    const val STAFF = "staff"
    const val STAFF_SCAN = "staff/scan"
    const val STAFF_SESSION = "staff/session/{id}"
    fun staffSession(id: String) = "staff/session/$id"
    const val KIOSK = "kiosk"
    const val KIOSK_CONSENT = "kiosk/consent"
    const val KIOSK_CAPTURE = "kiosk/capture"
    const val KIOSK_RESULTS = "kiosk/results"
    const val KIOSK_THANKS = "kiosk/thanks"
    const val KIOSK_WAIT_GIFT = "kiosk/wait-gift"
}

@Composable
fun PalluNav(container: AppContainer) {
    val settings: AppSettings? by container.settingsStore.settings.collectAsState(initial = null)
    PalluTheme {
        val s = settings
        if (s == null) {
            Box(Modifier.fillMaxSize().background(Silk), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@PalluTheme
        }
        val repo = remember(s.serverUrl, s.apiKey) { container.repository(s) }
        LaunchedEffect(s.kioskLight) { mirrorPalette = if (s.kioskLight) LightMirror else DarkMirror }
        val start = remember {
            when {
                !s.isConfigured -> Routes.SETUP
                s.mode == AppMode.KIOSK -> Routes.KIOSK
                else -> Routes.STAFF
            }
        }
        CompositionLocalProvider(LocalRepository provides repo) {
            val nav = rememberNavController()
            val staffVm: StaffViewModel = viewModel { StaffViewModel(container) }
            val kioskVm: KioskViewModel = viewModel { KioskViewModel(container) }
            Graph(nav, start, s, container, staffVm, kioskVm)
        }
    }
}

@Composable
private fun Graph(
    nav: NavHostController,
    start: String,
    settings: AppSettings,
    container: AppContainer,
    staffVm: StaffViewModel,
    kioskVm: KioskViewModel,
) {
    fun goRoot(route: String) = nav.navigate(route) { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
    val scope = rememberCoroutineScope()
    val toggleLight: () -> Unit = {
        val light = !settings.kioskLight
        mirrorPalette = if (light) LightMirror else DarkMirror // repaint at once; the save follows
        scope.launch { container.settingsStore.setKioskLight(light) }
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: start
    SystemBars(kiosk = route.startsWith(Routes.KIOSK), light = settings.kioskLight)

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SETUP) {
            SetupScreen(container = container, initial = settings) { mode ->
                goRoot(if (mode == AppMode.KIOSK) Routes.KIOSK else Routes.STAFF)
            }
        }

        // ---------------- staff ----------------
        composable(Routes.STAFF) {
            StaffHomeScreen(
                vm = staffVm,
                storeName = settings.storeName,
                onNewCustomer = { staffVm.startNew(); nav.navigate(Routes.STAFF_SCAN) },
                onOpenSession = { nav.navigate(Routes.staffSession(it)) },
                onSettings = { nav.navigate(Routes.SETUP) },
            )
        }
        composable(Routes.STAFF_SCAN) {
            ScanScreen(
                vm = staffVm,
                onBack = { nav.popBackStack() },
                onSessionReady = { id ->
                    nav.navigate(Routes.staffSession(id)) { popUpTo(Routes.STAFF) }
                },
            )
        }
        composable(Routes.STAFF_SESSION, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            SessionScreen(
                vm = staffVm,
                sessionId = id,
                onBack = { nav.popBackStack(Routes.STAFF, inclusive = false) },
                onScanMore = { staffVm.startAdding(id); nav.navigate(Routes.STAFF_SCAN) },
                onNewCustomer = { staffVm.startNew(); nav.navigate(Routes.STAFF_SCAN) { popUpTo(Routes.STAFF) } },
            )
        }

        // ---------------- kiosk ----------------
        composable(Routes.KIOSK) {
            KioskFrame(kioskVm, idleSeconds = settings.kioskIdleSeconds, onIdle = {}, light = settings.kioskLight, onToggleLight = toggleLight) {
                AttractScreen(
                    vm = kioskVm,
                    storeName = settings.storeName,
                    apiKey = settings.apiKey,
                    scanFront = settings.scanFront,
                    onScanFront = { front -> scope.launch { container.settingsStore.setCameras(scanFront = front) } },
                    onSession = { hasPhoto ->
                        // a staff member may already have added a gift photo: go straight to the looks
                        nav.navigate(if (hasPhoto) Routes.KIOSK_RESULTS else Routes.KIOSK_CONSENT)
                    },
                    onExitKiosk = { nav.navigate(Routes.SETUP) },
                )
            }
        }
        composable(Routes.KIOSK_CONSENT) {
            KioskFrame(kioskVm, settings.kioskIdleSeconds, onIdle = { goRoot(Routes.KIOSK) }, light = settings.kioskLight, onToggleLight = toggleLight) {
                ConsentScreen(
                    vm = kioskVm,
                    scanFront = settings.scanFront,
                    onScanFront = { front -> scope.launch { container.settingsStore.setCameras(scanFront = front) } },
                    onBack = { kioskVm.reset(); goRoot(Routes.KIOSK) },
                    onContinue = {
                        val gift = kioskVm.state.value.mode == TryOnMode.GIFT
                        nav.navigate(if (gift) Routes.KIOSK_WAIT_GIFT else Routes.KIOSK_CAPTURE)
                    },
                )
            }
        }
        composable(Routes.KIOSK_CAPTURE) {
            KioskFrame(kioskVm, settings.kioskIdleSeconds, onIdle = { goRoot(Routes.KIOSK) }, light = settings.kioskLight, onToggleLight = toggleLight) {
                CaptureScreen(
                    vm = kioskVm,
                    photoFront = settings.photoFront,
                    onPhotoFront = { front -> scope.launch { container.settingsStore.setCameras(photoFront = front) } },
                    onBack = { nav.popBackStack() },
                    onUploaded = { nav.navigate(Routes.KIOSK_RESULTS) { popUpTo(Routes.KIOSK_CONSENT) { inclusive = true } } },
                )
            }
        }
        composable(Routes.KIOSK_WAIT_GIFT) {
            KioskFrame(kioskVm, settings.kioskIdleSeconds * 3, onIdle = { goRoot(Routes.KIOSK) }, light = settings.kioskLight, onToggleLight = toggleLight) {
                WaitGiftScreen(
                    vm = kioskVm,
                    onArrived = { nav.navigate(Routes.KIOSK_RESULTS) { popUpTo(Routes.KIOSK_CONSENT) { inclusive = true } } },
                    onUseThisDevice = { nav.navigate(Routes.KIOSK_CAPTURE) },
                    onCancel = { nav.popBackStack() },
                )
            }
        }
        composable(Routes.KIOSK_RESULTS) {
            KioskFrame(kioskVm, settings.kioskIdleSeconds, onIdle = { goRoot(Routes.KIOSK) }, light = settings.kioskLight, onToggleLight = toggleLight) {
                ResultsScreen(
                    vm = kioskVm,
                    scanFront = settings.scanFront,
                    onScanFront = { front -> scope.launch { container.settingsStore.setCameras(scanFront = front) } },
                    onRetake = { nav.navigate(Routes.KIOSK_CAPTURE) },
                    onFinished = { nav.navigate(Routes.KIOSK_THANKS) { popUpTo(Routes.KIOSK) } },
                )
            }
        }
        composable(Routes.KIOSK_THANKS) {
            KioskFrame(kioskVm, settings.kioskIdleSeconds, onIdle = { goRoot(Routes.KIOSK) }, light = settings.kioskLight, onToggleLight = toggleLight) {
                ThankYouScreen(vm = kioskVm, onDone = { kioskVm.reset(); goRoot(Routes.KIOSK) })
            }
        }
    }
}
