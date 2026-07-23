package org.openbabyphone

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.service.ListenSessionState
import org.openbabyphone.service.MonitorSessionState
import org.openbabyphone.ui.theme.QuietEngineTheme
import org.openbabyphone.viewmodel.DiscoverUiState
import org.openbabyphone.viewmodel.DiscoveredDevice
import org.openbabyphone.viewmodel.KnownChildRow
import org.openbabyphone.viewmodel.KnownChildStatus
import org.openbabyphone.viewmodel.ListenRequest
import org.openbabyphone.viewmodel.ListenUiState
import org.openbabyphone.viewmodel.MonitorUiState
import org.openbabyphone.viewmodel.PairingFlowState
import org.openbabyphone.viewmodel.DiscoverViewModel
import org.openbabyphone.viewmodel.listenPresentation

@RunWith(AndroidJUnit4::class)
class CoreJourneyAndConfigurationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun clearPendingConnections() {
        PendingConnections.store.clear()
        TrustedChildStore(composeTestRule.activity, TestCredentialCrypto()).let { store ->
            store.getAll().forEach { store.forget(it.childId) }
        }
    }

    @Test
    fun firstChild_roleToMonitoringTakesTwoInteractionsWithoutPermissionUi() {
        var destination by mutableIntStateOf(0)
        var interactions = 0
        var monitoring by mutableStateOf(false)
        composeTestRule.setContent {
            QuietEngineTheme {
                if (destination == 0) {
                    StartScreen(
                        onNavigateToMonitor = {
                            interactions++
                            destination = 1
                        },
                        onNavigateToDiscover = {}
                    )
                } else {
                    monitorContent(
                        state = monitorState(monitoring),
                        onStart = {
                            interactions++
                            monitoring = true
                        }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("child_role_card").performClick()
        composeTestRule.onNodeWithTag("start_monitoring_button").performClick()

        composeTestRule.onNodeWithTag("monitoring_hero").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(2, interactions) }
        assertCoreScreenHasNoTechnicalEditors()
    }

    @Test
    fun firstParent_roleQrReadyListenTakesThreeInteractionsWithoutCamera() {
        var destination by mutableIntStateOf(0)
        var flow by mutableStateOf<PairingFlowState>(PairingFlowState.Idle)
        var interactions = 0
        composeTestRule.setContent {
            QuietEngineTheme {
                when (destination) {
                    0 -> StartScreen(
                        onNavigateToMonitor = {},
                        onNavigateToDiscover = {
                            interactions++
                            destination = 1
                        }
                    )
                    1 -> ParentHomeContent(
                        uiState = DiscoverUiState(pairingFlow = flow),
                        onScanQr = {
                            interactions++
                            flow = PairingFlowState.LookingForChild(
                                "Nursery", "hidden-child", "hidden-pair", "hidden-request"
                            )
                        },
                        onCannotScan = {},
                        onKnownChildAction = { _, _ -> },
                        onRetry = {},
                        onStartListening = {
                            interactions++
                            destination = 2
                        },
                        onConnectionHelp = {}
                    )
                    else -> listenContent(ListenSessionState.Connecting)
                }
            }
        }

        composeTestRule.onNodeWithTag("parent_role_card").performClick()
        composeTestRule.onNodeWithTag("scan_qr_button").performClick()
        composeTestRule.onNodeWithText("Looking for Nursery").assertIsDisplayed()
        composeTestRule.runOnIdle {
            flow = PairingFlowState.Ready("Nursery", ListenRequest("request", "child", "pair"))
        }
        composeTestRule.onNodeWithTag("parent_state_hero", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertEquals(
            1,
            composeTestRule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
                useUnmergedTree = true
            ).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag("start_listening_button").performClick()

        composeTestRule.onNodeWithTag("listen_state_hero").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(3, interactions) }
        assertCoreScreenHasNoTechnicalEditors()
    }

    @Test
    fun returningParent_roleToQuickListenTakesTwoInteractions() {
        val store = TrustedChildStore(composeTestRule.activity, TestCredentialCrypto())
        store.getAll().forEach { store.forget(it.childId) }
        val credential = "babyroom42".toCharArray()
        store.trustAuthenticated(
            childId = "abcdefghijklmnop",
            pairingId = "ponmlkjihgfedcba",
            displayName = "Nursery",
            pairingCode = credential,
            address = "child.local",
            port = 10_000
        )
        credential.fill('\u0000')
        val viewModel = DiscoverViewModel(
            composeTestRule.activity.application,
            trustedChildStore = store
        )
        viewModel.recordResolvedDevice(
            DiscoveredDevice(
                name = "raw-service",
                address = "child.local",
                port = 10_000,
                childId = "abcdefghijklmnop",
                pairingId = "ponmlkjihgfedcba",
                displayName = "Nursery"
            )
        )
        assertEquals(KnownChildStatus.Available, viewModel.uiState.value.knownChildren.single().status)
        var destination by mutableIntStateOf(0)
        var interactions = 0
        composeTestRule.setContent {
            QuietEngineTheme {
                when (destination) {
                    0 -> StartScreen({}, {
                        interactions++
                        destination = 1
                    })
                    1 -> DiscoverScreen(
                        onNavigateBack = {},
                        onNavigateToListen = { requestId, childId, pairingId ->
                            check(PendingConnections.store.contains(requestId))
                            check(childId == "abcdefghijklmnop")
                            check(pairingId == "ponmlkjihgfedcba")
                            interactions++
                            destination = 2
                        },
                        onConnectionHelp = {},
                        viewModel = viewModel,
                        autoStartDiscovery = false
                    )
                    else -> listenContent(ListenSessionState.Listening, floatArrayOf(0.01f))
                }
            }
        }

        composeTestRule.onNodeWithTag("parent_role_card").performClick()
        composeTestRule.onNodeWithTag("known_child_action_0").performClick()

        composeTestRule.onNodeWithText("Quiet").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(2, interactions) }
        assertCoreScreenHasNoTechnicalEditors()
    }

    @Test
    fun activeAndRecoveryStatesExposeOneClearAction() {
        var state by mutableStateOf(ListenSessionState.Listening as ListenSessionState)
        var volume by mutableStateOf(floatArrayOf(0.01f))
        var actionCount = 0
        composeTestRule.setContent {
            QuietEngineTheme {
                listenContent(state, volume) { actionCount++ }
            }
        }

        composeTestRule.onNodeWithText("Quiet").assertIsDisplayed()
        composeTestRule.runOnIdle { volume = floatArrayOf(0.2f) }
        composeTestRule.onNodeWithText("Sound detected").assertIsDisplayed()
        composeTestRule.runOnIdle { volume = floatArrayOf(0.9f) }
        composeTestRule.onNodeWithText("Loud sound").assertIsDisplayed()
        composeTestRule.runOnIdle { state = ListenSessionState.Disrupted }
        composeTestRule.onNodeWithText("Audio interrupted").assertIsDisplayed()
        composeTestRule.runOnIdle { state = ListenSessionState.Reconnecting(2, 5) }
        composeTestRule.onNodeWithText("Reconnecting").assertIsDisplayed()
        composeTestRule.runOnIdle { state = ListenSessionState.Lost }
        composeTestRule.onNodeWithTag("listen_primary_action")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
            .performClick()

        composeTestRule.runOnIdle { assertEquals(1, actionCount) }
    }

    @Test
    fun compactPortraitAtTwoHundredPercentKeepsCriticalActionsReachable() {
        assertCriticalActionsReachable(DpSize(360.dp, 640.dp))
    }

    @Test
    fun shortLandscapeAtTwoHundredPercentKeepsCriticalActionsReachable() {
        assertCriticalActionsReachable(DpSize(640.dp, 260.dp))
    }

    @Test
    fun wideAtTwoHundredPercentKeepsCriticalActionsReachable() {
        assertCriticalActionsReachable(DpSize(840.dp, 600.dp))
    }

    @Test
    fun semanticsDescribeRoleChildSessionCountFreshnessWarningAndRecoveryInOrder() {
        var screen by mutableIntStateOf(0)
        composeTestRule.setContent {
            QuietEngineTheme {
                when (screen) {
                    0 -> StartScreen({}, {})
                    1 -> monitorContent(
                        monitorState(active = true).copy(
                            deviceName = "Nursery",
                            status = "Streaming securely",
                            connectedClients = 2,
                            sessionState = MonitorSessionState.Connected(2)
                        )
                    )
                    2 -> monitorContent(
                        monitorState(active = true).copy(
                            deviceName = "Nursery",
                            status = "No network",
                            sessionState = MonitorSessionState.NoNetwork
                        )
                    )
                    else -> listenContent(ListenSessionState.Listening, floatArrayOf(0.01f))
                }
            }
        }

        composeTestRule.onNodeWithTag("child_role_card")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 0f))
        composeTestRule.onNodeWithTag("parent_role_card")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))

        composeTestRule.runOnIdle { screen = 1 }
        composeTestRule.onNodeWithTag("monitoring_hero")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Nursery")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Streaming securely"))
        composeTestRule.onNodeWithTag("parent_count_text")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 parents are listening"))

        composeTestRule.runOnIdle { screen = 2 }
        composeTestRule.onNodeWithTag("monitor_issue_banner")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No Wi-Fi connection"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 2f))
        composeTestRule.onNodeWithText("Open Wi-Fi settings").assertIsDisplayed()
        assertEquals(
            1,
            composeTestRule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
                useUnmergedTree = true
            ).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag("monitor_issue_banner", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        composeTestRule.runOnIdle { screen = 3 }
        composeTestRule.onNodeWithTag("audio_freshness")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Quiet"))
        val liveNodes = composeTestRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
            useUnmergedTree = true
        ).fetchSemanticsNodes()
        assertEquals(0, liveNodes.size)
        composeTestRule.onNodeWithTag("listen_state_hero", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        composeTestRule.onNodeWithTag("audio_freshness", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        composeTestRule.onNodeWithTag("disconnect_button")
            .assert(hasAnyAncestor(hasTestTag("listen_state_hero")))
    }

    private fun assertCriticalActionsReachable(size: DpSize) {
        var screen by mutableIntStateOf(0)
        composeTestRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f) then
                    DeviceConfigurationOverride.ForcedSize(size)
            ) {
                QuietEngineTheme {
                    when (screen) {
                        0 -> monitorContent(monitorState(false))
                        1 -> monitorContent(monitorState(true))
                        2 -> listenContent(ListenSessionState.Lost)
                        else -> monitorContent(monitorState(true).copy(connectedClients = 0))
                    }
                }
            }
        }

        assertReachable("start_monitoring_button")
        composeTestRule.runOnIdle { screen = 1 }
        assertReachable("stop_monitoring_button")
        composeTestRule.runOnIdle { screen = 2 }
        assertReachable("listen_primary_action")
        composeTestRule.runOnIdle { screen = 3 }
        assertReachable("pair_parent_button")
    }

    private fun assertReachable(tag: String) {
        composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }

    @Composable
    private fun monitorContent(
        state: MonitorUiState,
        onStart: () -> Unit = {}
    ) {
        MonitorContent(
            uiState = state,
            notificationWarning = false,
            microphonePermissionDenied = false,
            onStartMonitoring = onStart,
            onStopMonitoring = {},
            onConnectionHelp = {},
            onOpenWifiSettings = {},
            onOpenAppSettings = {},
            onOpenBatteryOptimizationSettings = {}
        )
    }

    @Composable
    private fun listenContent(
        state: ListenSessionState,
        volume: FloatArray = floatArrayOf(),
        onAction: () -> Unit = {}
    ) {
        ListenContent(
            uiState = ListenUiState(
                childDeviceName = "Nursery",
                sessionState = state,
                presentation = listenPresentation(composeTestRule.activity.application, state),
                volumeHistory = volume,
                lastAudioUpdateAtMillis = if (volume.isEmpty()) 0L else 4_000L
            ),
            childName = "Nursery",
            nowMillis = 5_000L,
            readinessNotice = null,
            onPrimaryAction = { onAction() },
            onOpenNotificationSettings = {},
            onDisconnect = {}
        )
    }

    private fun monitorState(active: Boolean) = MonitorUiState(
        deviceName = "Nursery",
        status = if (active) "Streaming securely" else "Ready",
        connectedClients = if (active) 1 else 0,
        isLoading = false,
        isMonitoring = active,
        sessionState = if (active) MonitorSessionState.Connected(1) else MonitorSessionState.Setup,
        batteryOptimizationIgnored = true
    )

    private fun assertCoreScreenHasNoTechnicalEditors() {
        listOf(
            "IP address",
            "Port",
            "Service name",
            "Child ID",
            "Pairing code:"
        ).forEach { text ->
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().also {
                assertEquals("Unexpected technical field: $text", 0, it.size)
            }
        }
        listOf(
            "ip_address_field",
            "port_field",
            "service_name_field",
            "child_id_field",
            "pairing_code_field",
            "fallback_code_field"
        ).forEach { tag -> composeTestRule.onNodeWithTag(tag).assertDoesNotExist() }
    }

    private class TestCredentialCrypto : TrustedCredentialCrypto {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ProtectedCredential =
            ProtectedCredential(ByteArray(12), plaintext.copyOf())

        override fun decrypt(
            protectedCredential: ProtectedCredential,
            aad: ByteArray
        ): ByteArray = protectedCredential.ciphertext.copyOf()
    }
}
