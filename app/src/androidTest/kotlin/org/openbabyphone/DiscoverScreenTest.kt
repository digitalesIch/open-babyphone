package org.openbabyphone

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoverScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun discoverScreen_displaysDiscoveryButton() {
        composeTestRule.setContent {
            DiscoverScreen(
                onNavigateBack = {},
                onNavigateToAddressInput = {},
                onNavigateToListen = { _, _, _, _ -> },
                autoStartDiscovery = false
            )
        }
        composeTestRule.onNodeWithText("Nearby child devices").assertIsDisplayed()
        composeTestRule.onNodeWithTag("discover_button").assertIsDisplayed()
    }

    @Test
    fun discoverScreen_advancedSection_showsAddressInputButton() {
        composeTestRule.setContent {
            DiscoverScreen(
                onNavigateBack = {},
                onNavigateToAddressInput = {},
                onNavigateToListen = { _, _, _, _ -> },
                autoStartDiscovery = false
            )
        }
        composeTestRule.onNodeWithTag("advanced_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("address_input_button").assertIsDisplayed()
    }

    @Test
    fun discoverScreen_hidesGlobalPairingCodeField() {
        composeTestRule.setContent {
            DiscoverScreen(
                onNavigateBack = {},
                onNavigateToAddressInput = {},
                onNavigateToListen = { _, _, _, _ -> },
                autoStartDiscovery = false
            )
        }
        composeTestRule.onAllNodesWithText("Pairing code (optional)").assertCountEquals(0)
    }

    @Test
    fun discoverScreen_displaysScanQrButton() {
        composeTestRule.setContent {
            DiscoverScreen(
                onNavigateBack = {},
                onNavigateToAddressInput = {},
                onNavigateToListen = { _, _, _, _ -> },
                onNavigateToWifiDirect = {},
                autoStartDiscovery = false
            )
        }
        composeTestRule.onNodeWithTag("scan_qr_button").assertIsDisplayed()
    }

    @Test
    fun discoverScreen_advancedSection_showsWifiDirectButton() {
        composeTestRule.setContent {
            DiscoverScreen(
                onNavigateBack = {},
                onNavigateToAddressInput = {},
                onNavigateToListen = { _, _, _, _ -> },
                onNavigateToWifiDirect = {},
                autoStartDiscovery = false
            )
        }
        composeTestRule.onNodeWithTag("advanced_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wifi_direct_button").assertIsDisplayed()
    }
}
