package de.rochefort.childmonitor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
}