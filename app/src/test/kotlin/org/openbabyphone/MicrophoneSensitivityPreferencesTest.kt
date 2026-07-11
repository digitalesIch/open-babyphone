package org.openbabyphone

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MicrophoneSensitivityPreferencesTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
        context.getSharedPreferences(OpenBabyphoneApplication.SETTINGS_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().clear().apply()
        context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test
    fun `read defaults to normal`() {
        assertEquals(MicrophoneSensitivity.NORMAL, MicrophoneSensitivityPreferences.read(context))
    }

    @Test
    fun `read uses settings preference`() {
        context.getSharedPreferences(OpenBabyphoneApplication.SETTINGS_PREFS_NAME, Application.MODE_PRIVATE)
            .edit()
            .putString(MicrophoneSensitivityPreferences.KEY, MicrophoneSensitivity.HIGH.preferenceValue)
            .apply()

        assertEquals(MicrophoneSensitivity.HIGH, MicrophoneSensitivityPreferences.read(context))
    }

    @Test
    fun `read migrates legacy pairing preference`() {
        context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
            .edit()
            .putString(MonitorService.PREF_KEY_MICROPHONE_SENSITIVITY, MicrophoneSensitivity.VERY_HIGH.preferenceValue)
            .apply()

        assertEquals(MicrophoneSensitivity.VERY_HIGH, MicrophoneSensitivityPreferences.read(context))
        assertEquals(
            MicrophoneSensitivity.VERY_HIGH.preferenceValue,
            context.getSharedPreferences(OpenBabyphoneApplication.SETTINGS_PREFS_NAME, Application.MODE_PRIVATE)
                .getString(MicrophoneSensitivityPreferences.KEY, null)
        )
    }
}
