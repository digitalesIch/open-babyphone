package org.openbabyphone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import org.openbabyphone.navigation.Discover
import org.openbabyphone.navigation.DiscoverAddress
import org.openbabyphone.navigation.DiscoverWifiDirect
import org.openbabyphone.navigation.ConnectionHelp
import org.openbabyphone.navigation.ConnectionHelpMode
import org.openbabyphone.navigation.Listen
import org.openbabyphone.navigation.LauncherDestination
import org.openbabyphone.navigation.Monitor
import org.openbabyphone.navigation.Start
import org.openbabyphone.navigation.Settings
import org.openbabyphone.navigation.launcherDestination
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.MonitorServiceRepository
import org.openbabyphone.ui.theme.Motion
import org.openbabyphone.ui.theme.QuietEngineTheme

val LocalWindowWidthSizeClass = staticCompositionLocalOf {
    WindowWidthSizeClass.Compact
}

class MainActivity : ComponentActivity() {
    private val internalRoutes = MutableSharedFlow<Listen>(extraBufferCapacity = 1)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        internalListenRoute(intent)?.let(internalRoutes::tryEmit)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val requestedRoute = internalListenRoute(intent)
        setContent {
            var themeMode by remember { mutableStateOf(ThemePreferences.read(this@MainActivity)) }
            QuietEngineTheme(darkTheme = themeMode.useDarkTheme(isSystemInDarkTheme())) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                    val navController = rememberNavController()
                    val startDestination = remember {
                        requestedRoute ?: when (
                            launcherDestination(
                                MonitorServiceRepository.sessionState.value,
                                ListenServiceRepository.sessionState.value
                            )
                        ) {
                            LauncherDestination.Start -> Start
                            LauncherDestination.Monitor -> Monitor
                            LauncherDestination.Listen -> Listen(resumeOnly = true)
                        }
                    }
                    LaunchedEffect(navController) {
                        internalRoutes.collect { navController.navigate(it) }
                    }

                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            enterTransition = {
                                fadeIn(animationSpec = tween(Motion.DurationMedium)) +
                                    slideInHorizontally(animationSpec = tween(Motion.DurationMedium)) { it / 4 }
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(Motion.DurationShort)) +
                                    slideOutHorizontally(animationSpec = tween(Motion.DurationShort)) { -it / 4 }
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = tween(Motion.DurationMedium)) +
                                    slideInHorizontally(animationSpec = tween(Motion.DurationMedium)) { -it / 4 }
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(Motion.DurationShort)) +
                                    slideOutHorizontally(animationSpec = tween(Motion.DurationShort)) { it / 4 }
                            }
                        ) {
                            composable<Start> {
                                StartScreen(
                                    onNavigateToMonitor = { navController.navigate(Monitor) },
                                    onNavigateToDiscover = { navController.navigate(Discover) },
                                    onNavigateToSettings = { navController.navigate(Settings) }
                                )
                            }
                            composable<Monitor> {
                                MonitorScreen(
                                    onNavigateBack = { navigateFromStoppedMonitor(navController) },
                                    onConnectionHelp = {
                                        navController.navigate(ConnectionHelp(ConnectionHelpMode.Child))
                                    },
                                    onNavigateToSettings = { navController.navigate(Settings) }
                                )
                            }
                            composable<Settings> {
                                SettingsScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    themeMode = themeMode,
                                    onThemeModeChanged = { selectedMode ->
                                        if (ThemePreferences.write(this@MainActivity, selectedMode)) {
                                            themeMode = selectedMode
                                        }
                                    }
                                )
                            }
                            composable<Discover> {
                                DiscoverScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToListen = { requestId, childId, pairingId ->
                                        navController.navigate(
                                            Listen(requestId, childId, pairingId)
                                        )
                                    },
                                    onConnectionHelp = { requestId ->
                                        navController.navigate(
                                            ConnectionHelp(
                                                mode = ConnectionHelpMode.Parent,
                                                requestId = requestId.orEmpty()
                                            )
                                        )
                                    }
                                )
                            }
                            composable<ConnectionHelp> { backStackEntry ->
                                val route = backStackEntry.toRoute<ConnectionHelp>()
                                ConnectionHelpScreen(
                                    mode = route.mode,
                                    requestId = route.requestId,
                                    onNavigateBack = {
                                        if (!navController.popBackStack()) {
                                            if (route.mode == ConnectionHelpMode.Parent) {
                                                navController.navigate(Discover)
                                            } else {
                                                navController.navigate(Start)
                                            }
                                        }
                                    },
                                    onTryLastKnownConnection = { requestId, childId, pairingId ->
                                        navController.navigate(Listen(requestId, childId, pairingId))
                                    },
                                    onManualAddress = {
                                        navController.navigate(DiscoverAddress(route.requestId))
                                    },
                                    onWifiDirect = {
                                        navController.navigate(DiscoverWifiDirect(route.requestId))
                                    },
                                    onPairAgain = {
                                        navController.navigate(Discover) {
                                            popUpTo<Discover> { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable<DiscoverAddress> { backStackEntry ->
                                val route = backStackEntry.toRoute<DiscoverAddress>()
                                DiscoverAddressScreen(
                                    requestId = route.requestId,
                                    onNavigateBack = { navController.popBackStack() },
                                    onPairAgain = {
                                        navController.navigate(Discover) {
                                            popUpTo<Discover> { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    onConnect = { requestId ->
                                        navController.navigate(Listen(requestId))
                                    }
                                )
                            }
                            composable<DiscoverWifiDirect> { backStackEntry ->
                                val route = backStackEntry.toRoute<DiscoverWifiDirect>()
                                DiscoverWifiDirectScreen(
                                    requestId = route.requestId,
                                    onNavigateBack = { navController.popBackStack() },
                                    onUseRegularWifi = { navController.popBackStack() },
                                    onPairAgain = {
                                        navController.navigate(Discover) {
                                            popUpTo<Discover> { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    onConnected = { requestId ->
                                        navController.navigate(Listen(requestId))
                                    }
                                )
                            }
                            composable<Listen> { backStackEntry ->
                                val route = backStackEntry.toRoute<Listen>()
                                ListenScreen(
                                    requestId = route.requestId,
                                    expectedChildId = route.expectedChildId,
                                    expectedPairingId = route.expectedPairingId,
                                    resumeOnly = route.resumeOnly,
                                    onNavigateBack = {
                                        if (!navController.popBackStack()) navController.navigate(Discover)
                                    },
                                    onPairAgain = {
                                        navController.navigate(Discover) {
                                            popUpTo<Listen> { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onConnectionHelp = {
                                        navController.navigate(
                                            ConnectionHelp(ConnectionHelpMode.Parent)
                                        ) {
                                            popUpTo<Listen> { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun internalListenRoute(intent: Intent): Listen? {
        return consumeInternalListenRoute(intent)
    }

    companion object {
        const val EXTRA_INTERNAL_ROUTE_ID = "org.openbabyphone.extra.INTERNAL_LISTEN_ROUTE_ID"
    }
}

internal fun navigateFromStoppedMonitor(navController: NavHostController) {
    if (!navController.popBackStack()) {
        navController.navigate(Start) {
            popUpTo<Monitor> { inclusive = true }
            launchSingleTop = true
        }
    }
}
