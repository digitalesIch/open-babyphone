package org.openbabyphone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.ListenSessionError
import org.openbabyphone.service.ListenSessionState
import org.openbabyphone.service.ServiceConnectionManager
import org.openbabyphone.viewmodel.ListenUiState
import org.openbabyphone.viewmodel.ListenViewModel
import org.openbabyphone.viewmodel.listenPresentation

@RunWith(AndroidJUnit4::class)
class ListenScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun resetRepository() {
        ListenServiceRepository.reset()
        PendingConnections.store.clear()
    }

    @Test
    fun connectingAndReconnecting_eachShowOneSpinnerAndOneHero() {
        var state by mutableStateOf(uiState(ListenSessionState.Connecting))
        setListenContent { state }

        composeTestRule.onAllNodesWithTag("listen_state_hero").assertCountEquals(1)
        composeTestRule.onAllNodesWithTag("listen_state_spinner").assertCountEquals(1)
        composeTestRule.onNodeWithText("Connecting").assertIsDisplayed()

        composeTestRule.runOnIdle {
            state = uiState(ListenSessionState.Reconnecting(1, 5))
        }
        composeTestRule.onAllNodesWithTag("listen_state_spinner").assertCountEquals(1)
        composeTestRule.onNodeWithText("Reconnecting").assertIsDisplayed()
    }

    @Test
    fun listeningMeter_distinguishesQuietSoundLoudAndStale() {
        var state by mutableStateOf(listeningState(floatArrayOf(0f), 4_000L))
        setListenContent(nowMillis = 5_000L) { state }

        composeTestRule.onNodeWithText("Quiet").assertIsDisplayed()

        composeTestRule.runOnIdle {
            state = listeningState(floatArrayOf(0.2f), 4_000L)
        }
        composeTestRule.onNodeWithText("Sound detected").assertIsDisplayed()

        composeTestRule.runOnIdle {
            state = listeningState(floatArrayOf(0.9f), 4_000L)
        }
        composeTestRule.onNodeWithText("Loud sound").assertIsDisplayed()

        composeTestRule.runOnIdle {
            state = listeningState(floatArrayOf(0f), 1_000L)
        }
        composeTestRule.onNodeWithText("No recent audio").assertIsDisplayed()
    }

    @Test
    fun disruptedAndLost_haveDistinctRecoveryPresentation() {
        var state by mutableStateOf(uiState(ListenSessionState.Disrupted))
        setListenContent { state }

        composeTestRule.onNodeWithText("Audio interrupted").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertDoesNotExist()

        composeTestRule.runOnIdle { state = uiState(ListenSessionState.Lost) }
        composeTestRule.onNodeWithText("Connection lost").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun typedFailures_showOnlyTheirActionablePrimaryAction() {
        var state by mutableStateOf(errorState(ListenSessionError.Unreachable))
        setListenContent { state }

        composeTestRule.onNodeWithText("Could not connect").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()

        composeTestRule.runOnIdle { state = errorState(ListenSessionError.Authentication) }
        composeTestRule.onNodeWithText("Pair again").assertIsDisplayed()

        composeTestRule.runOnIdle { state = errorState(ListenSessionError.CredentialStorage) }
        composeTestRule.onNodeWithText("Could not save pairing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()

        composeTestRule.runOnIdle { state = errorState(ListenSessionError.Playback) }
        composeTestRule.onNodeWithText("Could not play audio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()

        composeTestRule.runOnIdle { state = errorState(ListenSessionError.Decoding) }
        composeTestRule.onNodeWithText("Audio stream problem").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connection Help").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("listen_primary_action").assertCountEquals(1)
    }

    @Test
    fun readinessPolicy_surfacesAtMostOneNotice() {
        setListenContent(readinessNotice = ListenReadinessNotice.MutedMediaVolume) {
            uiState(ListenSessionState.Listening)
        }

        composeTestRule.onAllNodesWithTag("listen_readiness_notice").assertCountEquals(1)
        composeTestRule.onNodeWithText("Media volume is muted").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connection alerts are off").assertDoesNotExist()
    }

    @Test
    fun disconnectRemainsReachableAtTwoHundredPercentFontInShortLayout() {
        val baseDensity = composeTestRule.activity.resources.displayMetrics.density
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(baseDensity, fontScale = 2f)) {
                Box(Modifier.width(600.dp).height(220.dp)) {
                    ListenContent(
                        uiState = listeningState(floatArrayOf(0f), 4_000L),
                        childName = "Nursery",
                        nowMillis = 5_000L,
                        readinessNotice = ListenReadinessNotice.ConnectionAlertsDisabled,
                        onPrimaryAction = {},
                        onOpenNotificationSettings = {},
                        onDisconnect = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("disconnect_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun toolbarBackExplicitlyStopsNormalSession() {
        assertBackStopsSession(resumeOnly = false, systemBack = false)
    }

    @Test
    fun systemBackExplicitlyStopsNotificationResumedSession() {
        assertBackStopsSession(resumeOnly = true, systemBack = true)
    }

    @Test
    fun disconnectButtonExplicitlyStopsBeforeNavigating() {
        ListenServiceRepository.updateListening()
        var stopped = 0
        var navigated = 0
        setListenScreen(
            onNavigateBack = { navigated++ },
            unbindAndStopService = { _, _ -> stopped++ }
        )

        composeTestRule.onNodeWithTag("disconnect_button").performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, stopped)
            assertEquals(1, navigated)
        }
    }

    @Test
    fun retryDisposesOldBindingAndCreatesOneNewSession() {
        ListenServiceRepository.updateError(ListenSessionError.Unreachable, "failed")
        var bindCount = 0
        var disposeCount = 0
        setListenScreen(
            bindListenService = { context, _, _, _, _, _ ->
                bindCount++
                fakeBinding(context)
            },
            disposeServiceBinding = { _, _ -> disposeCount++ }
        )

        composeTestRule.onNodeWithText("Retry").performClick()

        composeTestRule.runOnIdle {
            assertEquals(2, bindCount)
            assertEquals(1, disposeCount)
        }
    }

    @Test
    fun authenticationPairAgainStopsAndReturnsToParentHomeAction() {
        ListenServiceRepository.updateError(ListenSessionError.Authentication, "failed")
        var stopped = 0
        var pairedAgain = 0
        setListenScreen(
            onPairAgain = { pairedAgain++ },
            unbindAndStopService = { _, _ -> stopped++ }
        )

        composeTestRule.onNodeWithTag("listen_primary_action").performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, stopped)
            assertEquals(1, pairedAgain)
        }
    }

    @Test
    fun decodingHelpStopsInsteadOfRetryingInvalidStream() {
        val requestId = PendingConnections.store.put(
            PendingConnection("host", 10_000, "Nursery", "code1234".toCharArray())
        )
        ListenServiceRepository.updateError(ListenSessionError.Decoding, "failed")
        var stopped = 0
        var openedHelp = 0
        setListenScreen(
            requestId = requestId,
            onConnectionHelp = { openedHelp++ },
            unbindAndStopService = { _, _ -> stopped++ }
        )

        composeTestRule.onNodeWithTag("listen_primary_action").performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, stopped)
            assertEquals(1, openedHelp)
            assertTrue(PendingConnections.store.contains(requestId))
        }
    }

    @Test
    fun credentialStorageRetryRetainsPendingRequest() {
        val requestId = PendingConnections.store.put(
            PendingConnection("host", 10_000, "Nursery", "code1234".toCharArray())
        )
        ListenServiceRepository.updateError(ListenSessionError.CredentialStorage, "failed")
        setListenScreen(requestId = requestId)

        composeTestRule.onNodeWithTag("listen_primary_action").performClick()

        composeTestRule.runOnIdle {
            assertTrue(PendingConnections.store.contains(requestId))
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun notificationDenialDoesNotBlockListeningAndShowsOneReadinessNotice() {
        ListenServiceRepository.updateListening()
        val requested = mutableListOf<String>()
        setListenScreen(
            permissionChecker = { _, _ -> false },
            permissionRequester = { permission, result ->
                requested += permission
                result(false)
            },
            readinessStatus = {
                ListenReadinessStatus(false, false, false, false, false)
            }
        )

        composeTestRule.onNodeWithText("Listening").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("listen_readiness_notice").assertCountEquals(1)
        composeTestRule.runOnIdle {
            assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), requested)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun notificationResultPrecedesListenServiceStartAndDenialStillProceeds() {
        var permissionResult: ((Boolean) -> Unit)? = null
        var bindCount = 0
        setListenScreen(
            permissionChecker = { _, _ -> false },
            permissionRequester = { _, result -> permissionResult = result },
            bindListenService = { context, _, _, _, _, _ ->
                bindCount++
                fakeBinding(context)
            }
        )

        composeTestRule.runOnIdle {
            assertEquals(0, bindCount)
            permissionResult?.invoke(false)
        }
        composeTestRule.runOnIdle { assertEquals(1, bindCount) }
    }

    @Test
    fun readinessWarningTransitionAddsPoliteLiveRegionButAudioMeterNeverDoes() {
        var notice by mutableStateOf<ListenReadinessNotice?>(null)
        composeTestRule.setContent {
            ListenContent(
                uiState = listeningState(floatArrayOf(0.2f), 4_000L),
                childName = "Nursery",
                nowMillis = 5_000L,
                readinessNotice = notice,
                onPrimaryAction = {},
                onOpenNotificationSettings = {},
                onDisconnect = {}
            )
        }
        composeTestRule.onNodeWithTag("listen_state_hero", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        composeTestRule.onNodeWithTag("audio_freshness", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))

        composeTestRule.runOnIdle { notice = ListenReadinessNotice.ConnectionAlertsDisabled }

        composeTestRule.onNodeWithTag("listen_readiness_notice", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeTestRule.onNodeWithTag("audio_freshness", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    private fun assertBackStopsSession(resumeOnly: Boolean, systemBack: Boolean) {
        ListenServiceRepository.updateListening()
        var stopped = 0
        var navigated = 0
        setListenScreen(
            resumeOnly = resumeOnly,
            onNavigateBack = { navigated++ },
            unbindAndStopService = { _, _ -> stopped++ }
        )

        if (systemBack) {
            composeTestRule.activityRule.scenario.onActivity {
                it.onBackPressedDispatcher.onBackPressed()
            }
        } else {
            composeTestRule.onNodeWithContentDescription("Back").performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(1, stopped)
            assertEquals(1, navigated)
        }
    }

    private fun setListenContent(
        nowMillis: Long = 5_000L,
        readinessNotice: ListenReadinessNotice? = null,
        state: () -> ListenUiState
    ) {
        composeTestRule.setContent {
            ListenContent(
                uiState = state(),
                childName = "Nursery",
                nowMillis = nowMillis,
                readinessNotice = readinessNotice,
                onPrimaryAction = {},
                onOpenNotificationSettings = {},
                onDisconnect = {}
            )
        }
    }

    private fun setListenScreen(
        requestId: String = "request",
        resumeOnly: Boolean = false,
        onNavigateBack: () -> Unit = {},
        onPairAgain: () -> Unit = {},
        onConnectionHelp: () -> Unit = {},
        bindListenService: (Context, ListenViewModel, String, String, String, Boolean) ->
            ServiceConnectionManager.ServiceBinding = { context, _, _, _, _, _ -> fakeBinding(context) },
        disposeServiceBinding: (Context, ServiceConnectionManager.ServiceBinding) -> Unit = { _, _ -> },
        unbindAndStopService: (Context, ServiceConnectionManager.ServiceBinding) -> Unit = { _, _ -> },
        permissionChecker: (Context, String) -> Boolean = { _, _ -> true },
        permissionRequester: ((String, (Boolean) -> Unit) -> Unit)? = null,
        readinessStatus: (Context) -> ListenReadinessStatus = { readyStatus() }
    ) {
        composeTestRule.setContent {
            ListenScreen(
                requestId = requestId,
                expectedChildId = "child",
                expectedPairingId = "pairing",
                resumeOnly = resumeOnly,
                onNavigateBack = onNavigateBack,
                onPairAgain = onPairAgain,
                onConnectionHelp = onConnectionHelp,
                bindListenService = bindListenService,
                disposeServiceBinding = disposeServiceBinding,
                unbindAndStopService = unbindAndStopService,
                stopListenService = {},
                permissionChecker = permissionChecker,
                permissionRequester = permissionRequester,
                readinessStatus = readinessStatus,
                openNotificationSettings = {}
            )
        }
    }

    private fun uiState(state: ListenSessionState): ListenUiState = ListenUiState(
        childDeviceName = "Nursery",
        sessionState = state,
        presentation = listenPresentation(composeTestRule.activity.application, state)
    )

    private fun errorState(type: ListenSessionError): ListenUiState =
        uiState(ListenSessionState.Error(type, "internal reason"))

    private fun listeningState(history: FloatArray, updatedAt: Long): ListenUiState =
        uiState(ListenSessionState.Listening).copy(
            volumeHistory = history,
            lastAudioUpdateAtMillis = updatedAt
        )

    private fun fakeBinding(context: Context): ServiceConnectionManager.ServiceBinding {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(
                name: android.content.ComponentName?,
                service: android.os.IBinder?
            ) = Unit

            override fun onServiceDisconnected(name: android.content.ComponentName?) = Unit
        }
        return ServiceConnectionManager.ServiceBinding(
            Intent(context, ListenService::class.java),
            connection,
            false
        )
    }

    private fun readyStatus() = ListenReadinessStatus(
        mediaVolumeMuted = false,
        postNotificationsGranted = true,
        appNotificationsEnabled = true,
        alertChannelEnabled = true,
        likelyExternalOutput = false
    )
}
