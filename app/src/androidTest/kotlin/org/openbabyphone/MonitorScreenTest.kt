package org.openbabyphone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.service.MonitorServiceRepository
import org.openbabyphone.service.MonitorSessionError
import org.openbabyphone.service.MonitorSessionState
import org.openbabyphone.service.ServiceConnectionManager

@RunWith(AndroidJUnit4::class)
class MonitorScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun resetRepository() {
        MonitorServiceRepository.reset()
        composeTestRule.activity.getSharedPreferences(
            MonitorService.PAIRING_PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun inactiveScreen_isConciseAndDoesNotExposeCredentials() {
        setMonitorContent()

        composeTestRule.onNodeWithText("Start monitoring").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.monitoring_setup_description)
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("device_name_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pairing_code_field").assertDoesNotExist()
        composeTestRule.onNodeWithText("Reset pairing").assertDoesNotExist()
    }

    @Test
    fun startMonitoring_requestsMicrophoneAndDoesNotStartWhenDenied() {
        val requestedPermissions = mutableListOf<String>()
        setMonitorContent(
            permissionChecker = { _, _ -> false },
            permissionRequester = { permission, result ->
                requestedPermissions += permission
                result(false)
            }
        )

        composeTestRule.onNodeWithTag("start_monitoring_button").performClick()

        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), requestedPermissions)
        composeTestRule.onNodeWithTag("monitoring_hero").assertDoesNotExist()
        composeTestRule.onNodeWithTag("microphone_permission_recovery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retry_microphone_permission").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_permission_settings").assertIsDisplayed()
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun notificationDenial_doesNotBlockMonitoringAndShowsOneIssue() {
        val requestedPermissions = mutableListOf<String>()
        setMonitorContent(
            permissionChecker = { _, permission -> permission == Manifest.permission.RECORD_AUDIO },
            permissionRequester = { permission, result ->
                requestedPermissions += permission
                result(false)
            }
        )

        composeTestRule.onNodeWithTag("start_monitoring_button").performClick()
        composeTestRule.onNodeWithText("Close").performClick()

        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), requestedPermissions)
        composeTestRule.onNodeWithTag("monitoring_hero").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notifications are off").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("monitor_issue_banner").assertCountEquals(1)
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun notificationResultPrecedesForegroundServiceStartAndDenialStillProceeds() {
        val events = mutableListOf<String>()
        var notificationResult: ((Boolean) -> Unit)? = null
        setMonitorContent(
            permissionChecker = { _, permission -> permission == Manifest.permission.RECORD_AUDIO },
            permissionRequester = { permission, result ->
                events += "request:$permission"
                notificationResult = result
            },
            startMonitorService = {
                events += "start"
                true
            }
        )

        composeTestRule.onNodeWithTag("start_monitoring_button").performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf("request:${Manifest.permission.POST_NOTIFICATIONS}"), events)
            notificationResult?.invoke(false)
        }
        composeTestRule.runOnIdle {
            assertEquals(
                listOf("request:${Manifest.permission.POST_NOTIFICATIONS}", "start"),
                events
            )
        }
    }

    @Test
    fun prioritizedWarningBecomesTheOnlyPoliteMonitorLiveRegion() {
        val warning = mutableStateOf(false)
        val state = org.openbabyphone.viewmodel.MonitorUiState(
            deviceName = "Nursery",
            status = "Monitoring",
            isMonitoring = true,
            sessionState = MonitorSessionState.Connected(1),
            batteryOptimizationIgnored = true
        )
        composeTestRule.setContent {
            MonitorContent(
                uiState = state,
                notificationWarning = warning.value,
                microphonePermissionDenied = false,
                onStartMonitoring = {},
                onStopMonitoring = {},
                onConnectionHelp = {},
                onOpenWifiSettings = {},
                onOpenAppSettings = {},
                onOpenBatteryOptimizationSettings = {}
            )
        }
        composeTestRule.onNodeWithTag("monitoring_hero", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))

        composeTestRule.runOnIdle { warning.value = true }

        composeTestRule.onNodeWithTag("monitor_issue_banner", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeTestRule.onNodeWithTag("monitoring_hero", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun foregroundServiceStartFailure_showsRetryableInactiveRecovery() {
        setMonitorContent(
            startMonitorService = {
                MonitorServiceRepository.updateError(
                    MonitorSessionError.Startup,
                    "Monitoring could not start"
                )
                false
            }
        )

        composeTestRule.onNodeWithTag("start_monitoring_button").performClick()

        composeTestRule.onNodeWithTag("monitoring_hero").assertDoesNotExist()
        composeTestRule.onNodeWithTag("monitor_terminal_recovery").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monitoring could not start").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_monitoring_button").assertIsDisplayed()
    }

    @Test
    fun waitingState_showsHeroAndPairingDialogWithoutDuplicateCount() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.WaitingForParent)
        setMonitorContent()

        composeTestRule.onNodeWithTag("pairing_dialog_qr_code").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pairing_dialog_pairing_code").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_pairing_code").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").performClick()

        composeTestRule.onNodeWithTag("monitoring_hero").assertIsDisplayed()
        composeTestRule.onNodeWithText("No parent is listening").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pair a parent").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("parent_count_text").assertCountEquals(1)
    }

    @Test
    fun startingAndNoNetwork_doNotAutoOpenPairingDialog() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Starting)
        setMonitorContent()

        composeTestRule.onNodeWithTag("monitoring_hero").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pairing_dialog_qr_code").assertDoesNotExist()

        MonitorServiceRepository.updateSessionState(MonitorSessionState.NoNetwork)
        composeTestRule.onNodeWithTag("pairing_dialog_qr_code").assertDoesNotExist()
    }

    @Test
    fun automaticallyOpenedPairingDialog_closesWhenParentConnects() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.WaitingForParent)
        setMonitorContent()
        composeTestRule.onNodeWithTag("pairing_dialog_qr_code").assertIsDisplayed()

        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(1))

        composeTestRule.onNodeWithTag("pairing_dialog_qr_code").assertDoesNotExist()
    }

    @Test
    fun manuallyOpenedPairingDialog_remainsOpenWhenParentCountChanges() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(1))
        setMonitorContent()
        composeTestRule.onNodeWithTag("pair_parent_button").performClick()
        composeTestRule.onNodeWithTag("pairing_dialog_qr_code").assertIsDisplayed()

        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(2))

        composeTestRule.onNodeWithTag("pairing_dialog_qr_code").assertIsDisplayed()
    }

    @Test
    fun connectedState_showsAuthoritativeCountAndPairAnotherParent() {
        MonitorServiceRepository.updateConnectedClients(1)
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(2))
        setMonitorContent()

        composeTestRule.onNodeWithText("2 parents are listening").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pair another parent").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("parent_count_text").assertCountEquals(1)
    }

    @Test
    fun activeCoreScreen_hidesTechnicalDetailsAndWifiDirect() {
        MonitorServiceRepository.updateServiceInfo("Test Service", 10042, listOf("test.local"))
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(1))
        setMonitorContent()

        composeTestRule.onNodeWithText("Test Service").assertDoesNotExist()
        composeTestRule.onNodeWithText("test.local").assertDoesNotExist()
        composeTestRule.onNodeWithText("10042").assertDoesNotExist()
        composeTestRule.onNodeWithTag("wifi_direct_content").assertDoesNotExist()
        composeTestRule.onNodeWithText("Wi-Fi Direct (experimental)").assertDoesNotExist()
        composeTestRule.onNodeWithTag("monitor_connection_help").assertIsDisplayed()
    }

    @Test
    fun connectionHelpAction_navigatesWithoutStoppingMonitoring() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(1))
        var opened = false
        setMonitorContent(onConnectionHelp = { opened = true })

        composeTestRule.onNodeWithTag("monitor_connection_help").performClick()
        assertTrue(opened)
        composeTestRule.onNodeWithTag("monitoring_hero").assertIsDisplayed()
    }

    @Test
    fun settingsAction_navigatesWithoutRequestingAStop() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(1))
        var opened = false
        var stopped = false
        setMonitorContent(
            onNavigateToSettings = { opened = true },
            unbindAndStopService = { _, _ -> stopped = true }
        )

        composeTestRule.onNodeWithTag("monitor_settings_action").performClick()

        assertTrue(opened)
        assertFalse(stopped)
        composeTestRule.onNodeWithTag("confirm_stop_monitoring").assertDoesNotExist()
    }

    @Test
    fun leavingMonitorForSettings_onlyDisposesBindingAndKeepsServiceRunning() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(1))
        val showMonitor = mutableStateOf(true)
        var bindingDisposed = false
        var serviceStopped = false
        composeTestRule.setContent {
            if (showMonitor.value) {
                MonitorScreen(
                    onNavigateBack = {},
                    onConnectionHelp = {},
                    onNavigateToSettings = { showMonitor.value = false },
                    bindMonitorService = { fakeBinding(it) },
                    disposeServiceBinding = { _, _ -> bindingDisposed = true },
                    unbindAndStopService = { _, _ -> serviceStopped = true },
                    stopMonitorService = { serviceStopped = true },
                    permissionChecker = { _, _ -> true },
                    notificationWarningChecker = { false },
                    openBatteryOptimizationSettings = {}
                )
            } else {
                Text("Settings destination")
            }
        }

        composeTestRule.onNodeWithTag("monitor_settings_action").performClick()

        composeTestRule.onNodeWithText("Settings destination").assertIsDisplayed()
        assertTrue(bindingDisposed)
        assertFalse(serviceStopped)
        assertEquals(MonitorSessionState.Connected(1), MonitorServiceRepository.sessionState.value)
    }

    @Test
    fun terminalErrorShowsInactiveRecoveryInsteadOfMonitoringHero() {
        MonitorServiceRepository.updateError(MonitorSessionError.AudioCapture, "Microphone failed")
        setMonitorContent()

        composeTestRule.onNodeWithText("Monitoring problem").assertIsDisplayed()
        composeTestRule.onNodeWithText("Microphone failed").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monitor_terminal_recovery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_monitoring_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monitoring_hero").assertDoesNotExist()
        composeTestRule.onAllNodesWithTag("monitor_issue_banner").assertCountEquals(0)
    }

    @Test
    fun advertisingErrorRemainsActiveAndRecoverable() {
        MonitorServiceRepository.updateError(MonitorSessionError.Advertising, "Advertising failed")
        setMonitorContent()

        composeTestRule.onNodeWithTag("monitoring_hero").assertIsDisplayed()
        composeTestRule.onNodeWithText("Advertising failed").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monitor_issue_banner").assertIsDisplayed()
    }

    @Test
    fun stopButton_requiresConfirmationAndExplicitlyStopsService() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(1))
        var stopped = false
        var navigatedBack = false
        setMonitorContent(
            onNavigateBack = { navigatedBack = true },
            unbindAndStopService = { _, _ -> stopped = true }
        )

        composeTestRule.onNodeWithTag("stop_monitoring_button").performClick()
        assertFalse(stopped)
        composeTestRule.onNodeWithTag("confirm_stop_monitoring").performClick()

        assertTrue(stopped)
        assertTrue(navigatedBack)
        composeTestRule.onNodeWithTag("start_monitoring_button").assertIsDisplayed()
    }

    @Test
    fun toolbarBack_requiresStopConfirmationBeforeNavigating() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(1))
        var navigatedBack = false
        setMonitorContent(onNavigateBack = { navigatedBack = true })

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertFalse(navigatedBack)
        composeTestRule.onNodeWithTag("confirm_stop_monitoring").performClick()

        assertTrue(navigatedBack)
    }

    @Test
    fun systemBack_requiresStopConfirmationBeforeNavigating() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.Connected(1))
        var navigatedBack = false
        setMonitorContent(onNavigateBack = { navigatedBack = true })

        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.onNodeWithTag("confirm_stop_monitoring").assertIsDisplayed()
        assertFalse(navigatedBack)

        composeTestRule.onNodeWithTag("confirm_stop_monitoring").performClick()
        assertTrue(navigatedBack)
    }

    private fun setMonitorContent(
        onNavigateBack: () -> Unit = {},
        onConnectionHelp: () -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
        permissionChecker: (Context, String) -> Boolean = { _, _ -> true },
        permissionRequester: ((String, (Boolean) -> Unit) -> Unit)? = null,
        unbindAndStopService: (Context, ServiceConnectionManager.ServiceBinding) -> Unit = { _, _ -> },
        startMonitorService: (Context) -> Boolean = { true },
        notificationWarningChecker: (Context) -> Boolean = { false }
    ) {
        composeTestRule.setContent {
            MonitorScreen(
                onNavigateBack = onNavigateBack,
                onConnectionHelp = onConnectionHelp,
                onNavigateToSettings = onNavigateToSettings,
                bindMonitorService = { fakeBinding(it) },
                startMonitorService = startMonitorService,
                disposeServiceBinding = { _, _ -> },
                unbindAndStopService = unbindAndStopService,
                stopMonitorService = {},
                permissionChecker = permissionChecker,
                permissionRequester = permissionRequester,
                notificationWarningChecker = notificationWarningChecker,
                openBatteryOptimizationSettings = {}
            )
        }
    }

    private fun fakeBinding(context: Context): ServiceConnectionManager.ServiceBinding {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(
                name: android.content.ComponentName?,
                service: android.os.IBinder?
            ) = Unit

            override fun onServiceDisconnected(name: android.content.ComponentName?) = Unit
        }
        return ServiceConnectionManager.ServiceBinding(
            Intent(context, MonitorService::class.java),
            connection,
            false
        )
    }
}
