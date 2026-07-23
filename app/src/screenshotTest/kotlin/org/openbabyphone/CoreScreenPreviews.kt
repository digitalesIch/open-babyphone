package org.openbabyphone

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import org.openbabyphone.navigation.ConnectionHelpMode
import org.openbabyphone.service.ListenSessionError
import org.openbabyphone.service.ListenSessionState
import org.openbabyphone.service.MonitorSessionState
import org.openbabyphone.ui.theme.QuietEngineTheme
import org.openbabyphone.viewmodel.ConnectionHelpUiState
import org.openbabyphone.viewmodel.DiscoverUiState
import org.openbabyphone.viewmodel.KnownChildRow
import org.openbabyphone.viewmodel.KnownChildStatus
import org.openbabyphone.viewmodel.ListenRequest
import org.openbabyphone.viewmodel.ListenUiState
import org.openbabyphone.viewmodel.MonitorUiState
import org.openbabyphone.viewmodel.PairingFlowState
import org.openbabyphone.viewmodel.listenPresentation

@Preview(name = "compact portrait", widthDp = 360, heightDp = 720)
@Preview(name = "short landscape", widthDp = 720, heightDp = 360)
@Preview(name = "wide", widthDp = 840, heightDp = 900)
private annotation class CoreLayouts

@PreviewTest
@CoreLayouts
@Composable
fun roleChooserLayouts() {
    PreviewSurface {
        StartScreen({}, {}, onNavigateToSettings = {})
    }
}

