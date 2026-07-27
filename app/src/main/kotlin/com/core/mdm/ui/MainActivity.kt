package com.core.mdm.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.core.mdm.firebase.AuthRepository
import com.core.mdm.security.AppLockState
import com.core.mdm.security.PinManager
import com.core.mdm.service.MdmCommandService
import com.core.mdm.settings.ThemePrefs
import com.core.mdm.ui.apps.AppsScreen
import com.core.mdm.ui.dashboard.DashboardScreen
import com.core.mdm.ui.filter.FilterScreen
import com.core.mdm.ui.kiosk.KioskScreen
import com.core.mdm.ui.login.LoginScreen
import com.core.mdm.ui.pin.PinLockScreen
import com.core.mdm.ui.pin.PinLockViewModel
import com.core.mdm.ui.pin.PinScreenMode
import com.core.mdm.ui.provisioning.ProvisioningState
import com.core.mdm.ui.provisioning.ProvisioningTokenDialog
import com.core.mdm.ui.provisioning.ProvisioningViewModel
import com.core.mdm.ui.remote.RemoteControlScreen
import com.core.mdm.ui.telemetry.TelemetryScreen
import com.core.mdm.ui.theme.CoreMdmTheme
import com.core.mdm.ui.theme.ThemeMode

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_APPS      = "apps"
private const val ROUTE_PIN_SETUP = "pin_setup"
private const val ROUTE_FILTER    = "filter"
private const val ROUTE_KIOSK     = "kiosk"
private const val ROUTE_TELEMETRY = "telemetry"
private const val ROUTE_REMOTE    = "remote"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePrefs.init(this)
        enableEdgeToEdge()
        setContent {
            val themeMode by ThemePrefs.themeMode.collectAsState()
            CoreMdmTheme(themeMode = themeMode) {
                AppRoot()
            }
        }
    }
}

// ── Auth gate ─────────────────────────────────────────────────────────────────

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val user by AuthRepository.authState.collectAsState(initial = AuthRepository.currentUser)

    AnimatedContent(
        targetState = user != null,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
        label = "auth_gate"
    ) { isSignedIn ->
        if (!isSignedIn) {
            // Stop service whenever user is signed out
            DisposableEffect(Unit) {
                onDispose { MdmCommandService.stop(context) }
            }
            LoginScreen()
        } else {
            // Start service once authenticated
            LaunchedEffect(Unit) { MdmCommandService.start(context) }
            val provVm: ProvisioningViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val provState by provVm.state.collectAsState()
            MdmRoot()
            if (provState.showDialog) {
                ProvisioningTokenDialog(
                    state         = provState,
                    onTokenChange = provVm::onTokenChange,
                    onVerify      = provVm::verify,
                    onSkip        = provVm::skip,
                )
            }
        }
    }
}

// ── Root: lock gate + process-lifecycle auto-lock ─────────────────────────────

@Composable
private fun MdmRoot() {
    val context    = LocalContext.current
    val pinManager = remember { PinManager.getInstance(context) }
    val isLocked   by AppLockState.isLocked.collectAsState()

    DisposableEffect(Unit) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (pinManager.isPinSet) AppLockState.lock()
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
    }

    AnimatedContent(
        targetState  = isLocked,
        transitionSpec = {
            if (targetState) {
                fadeIn(tween(200)) togetherWith fadeOut(tween(100))
            } else {
                (slideInVertically(tween(320)) { it / 4 } + fadeIn(tween(320))) togetherWith
                        fadeOut(tween(150))
            }
        },
        label = "lock_gate"
    ) { locked ->
        if (locked) {
            PinLockScreen(
                preventBack      = true,
                onAuthenticated  = { AppLockState.unlock() },
                viewModel        = androidx.lifecycle.viewmodel.compose.viewModel(
                    key     = "lock_overlay",
                    factory = PinLockViewModel.factory(PinScreenMode.VERIFY)
                )
            )
        } else {
            MdmNavGraph(pinManager = pinManager)
        }
    }
}

// ── Navigation graph ──────────────────────────────────────────────────────────

@Composable
private fun MdmNavGraph(pinManager: PinManager) {
    val context       = LocalContext.current
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_DASHBOARD,
        enterTransition  = { slideInHorizontally(tween(280)) { it } + fadeIn(tween(280)) },
        exitTransition   = { slideOutHorizontally(tween(280)) { -it / 3 } + fadeOut(tween(180)) },
        popEnterTransition  = { slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(280)) },
        popExitTransition   = { slideOutHorizontally(tween(280)) { it } + fadeOut(tween(180)) }
    ) {
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                onNavigateToApps      = { navController.navigate(ROUTE_APPS) },
                onNavigateToPinSetup  = { navController.navigate(ROUTE_PIN_SETUP) },
                onNavigateToFilter    = { navController.navigate(ROUTE_FILTER) },
                onNavigateToKiosk     = { navController.navigate(ROUTE_KIOSK) },
                onNavigateToTelemetry = { navController.navigate(ROUTE_TELEMETRY) },
                onNavigateToRemote    = { navController.navigate(ROUTE_REMOTE) },
                onSignOut = {
                    MdmCommandService.stop(context)
                    AuthRepository.signOut()
                }
            )
        }

        composable(ROUTE_APPS) {
            AppsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(ROUTE_FILTER) {
            FilterScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(ROUTE_KIOSK) {
            KioskScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(ROUTE_TELEMETRY) {
            TelemetryScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(ROUTE_REMOTE) {
            RemoteControlScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(ROUTE_PIN_SETUP) {
            val mode = if (pinManager.isPinSet) PinScreenMode.CHANGE_VERIFY else PinScreenMode.SETUP
            PinLockScreen(
                preventBack = false,
                onComplete  = { navController.popBackStack() },
                viewModel   = androidx.lifecycle.viewmodel.compose.viewModel(
                    key     = "pin_setup",
                    factory = PinLockViewModel.factory(mode)
                )
            )
        }
    }
}
