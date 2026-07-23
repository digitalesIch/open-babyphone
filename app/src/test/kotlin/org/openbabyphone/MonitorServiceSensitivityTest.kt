package org.openbabyphone

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MonitorServiceSensitivityTest {
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(OpenBabyphoneApplication.SETTINGS_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `active service listener applies sensitivity without restarting service`() {
        val controller = Robolectric.buildService(MonitorService::class.java).create()
        val service = controller.get()

        MicrophoneSensitivityPreferences.write(context, MicrophoneSensitivity.VERY_HIGH)

        val gainField = MonitorService::class.java.getDeclaredField("microphoneGain").apply {
            isAccessible = true
        }
        assertEquals(MicrophoneSensitivity.VERY_HIGH.gain, gainField.getFloat(service), 0.001f)
        controller.destroy()
    }
}
