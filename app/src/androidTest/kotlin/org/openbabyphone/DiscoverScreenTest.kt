package org.openbabyphone

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
                onNavigateToListen = { _, _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithText("Discover Child on Network").assertIsDisplayed()
    }

    @Test
    fun discoverScreen_displaysAddressButton() {
        composeTestRule.setContent {
            DiscoverScreen(
                onNavigateBack = {},
                onNavigateToAddressInput = {},
                onNavigateToListen = { _, _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithText("Select Child by Address").assertIsDisplayed()
    }

    @Test
    fun discoverScreen_displaysPairingCodeField() {
        composeTestRule.setContent {
            DiscoverScreen(
                onNavigateBack = {},
                onNavigateToAddressInput = {},
                onNavigateToListen = { _, _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithText("Pairing code (optional)").assertIsDisplayed()
    }
}