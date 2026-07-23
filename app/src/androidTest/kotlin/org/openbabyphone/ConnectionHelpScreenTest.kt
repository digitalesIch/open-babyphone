package org.openbabyphone

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.navigation.ConnectionHelpMode
import org.openbabyphone.ui.theme.QuietEngineTheme
import org.openbabyphone.viewmodel.ConnectionHelpUiState

@RunWith(AndroidJUnit4::class)
class ConnectionHelpScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun clearPendingConnections() {
        PendingConnections.store.clear()
    }

    @Test
    fun parentHelp_ordersRegularWifiHotspotExperimentalThenExpert() {
        setHelpContent(ConnectionHelpMode.Parent)

        val sameWifiTop = composeTestRule.onNodeWithTag("help_same_wifi").fetchSemanticsNode().boundsInRoot.top
        val hotspotTop = composeTestRule.onNodeWithTag("help_hotspot").fetchSemanticsNode().boundsInRoot.top
        val wifiDirectTop = composeTestRule.onNodeWithTag("help_wifi_direct").fetchSemanticsNode().boundsInRoot.top
        val manualTop = composeTestRule.onNodeWithTag("help_manual_expert").fetchSemanticsNode().boundsInRoot.top

        assertTrue(sameWifiTop < hotspotTop)
        assertTrue(hotspotTop < wifiDirectTop)
        assertTrue(wifiDirectTop < manualTop)
        composeTestRule.onNodeWithText("Recommended").assertIsDisplayed()
        composeTestRule.onNodeWithText("Experimental").assertIsDisplayed()
        composeTestRule.onNodeWithText("Expert").assertIsDisplayed()
    }

    @Test
    fun knownChildManagement_isFriendlyAndHidesEndpoint() {
        setHelpContent(
            mode = ConnectionHelpMode.Parent,
            state = ConnectionHelpUiState(
                knownChildren = listOf(
                    TrustedChild("raw-child", "raw-pair", "Nursery", "raw-host", 12345)
                )
            )
        )

        composeTestRule.onNodeWithText("Nursery").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try last known connection").assertIsDisplayed()
        composeTestRule.onNodeWithText("Forget").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("raw-host").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("12345").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("raw-child").assertCountEquals(0)
    }

    @Test
    fun childExperimentalPermissionPath_startsOnlyAfterTryAction() {
        var starts = 0
        setHelpContent(
            mode = ConnectionHelpMode.Child,
            state = ConnectionHelpUiState(wifiDirectSupported = true),
            onStartChildWifiDirect = { starts++ }
        )

        assertEquals(0, starts)
        composeTestRule.onNodeWithTag("child_wifi_direct_try").performClick()
        assertEquals(1, starts)
        composeTestRule.onAllNodesWithText("Expert").assertCountEquals(0)
    }

    @Test
    fun pendingCredentialExpiryImmediatelyOffersScanAgainAndSkipsAdvancedWork() {
        val requestId = PendingConnections.store.put(
            PendingConnection(name = "Nursery", pairingCode = "babyroom42".toCharArray())
        )
        composeTestRule.setContent {
            QuietEngineTheme {
                ConnectionHelpScreen(
                    mode = ConnectionHelpMode.Parent,
                    requestId = requestId,
                    onNavigateBack = {},
                    onTryLastKnownConnection = { _, _, _ -> },
                    onManualAddress = {},
                    onWifiDirect = {}
                )
            }
        }
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.scanned_pairing_reused)
        ).performScrollTo().assertIsDisplayed()

        composeTestRule.runOnIdle { PendingConnections.store.remove(requestId) }

        composeTestRule.onNodeWithTag("expired_pairing_recovery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pair_again_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("help_wifi_direct").assertDoesNotExist()
        composeTestRule.onNodeWithTag("help_manual_expert").assertDoesNotExist()
    }

    @Test
    fun childPermanentPermissionDenialOffersRegularWifiAndAppSettings() {
        composeTestRule.setContent {
            QuietEngineTheme {
                ConnectionHelpScreen(
                    mode = ConnectionHelpMode.Child,
                    requestId = "",
                    onNavigateBack = {},
                    onTryLastKnownConnection = { _, _, _ -> },
                    onManualAddress = {},
                    onWifiDirect = {},
                    permissionChecker = { _, _ -> false },
                    permissionRequester = { _, result -> result(false) },
                    permissionPermanentlyDenied = { _, _ -> true },
                    openAppSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("child_wifi_direct_try").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("child_wifi_direct_regular_wifi").assertIsDisplayed()
        composeTestRule.onNodeWithTag("child_wifi_direct_app_settings").assertIsDisplayed()
    }

    private fun setHelpContent(
        mode: ConnectionHelpMode,
        state: ConnectionHelpUiState = ConnectionHelpUiState(),
        onStartChildWifiDirect: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            QuietEngineTheme {
                ConnectionHelpContent(
                    mode = mode,
                    uiState = state,
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
                    onStartChildWifiDirect = onStartChildWifiDirect,
                    onStopChildWifiDirect = {}
                )
            }
        }
    }
}
