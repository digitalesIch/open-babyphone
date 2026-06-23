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
class StartScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun startScreen_displaysAppTitle() {
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = {}
            )
        }
        composeTestRule.onNodeWithText("Open Babyphone").assertIsDisplayed()
    }

    @Test
    fun startScreen_displaysChildDeviceButton() {
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = {}
            )
        }
        composeTestRule.onNodeWithText("Use as Child Device").assertIsDisplayed()
    }

    @Test
    fun startScreen_displaysParentDeviceButton() {
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = {}
            )
        }
        composeTestRule.onNodeWithText("Use as Parent Device").assertIsDisplayed()
    }

    @Test
    fun startScreen_displaysChildDescription() {
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = {}
            )
        }
        composeTestRule.onNodeWithText("Device is placed with baby, and sends audio to a paired parent device")
            .assertIsDisplayed()
    }

    @Test
    fun startScreen_displaysParentDescription() {
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = {}
            )
        }
        composeTestRule.onNodeWithText("Pairs with child device and plays received audio")
            .assertIsDisplayed()
    }
}