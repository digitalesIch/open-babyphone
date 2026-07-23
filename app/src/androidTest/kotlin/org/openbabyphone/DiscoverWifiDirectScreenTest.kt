package org.openbabyphone

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.ui.theme.QuietEngineTheme
import org.openbabyphone.viewmodel.WifiDirectParentUiState

@RunWith(AndroidJUnit4::class)
class DiscoverWifiDirectScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun clearPendingConnections() {
        PendingConnections.store.clear()
    }

    @Test
    fun idle_hasOnlyExplanationAndContextualTry() {
        setWifiContent(WifiDirectState.Idle)

        composeTestRule.onNodeWithTag("wifi_direct_idle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wifi_direct_try").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("wifi_direct_pairing_code_field").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("wifi_direct_cancel").assertCountEquals(0)
    }

    @Test
    fun progressConnectingAndError_eachExposeCancelAndSinglePresentation() {
        listOf(
            WifiDirectState.Starting,
            WifiDirectState.Discovering(emptyList()),
            WifiDirectState.Connecting(peer()),
            WifiDirectState.Connected(WifiDirectEndpoint("child", 10000, "Nursery")),
            WifiDirectState.Advertising,
            WifiDirectState.Error("failed")
        ).forEach { state ->
            setWifiContent(state)
            composeTestRule.onAllNodesWithTag("wifi_direct_state_presentation").assertCountEquals(1)
            composeTestRule.onNodeWithTag("wifi_direct_cancel").assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("wifi_direct_retry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use regular Wi-Fi instead").assertIsDisplayed()
    }

    @Test
    fun peerList_showsFriendlyNamesAndNoRawFields() {
        setWifiContent(WifiDirectState.Discovering(listOf(peer())))

        composeTestRule.onNodeWithText("Nursery").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("raw-device").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("aa:bb:cc:dd:ee:ff").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("12345").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("wifi_direct_pairing_code_field").assertCountEquals(1)
    }

    @Test
    fun pendingCredential_hidesPairingFieldAndConnectsByFriendlyRow() {
        var connectedPeer: WifiDirectPeer? = null
        setWifiContent(
            state = WifiDirectState.Discovering(listOf(peer())),
            hasPendingCredential = true,
            onConnect = { connectedPeer = it }
        )

        composeTestRule.onAllNodesWithTag("wifi_direct_pairing_code_field").assertCountEquals(0)
        composeTestRule.onNodeWithText("Connect").performClick()
        assertEquals(peer(), connectedPeer)
    }

    @Test
    fun permissionDenialAlwaysOffersRegularWifiAndPermanentDenialAddsSettings() {
        setWifiContent(
            state = WifiDirectState.Idle,
            permissionDenied = true,
            permissionPermanentlyDenied = true
        )

        composeTestRule.onNodeWithTag("wifi_direct_regular_wifi").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wifi_direct_app_settings").assertIsDisplayed()
    }

    @Test
    fun expiredCredentialStopsBeforeDiscoveryAndOffersRecovery() {
        setWifiContent(
            state = WifiDirectState.Idle,
            credentialState = PendingCredentialState.Expired
        )

        composeTestRule.onNodeWithTag("expired_pairing_recovery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pair_again_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wifi_direct_regular_wifi").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wifi_direct_try").assertDoesNotExist()
    }

    @Test
    fun openScreenObservesPendingCredentialExpiryAndHasOneHeading() {
        val requestId = PendingConnections.store.put(
            PendingConnection(name = "Nursery", pairingCode = "babyroom42".toCharArray())
        )
        composeTestRule.setContent {
            QuietEngineTheme {
                DiscoverWifiDirectScreen(
                    requestId = requestId,
                    onNavigateBack = {},
                    onConnected = {},
                    onUseRegularWifi = {}
                )
            }
        }
        composeTestRule.onAllNodesWithText("Wi-Fi Direct (experimental)").assertCountEquals(1)
        composeTestRule.onNodeWithTag("wifi_direct_try").assertIsDisplayed()

        composeTestRule.runOnIdle { PendingConnections.store.remove(requestId) }

        composeTestRule.onNodeWithTag("expired_pairing_recovery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wifi_direct_try").assertDoesNotExist()
    }

    @Test
    fun cancelRemainsReachableAtTwoHundredPercent() {
        val density = composeTestRule.activity.resources.displayMetrics.density
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                QuietEngineTheme {
                    WifiDirectParentContent(
                        uiState = WifiDirectParentUiState(wifiDirectState = WifiDirectState.Starting),
                        credentialState = PendingCredentialState.None,
                        permissionDenied = false,
                        permissionPermanentlyDenied = false,
                        onTry = {},
                        onRetry = {},
                        onCancel = {},
                        onUseRegularWifi = {},
                        onPairAgain = {},
                        onOpenAppSettings = {},
                        onPairingCodeChange = {},
                        onConnect = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("wifi_direct_cancel").performScrollTo().assertIsDisplayed()
    }

    private fun setWifiContent(
        state: WifiDirectState,
        hasPendingCredential: Boolean = false,
        credentialState: PendingCredentialState = if (hasPendingCredential) {
            PendingCredentialState.Available
        } else {
            PendingCredentialState.None
        },
        permissionDenied: Boolean = false,
        permissionPermanentlyDenied: Boolean = false,
        onConnect: (WifiDirectPeer) -> Unit = {}
    ) {
        composeTestRule.setContent {
            QuietEngineTheme {
                WifiDirectParentContent(
                    uiState = WifiDirectParentUiState(wifiDirectState = state),
                    credentialState = credentialState,
                    permissionDenied = permissionDenied,
                    permissionPermanentlyDenied = permissionPermanentlyDenied,
                    onTry = {},
                    onRetry = {},
                    onCancel = {},
                    onUseRegularWifi = {},
                    onPairAgain = {},
                    onOpenAppSettings = {},
                    onPairingCodeChange = {},
                    onConnect = onConnect
                )
            }
        }
    }

    private fun peer() = WifiDirectPeer(
        deviceAddress = "aa:bb:cc:dd:ee:ff",
        deviceName = "raw-device",
        port = 12345,
        displayName = "Nursery"
    )
}
