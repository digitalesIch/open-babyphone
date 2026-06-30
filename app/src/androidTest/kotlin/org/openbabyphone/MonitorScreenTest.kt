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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
        composeTestRule.onNodeWithTag("pairing_qr_code").assertIsDisplayed()
    }

    @Test
    fun monitorScreen_showsValidationForInvalidPairingCode() {
        composeTestRule.setContent {
            MonitorScreen(onNavigateBack = {}, bindMonitorService = { fakeBinding(it) })
        }

        composeTestRule.onNodeWithTag("pairing_code_field").performTextClearance()
        composeTestRule.onNodeWithTag("pairing_code_field").performTextInput("invalid-code")

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.invalid_pairing_code_feedback))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Monitoring").assertIsNotEnabled()
    }

    @Test
    fun monitorScreen_hidesSetupAfterStart() {
        composeTestRule.setContent {
            MonitorScreen(onNavigateBack = {}, bindMonitorService = { fakeBinding(it) })
        }

        composeTestRule.onNodeWithText("Start Monitoring").performClick()

        composeTestRule.onNodeWithText("Stop Monitoring").assertIsDisplayed()
        composeTestRule.onNodeWithTag("loading_card").assertIsDisplayed()
    }

    @Test
    fun monitorScreen_showsAddParentDeviceButtonDuringMonitoring() {
        composeTestRule.setContent {
            MonitorScreen(onNavigateBack = {}, bindMonitorService = { fakeBinding(it) })
        }

        composeTestRule.onNodeWithText("Start Monitoring").performClick()
        composeTestRule.onNodeWithTag("add_parent_device_button").assertIsDisplayed()
    }

    @Test
    fun monitorScreen_addParentDeviceDialog_showsQrCodeAndPairingCode() {
        composeTestRule.setContent {
            MonitorScreen(onNavigateBack = {}, bindMonitorService = { fakeBinding(it) })
        }

        composeTestRule.onNodeWithText("Start Monitoring").performClick()
        composeTestRule.onNodeWithTag("add_parent_device_button").performClick()

        composeTestRule.onNodeWithTag("pairing_dialog_qr_code").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pairing_dialog_pairing_code").assertIsDisplayed()
    }

    @Test
    fun monitorScreen_addParentDeviceDialog_canBeClosed() {
        composeTestRule.setContent {
            MonitorScreen(onNavigateBack = {}, bindMonitorService = { fakeBinding(it) })
        }

        composeTestRule.onNodeWithText("Start Monitoring").performClick()
        composeTestRule.onNodeWithTag("add_parent_device_button").performClick()
        composeTestRule.onNodeWithText("Close").performClick()

        composeTestRule.onNodeWithTag("add_parent_device_button").assertIsDisplayed()
    }

    @Test
    fun monitorScreen_showsMicrophoneSensitivityCard() {
        composeTestRule.setContent {
            MonitorScreen(onNavigateBack = {}, bindMonitorService = { fakeBinding(it) })
        }

        composeTestRule.onNodeWithTag("microphone_sensitivity_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sensitivity_normal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sensitivity_high").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sensitivity_very_high").assertIsDisplayed()
    }

    private fun fakeBinding(context: Context): ServiceConnectionManager.ServiceBinding {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) = Unit
            override fun onServiceDisconnected(name: android.content.ComponentName?) = Unit
        }
        return ServiceConnectionManager.ServiceBinding(Intent(context, MonitorService::class.java), connection, false)
    }
}
