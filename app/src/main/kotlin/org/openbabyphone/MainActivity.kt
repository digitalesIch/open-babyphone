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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import org.openbabyphone.navigation.Discover
import org.openbabyphone.navigation.DiscoverAddress
import org.openbabyphone.navigation.DiscoverWifiDirect
import org.openbabyphone.navigation.Listen
import org.openbabyphone.navigation.Monitor
import org.openbabyphone.navigation.Start
import org.openbabyphone.ui.theme.Motion
import org.openbabyphone.ui.theme.QuietEngineTheme

val LocalWindowWidthSizeClass = staticCompositionLocalOf {
    WindowWidthSizeClass.Compact
}

class MainActivity : ComponentActivity() {
    private val newIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        newIntents.tryEmit(intent)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuietEngineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                    val navController = rememberNavController()
                    LaunchedEffect(navController) {
                        newIntents.collect { navController.handleDeepLink(it) }
                    }

                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = Start,
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
                                val context = LocalContext.current
                                StartScreen(
                                    onNavigateToMonitor = { navController.navigate(Monitor) },
                                    onNavigateToDiscover = { navController.navigate(Discover) },
                                    onNavigateToSettings = {
                                        context.startActivity(
                                            Intent(context, SettingsActivity::class.java)
                                        )
                                    }
                                )
                            }
                            composable<Monitor> {
                                MonitorScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable<Discover> {
                                DiscoverScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToAddressInput = { navController.navigate(DiscoverAddress) },
                                    onNavigateToListen = { address, port, name, pairingCode ->
                                        navController.navigate(
                                            Listen(address, port, name, pairingCode)
                                        )
                                    },
                                    onNavigateToWifiDirect = { navController.navigate(DiscoverWifiDirect) }
                                )
                            }
                            composable<DiscoverAddress> {
                                DiscoverAddressScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onConnect = { address, port, pairingCode ->
                                        navController.navigate(
                                            Listen(address, port, address, pairingCode)
                                        )
                                    }
                                )
                            }
                            composable<DiscoverWifiDirect> {
                                DiscoverWifiDirectScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onConnected = { host, port, name, pairingCode ->
                                        navController.navigate(
                                            Listen(host, port, name, pairingCode)
                                        )
                                    }
                                )
                            }
                            composable<Listen>(
                                deepLinks = listOf(
                                    navDeepLink {
                                        uriPattern = "quiet-engine://listen?address={address}&port={port}&name={name}&pairingCode={pairingCode}&resumeOnly={resumeOnly}"
                                    },
                                    navDeepLink {
                                        uriPattern = "quiet-engine://listen?address={address}&port={port}&name={name}&resumeOnly={resumeOnly}"
                                    }
                                )
                            ) { backStackEntry ->
                                val route = backStackEntry.toRoute<Listen>()
                                ListenScreen(
                                    address = route.address,
                                    port = route.port,
                                    name = route.name,
                                    pairingCode = route.pairingCode,
                                    resumeOnly = route.resumeOnly,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
