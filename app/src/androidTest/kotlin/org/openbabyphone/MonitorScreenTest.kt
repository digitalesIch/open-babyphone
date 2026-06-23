package org.openbabyphone

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.openbabyphone.service.ServiceConnectionManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitorScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun monitorScreen_startsInSetupMode() {
        composeTestRule.setContent {
            MonitorScreen(onNavigateBack = {}, bindMonitorService = { fakeBinding(it) })
        }

        composeTestRule.onNodeWithText("Start Monitoring").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pairing_code_field").assertIsEnabled()
    }

    @Test
    fun monitorScreen_locksPairingCodeAfterStart() {
        composeTestRule.setContent {
            MonitorScreen(onNavigateBack = {}, bindMonitorService = { fakeBinding(it) })
        }

        composeTestRule.onNodeWithText("Start Monitoring").performClick()

        composeTestRule.onNodeWithText("Stop Monitoring").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pairing_code_field").assertIsNotEnabled()
    }

    private fun fakeBinding(context: Context): ServiceConnectionManager.ServiceBinding {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) = Unit
            override fun onServiceDisconnected(name: android.content.ComponentName?) = Unit
        }
        return ServiceConnectionManager.ServiceBinding(Intent(context, MonitorService::class.java), connection, false)
    }
}
