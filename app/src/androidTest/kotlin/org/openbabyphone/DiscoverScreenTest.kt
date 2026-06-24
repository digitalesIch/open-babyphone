package org.openbabyphone

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
                onNavigateToListen = { _, _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithText("Discover Child on Network").assertIsDisplayed()
    }

    @Test
    fun discoverScreen_advancedSectionCollapsible_AddressButtonRevealed() {
        composeTestRule.setContent {
            DiscoverScreen(
                onNavigateBack = {},
                onNavigateToAddressInput = {},
                onNavigateToListen = { _, _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithTag("advanced_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("advanced_toggle").performClick()
        composeTestRule.onNodeWithText("Manual connection (advanced)").assertIsDisplayed()
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