@PreviewTest
@Preview(name = "dark", widthDp = 360, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun roleChooserDark() {
    PreviewSurface(darkTheme = true) {
        StartScreen({}, {}, onNavigateToSettings = {})
    }
}

@PreviewTest
@Preview(name = "font 1.5", widthDp = 360, heightDp = 720, fontScale = 1.5f)
@Composable
fun roleChooserLargeFont() {
    PreviewSurface {
        StartScreen({}, {}, onNavigateToSettings = {})
    }
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun childSetup() {
    MonitorPreview(MonitorUiState(isLoading = false, isMonitoring = false))
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun childActive() {
    MonitorPreview(
        MonitorUiState(
            deviceName = "Nursery",
            status = "Streaming securely",
            connectedClients = 1,
            isLoading = false,
            isMonitoring = true,
            sessionState = MonitorSessionState.Connected(1),
            batteryOptimizationIgnored = true
        )
    )
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun parentHomeEmpty() {
    ParentPreview(DiscoverUiState())
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun parentHomeKnown() {
    val child = TrustedChild("child-id", "pair-id", "Nursery")
    ParentPreview(
        DiscoverUiState(
            trustedChildren = listOf(child),
            knownChildren = listOf(KnownChildRow(child, KnownChildStatus.Available, null))
        )
    )
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun parentQrLooking() {
    ParentPreview(
        DiscoverUiState(
            pairingFlow = PairingFlowState.LookingForChild("Nursery", "child", "pair", "request")
        )
    )
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun parentQrReady() {
    ParentPreview(
        DiscoverUiState(
            pairingFlow = PairingFlowState.Ready(
                "Nursery",
                ListenRequest("request", "child", "pair")
            )
        )
    )
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun parentChildNotFound() {
    ParentPreview(
        DiscoverUiState(
            pairingFlow = PairingFlowState.ChildNotFound("Nursery", "child", "pair", "request")
        )
    )
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun listenConnecting() = ListenPreview(ListenSessionState.Connecting)

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun listenQuiet() = ListenPreview(ListenSessionState.Listening, floatArrayOf(0.01f))

@PreviewTest
@Preview(widthDp = 360, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun listenLoudDark() = ListenPreview(ListenSessionState.Listening, floatArrayOf(0.9f), true)

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun listenDisrupted() = ListenPreview(ListenSessionState.Disrupted)

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun listenReconnecting() = ListenPreview(ListenSessionState.Reconnecting(2, 5))

@PreviewTest
@Preview(widthDp = 360, heightDp = 720)
@Composable
fun listenAuthenticationFailure() = ListenPreview(
    ListenSessionState.Error(ListenSessionError.Authentication, "Authentication failed")
)

@PreviewTest
@Preview(widthDp = 360, heightDp = 720, fontScale = 1.5f)
@Composable
fun listenLostLargeFont() = ListenPreview(ListenSessionState.Lost)

@PreviewTest
@Preview(widthDp = 720, heightDp = 360)
@Composable
fun connectionHelpShortLandscape() {
    PreviewSurface {
        ConnectionHelpContent(
            mode = ConnectionHelpMode.Parent,
            uiState = ConnectionHelpUiState(),
            credentialState = PendingCredentialState.None,
            childPermissionDenied = false,
            childPermissionPermanentlyDenied = false,
            onTryLastKnownConnection = {},
            onForget = {},
            onManualAddress = {},
            onWifiDirect = {},
            onPairAgain = {},
            onUseRegularWifi = {},
            onOpenAppSettings = {},
            onStartChildWifiDirect = {},
            onStopChildWifiDirect = {}
        )
    }
}

@PreviewTest
@Preview(widthDp = 840, heightDp = 900)
@Composable
fun settingsWide() {
    PreviewSurface {
        SettingsScreen(
            onNavigateBack = {},
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChanged = {},
            initialKnownChildren = emptyList(),
            initialChildName = "Nursery",
            initialSensitivity = MicrophoneSensitivity.NORMAL
        )
    }
}

@PreviewTest
@Preview(name = "settings font 2.0", widthDp = 360, heightDp = 720, fontScale = 2f)
@Composable
fun settingsTwoHundredPercent() {
    PreviewSurface {
        SettingsScreen(
            onNavigateBack = {},
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChanged = {},
            initialKnownChildren = emptyList(),
            initialChildName = "Nursery",
            initialSensitivity = MicrophoneSensitivity.NORMAL
        )
    }
}

@PreviewTest
@Preview(name = "QR ready font 2.0", widthDp = 360, heightDp = 720, fontScale = 2f)
@Composable
fun parentQrReadyTwoHundredPercent() {
    ParentPreview(
        DiscoverUiState(
            pairingFlow = PairingFlowState.Ready(
                "Nursery",
                ListenRequest("request", "child", "pair")
            )
        )
    )
}

@Composable
private fun MonitorPreview(state: MonitorUiState) {
    PreviewSurface {
        MonitorContent(
            uiState = state,
            notificationWarning = false,
            microphonePermissionDenied = false,
            onStartMonitoring = {},
            onStopMonitoring = {},
            onConnectionHelp = {},
            onOpenWifiSettings = {},
            onOpenAppSettings = {},
            onOpenBatteryOptimizationSettings = {}
        )
    }
}

@Composable
private fun ParentPreview(state: DiscoverUiState) {
    PreviewSurface {
        ParentHomeContent(
            uiState = state,
            onScanQr = {},
            onCannotScan = {},
            onKnownChildAction = { _, _ -> },
            onRetry = {},
            onStartListening = {},
            onConnectionHelp = {}
        )
    }
}

@Composable
private fun ListenPreview(
    sessionState: ListenSessionState,
    volume: FloatArray = floatArrayOf(),
    darkTheme: Boolean = false
) {
    val resources = LocalContext.current.resources
    val now = 5_000L
    PreviewSurface(darkTheme) {
        ListenContent(
            uiState = ListenUiState(
                childDeviceName = "Nursery",
                sessionState = sessionState,
                presentation = listenPresentation(resources, sessionState),
                volumeHistory = volume,
                lastAudioUpdateAtMillis = if (volume.isEmpty()) 0L else 4_000L
            ),
            childName = "Nursery",
            nowMillis = now,
            readinessNotice = null,
            onPrimaryAction = {},
            onOpenNotificationSettings = {},
            onDisconnect = {}
        )
    }
}

@Composable
private fun PreviewSurface(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    QuietEngineTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content
        )
    }
}
