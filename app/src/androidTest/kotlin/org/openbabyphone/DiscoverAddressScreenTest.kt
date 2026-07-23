package org.openbabyphone

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoverAddressScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun clearPendingConnections() {
        PendingConnections.store.clear()
    }

    @Test
    fun expertScreen_explainsEndpointAndKeepsPortCollapsed() {
        setAddressScreen()

        composeTestRule.onNodeWithText("Expert: Manual address").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Get the child phone",
            substring = true
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("port_override_disclosure").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed")
        )
        composeTestRule.onAllNodesWithTag("port_field").assertCountEquals(0)
    }

    @Test
    fun ordinaryManualConnection_requiresAddressAndValidCode() {
        setAddressScreen()

        composeTestRule.onNodeWithTag("ip_address_field").performTextInput("192.168.1.2")
        composeTestRule.onNodeWithTag("connect_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("manual_pairing_code_field").performTextInput("babyroom42")
        composeTestRule.onNodeWithTag("connect_button").assertIsEnabled()
    }

    @Test
    fun pendingQrRequest_reusesCredentialAndAsksOnlyForAddress() {
        val requestId = PendingConnections.store.put(
            PendingConnection(
                name = "Nursery",
                pairingCode = "babyroom42".toCharArray(),
                expectedChildId = "child",
                expectedPairingId = "pair",
                rememberAfterAuthentication = true
            )
        )
        var connectedRequest: String? = null
        setAddressScreen(requestId) { connectedRequest = it }

        composeTestRule.onNodeWithTag("pending_credential_notice").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("manual_pairing_code_field").assertCountEquals(0)
        composeTestRule.onNodeWithTag("ip_address_field").performTextInput("child.local")
        composeTestRule.onNodeWithTag("connect_button").performClick()

        assertEquals(requestId, connectedRequest)
        assertEquals("child.local", PendingConnections.store.lease(requestId)?.address)
        assertEquals(10000, PendingConnections.store.lease(requestId)?.port)
    }

    @Test
    fun expandedPortOverride_rejectsInvalidRange() {
        setAddressScreen()
        composeTestRule.onNodeWithTag("ip_address_field").performTextInput("192.168.1.2")
        composeTestRule.onNodeWithTag("manual_pairing_code_field").performTextInput("babyroom42")
        composeTestRule.onNodeWithTag("port_override_disclosure").performClick()
        composeTestRule.onNodeWithTag("port_field").performTextClearance()
        composeTestRule.onNodeWithTag("port_field").performTextInput("99999")

        composeTestRule.onNodeWithText("Enter a port from 1 to 65535").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_button").assertIsNotEnabled()
    }

    @Test
    fun pendingCredentialExpiryImmediatelyReplacesManualFormWithRecovery() {
        val requestId = PendingConnections.store.put(
            PendingConnection(name = "Nursery", pairingCode = "babyroom42".toCharArray())
        )
        setAddressScreen(requestId)
        composeTestRule.onNodeWithTag("pending_credential_notice").assertIsDisplayed()

        composeTestRule.runOnIdle { PendingConnections.store.remove(requestId) }

        composeTestRule.onNodeWithTag("expired_pairing_recovery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pair_again_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_regular_wifi").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ip_address_field").assertDoesNotExist()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.scanned_pairing_reused)
        ).assertDoesNotExist()
    }

    @Test
    fun manualFormConnectActionRemainsReachableAtTwoHundredPercent() {
        val density = composeTestRule.activity.resources.displayMetrics.density
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                DiscoverAddressScreen(onNavigateBack = {}, onConnect = {})
            }
        }

        composeTestRule.onNodeWithTag("connect_button").performScrollTo().assertIsDisplayed()
    }

    private fun setAddressScreen(
        requestId: String = "",
        onConnect: (String) -> Unit = {}
    ) {
        composeTestRule.setContent {
            DiscoverAddressScreen(
                requestId = requestId,
                onNavigateBack = {},
                onConnect = onConnect
            )
        }
    }
}
