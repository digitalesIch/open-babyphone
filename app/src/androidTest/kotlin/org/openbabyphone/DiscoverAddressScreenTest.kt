package org.openbabyphone

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoverAddressScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun addressScreen_displaysInstructions() {
        composeTestRule.setContent {
            DiscoverAddressScreen(
                onNavigateBack = {},
                onConnect = { _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithText("Enter the IP Address and port of a child and click Connect").assertIsDisplayed()
    }

    @Test
    fun addressScreen_displaysConnectButtonDisabledWhenEmpty() {
        composeTestRule.setContent {
            DiscoverAddressScreen(
                onNavigateBack = {},
                onConnect = { _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithText("Connect").assertIsNotEnabled()
    }

    @Test
    fun addressScreen_displaysIpFieldLabel() {
        composeTestRule.setContent {
            DiscoverAddressScreen(
                onNavigateBack = {},
                onConnect = { _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithText("IP Address").assertIsDisplayed()
    }

    @Test
    fun addressScreen_displaysPortFieldLabel() {
        composeTestRule.setContent {
            DiscoverAddressScreen(
                onNavigateBack = {},
                onConnect = { _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithText("Port:").assertIsDisplayed()
    }

    @Test
    fun addressScreen_displaysPairingCodeFieldLabel() {
        composeTestRule.setContent {
            DiscoverAddressScreen(
                onNavigateBack = {},
                onConnect = { _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithText("Pairing code (optional)").assertIsDisplayed()
    }

    @Test
    fun addressScreen_rejectsInvalidPortRange() {
        composeTestRule.setContent {
            DiscoverAddressScreen(
                onNavigateBack = {},
                onConnect = { _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithTag("ip_address_field").performTextInput("192.168.1.42")
        composeTestRule.onNodeWithTag("port_field").performTextInput("99999")

        composeTestRule.onNodeWithText("Enter a port from 1 to 65535").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect").assertIsNotEnabled()
    }

    @Test
    fun addressScreen_acceptsValidPortRange() {
        composeTestRule.setContent {
            DiscoverAddressScreen(
                onNavigateBack = {},
                onConnect = { _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithTag("ip_address_field").performTextInput("192.168.1.42")

        composeTestRule.onNodeWithText("Connect").assertIsEnabled()
    }
}
