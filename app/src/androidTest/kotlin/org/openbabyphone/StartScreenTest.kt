package org.openbabyphone

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
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
    fun startScreen_displaysClickableChildRoleCard() {
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = {}
            )
        }
        composeTestRule.onNodeWithTag("child_role_card")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule.onNodeWithText("Child Device").assertIsDisplayed()
    }

    @Test
    fun startScreen_displaysClickableParentRoleCard() {
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = {}
            )
        }
        composeTestRule.onNodeWithTag("parent_role_card")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule.onNodeWithText("Parent Device").assertIsDisplayed()
    }

    @Test
    fun startScreen_displaysChildDescription() {
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = {}
            )
        }
        composeTestRule.onNodeWithText(
            "Place this phone near your baby to send audio to a paired parent device."
        )
            .assertIsDisplayed()
    }

    @Test
    fun startScreen_displaysParentDescription() {
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = {},
                onNavigateToSettings = {}
            )
        }
        composeTestRule.onNodeWithText(
            "Keep this phone with you to hear audio from the child device."
        )
            .assertIsDisplayed()
    }

    @Test
    fun childRoleCard_navigatesImmediately() {
        var navigated = false
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = { navigated = true },
                onNavigateToDiscover = {}
            )
        }

        composeTestRule.onNodeWithTag("child_role_card").performClick()

        composeTestRule.runOnIdle { assertTrue(navigated) }
    }

    @Test
    fun parentRoleCard_navigatesImmediately() {
        var navigated = false
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = { navigated = true }
            )
        }

        composeTestRule.onNodeWithTag("parent_role_card").performClick()

        composeTestRule.runOnIdle { assertTrue(navigated) }
    }

    @Test
    fun startScreen_displaysSettingsButton() {
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = {},
                onNavigateToSettings = {}
            )
        }
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
    }

    @Test
    fun settingsButton_navigatesInternally() {
        var navigated = false
        composeTestRule.setContent {
            StartScreen(
                onNavigateToMonitor = {},
                onNavigateToDiscover = {},
                onNavigateToSettings = { navigated = true }
            )
        }

        composeTestRule.onNodeWithTag("settings_button").performClick()

        composeTestRule.runOnIdle { assertTrue(navigated) }
    }

    @Test
    fun roleCardsRemainReachableAtTwoHundredPercentFontScale() {
        val density = composeTestRule.activity.resources.displayMetrics.density
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                StartScreen(onNavigateToMonitor = {}, onNavigateToDiscover = {})
            }
        }

        composeTestRule.onNodeWithTag("child_role_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("parent_role_card").performScrollTo().assertIsDisplayed()
    }
}
