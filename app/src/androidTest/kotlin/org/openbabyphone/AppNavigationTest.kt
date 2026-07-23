package org.openbabyphone

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.navigation.Discover
import org.openbabyphone.navigation.Monitor
import org.openbabyphone.navigation.Start
import org.openbabyphone.ui.theme.QuietEngineTheme

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun startRoutesToBothRolesThroughNavHost() {
        lateinit var navController: NavHostController
        setNavigationContent(Start) { navController = it }

        composeTestRule.onNodeWithTag("child_role_card").performClick()
        composeTestRule.onNodeWithText("Monitor destination").assertIsDisplayed()
        composeTestRule.runOnIdle { navController.popBackStack() }
        composeTestRule.onNodeWithTag("parent_role_card").performClick()

        composeTestRule.onNodeWithText("Discover destination").assertIsDisplayed()
    }

    @Test
    fun confirmedStopFromActiveStartReplacesMonitorWithStart() {
        lateinit var navController: NavHostController
        setNavigationContent(Monitor) { navController = it }

        composeTestRule.onNodeWithText("Monitor destination").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirmed_stop").performClick()

        composeTestRule.onNodeWithTag("child_role_card").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertFalse(navController.popBackStack())
        }
    }

    private fun setNavigationContent(
        startDestination: Any,
        onController: (NavHostController) -> Unit
    ) {
        composeTestRule.setContent {
            QuietEngineTheme {
                val navController = rememberNavController()
                onController(navController)
                NavHost(navController = navController, startDestination = startDestination) {
                    composable<Start> {
                        StartScreen(
                            onNavigateToMonitor = { navController.navigate(Monitor) },
                            onNavigateToDiscover = { navController.navigate(Discover) }
                        )
                    }
                    composable<Monitor> {
                        Text("Monitor destination")
                        TextButton(
                            onClick = { navigateFromStoppedMonitor(navController) },
                            modifier = Modifier.testTag("confirmed_stop")
                        ) {
                            Text("Confirmed stop")
                        }
                    }
                    composable<Discover> { Text("Discover destination") }
                }
            }
        }
    }
}
