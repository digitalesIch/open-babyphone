package org.openbabyphone

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.ui.theme.QuietEngineTheme
import org.openbabyphone.viewmodel.DiscoverUiState
import org.openbabyphone.viewmodel.DiscoveredDevice
import org.openbabyphone.viewmodel.KnownChildRow
import org.openbabyphone.viewmodel.KnownChildStatus
import org.openbabyphone.viewmodel.ListenRequest
import org.openbabyphone.viewmodel.PairingFlowState

@RunWith(AndroidJUnit4::class)
class DiscoverScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun newParent_hasOnePrimaryQrActionAndConciseGuidance() {
        setParentHome(DiscoverUiState())

        composeTestRule.onNodeWithText("Connect to a child phone").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scan_qr_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cannot_scan_button").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Nearby child devices").assertCountEquals(0)
    }

    @Test
    fun knownChildren_showOnlyFriendlyRowsAndSemanticActions() {
        val child1 = TrustedChild(
            "raw-child-id-1",
            "raw-pair-id-1",
            "Nursery",
            "last-known-host",
            10000
        )
        val child2 = TrustedChild("raw-child-id-2", "raw-pair-id-2", "Bedroom")
        val child3 = TrustedChild("raw-child-id-3", "raw-pair-id-3", "Travel cot")
        setParentHome(
            DiscoverUiState(
                trustedChildren = listOf(child1, child2, child3),
                knownChildren = listOf(
                    KnownChildRow(child1, KnownChildStatus.Available, null),
                    KnownChildRow(child2, KnownChildStatus.NotFound, null),
                    KnownChildRow(child3, KnownChildStatus.PairAgain, null)
                )
            )
        )

        composeTestRule.onNodeWithText("Nursery").assertIsDisplayed()
        composeTestRule.onNodeWithText("Available").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not found").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pair again").assertIsDisplayed()
        composeTestRule.onNodeWithTag("known_child_action_1").assertIsNotEnabled()
        composeTestRule.onAllNodesWithText("raw-child-id-1").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("last-known-host").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Forget").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Enter Address").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Wi-Fi Direct connection (experimental)").assertCountEquals(0)
    }

    @Test
    fun lookingState_isFocusedAndHasNoStopControl() {
        setParentHome(
            DiscoverUiState(
                pairingFlow = PairingFlowState.LookingForChild(
                    "Nursery",
                    "hidden-child",
                    "hidden-pair",
                    "hidden-request"
                )
            )
        )

        composeTestRule.onNodeWithText("Looking for Nursery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pairing_progress").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Stop discovery").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("hidden-child").assertCountEquals(0)
    }

    @Test
    fun readyState_requiresExplicitStartAndShowsFeedbackGuidance() {
        setParentHome(
            DiscoverUiState(
                pairingFlow = PairingFlowState.Ready(
                    "Nursery",
                    ListenRequest("request", "child", "pair")
                )
            )
        )

        composeTestRule.onNodeWithText("Ready to listen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nursery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_listening_button").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Move the parent device away from the child device before listening to avoid audio feedback."
        ).assertIsDisplayed()
    }

    @Test
    fun timeoutState_hasAllRecoveryActions() {
        var helpRequest: String? = null
        setParentHome(
            DiscoverUiState(
                pairingFlow = PairingFlowState.ChildNotFound(
                    "Nursery",
                    "child",
                    "pair",
                    "request"
                )
            ),
            onConnectionHelp = { helpRequest = it }
        )

        composeTestRule.onNodeWithText("Child not found").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retry_pairing_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scan_again_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connection Help").performClick()
        assertEquals("request", helpRequest)
    }

    @Test
    fun normalParentHelp_hasNoPendingRequestAndNoAdvancedActions() {
        var sentinel: String? = "not-called"
        setParentHome(DiscoverUiState(), onConnectionHelp = { sentinel = it })

        composeTestRule.onNodeWithTag("parent_connection_help").performClick()

        assertEquals(null, sentinel)
        composeTestRule.onAllNodesWithText("Manual address").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Try Wi-Fi Direct").assertCountEquals(0)
    }

    @Test
    fun qrReadyAndNotFoundActionsRemainReachableAtTwoHundredPercent() {
        val density = composeTestRule.activity.resources.displayMetrics.density
        var state = androidx.compose.runtime.mutableStateOf<DiscoverUiState>(
            DiscoverUiState(
                pairingFlow = PairingFlowState.Ready(
                    "Nursery",
                    ListenRequest("request", "child", "pair")
                )
            )
        )
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                QuietEngineTheme {
                    ParentHomeContent(
                        uiState = state.value,
                        onScanQr = {},
                        onCannotScan = {},
                        onKnownChildAction = { _, _ -> },
                        onRetry = {},
                        onStartListening = {},
                        onConnectionHelp = {}
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("start_listening_button").performScrollTo().assertIsDisplayed()

        composeTestRule.runOnIdle {
            state.value = DiscoverUiState(
                pairingFlow = PairingFlowState.ChildNotFound(
                    "Nursery", "child", "pair", "request"
                )
            )
        }
        composeTestRule.onNodeWithTag("scan_again_button").performScrollTo().assertIsDisplayed()
    }

    private fun setParentHome(
        state: DiscoverUiState,
        onConnectionHelp: (String?) -> Unit = {}
    ) {
        composeTestRule.setContent {
            QuietEngineTheme {
                ParentHomeContent(
                    uiState = state,
                    onScanQr = {},
                    onCannotScan = {},
                    onKnownChildAction = { _, _ -> },
                    onRetry = {},
                    onStartListening = {},
                    onConnectionHelp = onConnectionHelp
                )
            }
        }
    }
}
