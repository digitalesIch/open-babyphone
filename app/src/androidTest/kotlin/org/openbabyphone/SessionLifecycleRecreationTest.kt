package org.openbabyphone

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.MonitorServiceRepository
import org.openbabyphone.service.MonitorSessionState
import org.openbabyphone.service.ServiceConnectionManager

@RunWith(AndroidJUnit4::class)
class SessionLifecycleRecreationTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun resetRepositories() {
        MonitorServiceRepository.reset()
        ListenServiceRepository.reset()
        PendingConnections.store.clear()
    }

    @After
    fun clearHost() {
        SessionLifecycleTestHost.content = {}
    }

    @Test
    fun recreatingActivityReattachesMonitorWithoutStoppingOrRestartingIt() {
        MonitorServiceRepository.updateSessionState(MonitorSessionState.WaitingForParent)
        var binds = 0
        var disposals = 0
        var stops = 0
        var starts = 0
        SessionLifecycleTestHost.content = {
            MonitorScreen(
                onNavigateBack = {},
                onConnectionHelp = {},
                bindMonitorService = { context ->
                    binds++
                    fakeBinding(context, MonitorService::class.java)
                },
                startMonitorService = { starts++; true },
                disposeServiceBinding = { _, _ -> disposals++ },
                unbindAndStopService = { _, _ -> stops++ },
                stopMonitorService = { stops++ },
                permissionChecker = { _, _ -> true },
                notificationWarningChecker = { false },
                openAppSettings = {},
                openBatteryOptimizationSettings = {}
            )
        }

        ActivityScenario.launch(SessionLifecycleTestActivity::class.java).use { scenario ->
            composeTestRule.waitUntil { binds == 1 }
            composeTestRule.onNodeWithText("Monitoring").assertIsDisplayed()

            scenario.recreate()

            composeTestRule.waitUntil { binds == 2 }
            composeTestRule.onNodeWithText("Monitoring").assertIsDisplayed()
            composeTestRule.runOnIdle {
                assertTrue(disposals >= 1)
                assertEquals(0, stops)
                assertEquals(0, starts)
            }
        }
    }

    @Test
    fun recreatingActivityReattachesListenWithoutStoppingIt() {
        ListenServiceRepository.startConnecting("Nursery")
        ListenServiceRepository.updateListening()
        var binds = 0
        var disposals = 0
        var stops = 0
        SessionLifecycleTestHost.content = {
            ListenScreen(
                requestId = "request",
                expectedChildId = "child",
                expectedPairingId = "pairing",
                resumeOnly = false,
                onNavigateBack = {},
                bindListenService = { context, _, _, _, _, _ ->
                    binds++
                    fakeBinding(context, ListenService::class.java)
                },
                disposeServiceBinding = { _, _ -> disposals++ },
                unbindAndStopService = { _, _ -> stops++ },
                stopListenService = { stops++ },
                permissionChecker = { _, _ -> true },
                readinessStatus = { readyStatus() },
                openNotificationSettings = {}
            )
        }

        ActivityScenario.launch(SessionLifecycleTestActivity::class.java).use { scenario ->
            composeTestRule.waitUntil { binds == 1 }
            composeTestRule.onNodeWithText("Listening").assertIsDisplayed()

            scenario.recreate()

            composeTestRule.waitUntil { binds == 2 }
            composeTestRule.onNodeWithText("Listening").assertIsDisplayed()
            composeTestRule.runOnIdle {
                assertTrue(disposals >= 1)
                assertEquals(0, stops)
            }
        }
    }

    @Test
    fun listenScreenKeepsScreenOnWhileVisibleAndClearsItOnLeave() {
        SessionLifecycleTestHost.content = {
            ListenScreen(
                requestId = "request",
                expectedChildId = "child",
                expectedPairingId = "pairing",
                resumeOnly = false,
                onNavigateBack = {},
                bindListenService = { context, _, _, _, _, _ ->
                    fakeBinding(context, ListenService::class.java)
                },
                disposeServiceBinding = { _, _ -> },
                unbindAndStopService = { _, _ -> },
                stopListenService = {},
                permissionChecker = { _, _ -> true },
                readinessStatus = { readyStatus() },
                openNotificationSettings = {}
            )
        }

        ActivityScenario.launch(SessionLifecycleTestActivity::class.java).use { scenario ->
            awaitScreenOn(scenario, expected = true)

            SessionLifecycleTestHost.content = {}
            awaitScreenOn(scenario, expected = false)
        }
    }

    private fun awaitScreenOn(scenario: ActivityScenario<SessionLifecycleTestActivity>, expected: Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        var matched = false
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { activity ->
                matched = (activity.window.attributes.flags and
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) == expected
            }
            if (matched) return
            SystemClock.sleep(200)
        }
        if (expected) {
            assertTrue("ListenScreen must keep the screen on while visible", matched)
        } else {
            assertFalse("Screen-on flag must be cleared when the listen screen leaves", matched)
        }
    }

    private fun fakeBinding(
        context: Context,
        serviceClass: Class<*>
    ): ServiceConnectionManager.ServiceBinding = ServiceConnectionManager.ServiceBinding(
        intent = Intent(context, serviceClass),
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) = Unit
            override fun onServiceDisconnected(name: android.content.ComponentName?) = Unit
        },
        bound = false
    )

    private fun readyStatus() = ListenReadinessStatus(
        mediaVolumeMuted = false,
        postNotificationsGranted = true,
        appNotificationsEnabled = true,
        alertChannelEnabled = true,
        likelyExternalOutput = false
    )
}
