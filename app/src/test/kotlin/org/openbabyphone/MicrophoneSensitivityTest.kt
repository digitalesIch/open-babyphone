package org.openbabyphone

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MicrophoneSensitivityTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(OpenBabyphoneApplication.SETTINGS_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `fromPreferenceValue null returns NORMAL`() {
        assertEquals(MicrophoneSensitivity.NORMAL, MicrophoneSensitivity.fromPreferenceValue(null))
    }

    @Test
    fun `fromPreferenceValue unknown returns NORMAL`() {
        assertEquals(MicrophoneSensitivity.NORMAL, MicrophoneSensitivity.fromPreferenceValue("unknown"))
    }

    @Test
    fun `fromPreferenceValue normal returns NORMAL`() {
        assertEquals(MicrophoneSensitivity.NORMAL, MicrophoneSensitivity.fromPreferenceValue("normal"))
    }

    @Test
    fun `fromPreferenceValue high returns HIGH`() {
        assertEquals(MicrophoneSensitivity.HIGH, MicrophoneSensitivity.fromPreferenceValue("high"))
    }

    @Test
    fun `fromPreferenceValue very_high returns VERY_HIGH`() {
        assertEquals(MicrophoneSensitivity.VERY_HIGH, MicrophoneSensitivity.fromPreferenceValue("very_high"))
    }

    @Test
    fun `applyGain with NORMAL is no-op`() {
        val samples = shortArrayOf(100, -200, 500, -1000, 30000, -30000)
        val original = samples.copyOf()
        MicrophoneSensitivity.applyGain(samples, samples.size, 1.0f)
        for (i in samples.indices) {
            assertEquals(original[i], samples[i])
        }
    }

    @Test
    fun `applyGain with HIGH increases small samples`() {
        val samples = shortArrayOf(100, -200, 500)
        MicrophoneSensitivity.applyGain(samples, samples.size, 2.0f)
        assertEquals(200, samples[0].toInt())
        assertEquals(-400, samples[1].toInt())
        assertEquals(1000, samples[2].toInt())
    }

    @Test
    fun `applyGain with VERY_HIGH increases small samples`() {
        val samples = shortArrayOf(50, -100)
        MicrophoneSensitivity.applyGain(samples, samples.size, 4.0f)
        assertEquals(200, samples[0].toInt())
        assertEquals(-400, samples[1].toInt())
    }

    @Test
    fun `applyGain clamps to Short range without overflow`() {
        val samples = shortArrayOf(30000, -30000)
        MicrophoneSensitivity.applyGain(samples, samples.size, 4.0f)
        assertTrue("Positive should be clamped to <= 32767", samples[0] <= 32767)
        assertTrue("Negative should be clamped to >= -32768", samples[1] >= -32768)
    }

    @Test
    fun `applyGain soft clips large values below hard max`() {
        val samples = shortArrayOf(32000)
        MicrophoneSensitivity.applyGain(samples, samples.size, 4.0f)
        val amplified = 32000 * 4
        val overshoot = (amplified - 32767.0f) / 32767.0f
        val expected = (32767.0f * (1.0f - 0.15f * overshoot.coerceAtMost(1.0f))).toInt()
        assertEquals(expected, samples[0].toInt())
    }

    @Test
    fun `applyGain respects length and leaves rest unchanged`() {
        val samples = shortArrayOf(100, 200, 300, 400, 500)
        MicrophoneSensitivity.applyGain(samples, 3, 2.0f)
        assertEquals(200, samples[0].toInt())
        assertEquals(400, samples[1].toInt())
        assertEquals(600, samples[2].toInt())
        assertEquals(400, samples[3].toInt())
        assertEquals(500, samples[4].toInt())
    }

    @Test
    fun `applyGain with gain below 1 is no-op`() {
        val samples = shortArrayOf(100, -200)
        val original = samples.copyOf()
        MicrophoneSensitivity.applyGain(samples, samples.size, 0.5f)
        for (i in samples.indices) {
            assertEquals(original[i], samples[i])
        }
    }

    @Test
    fun `applyGain handles zero samples`() {
        val samples = shortArrayOf(0, 0, 0)
        MicrophoneSensitivity.applyGain(samples, samples.size, 4.0f)
        assertEquals(0, samples[0].toInt())
        assertEquals(0, samples[1].toInt())
        assertEquals(0, samples[2].toInt())
    }

    @Test
    fun `applyGain handles empty array`() {
        val samples = shortArrayOf()
        MicrophoneSensitivity.applyGain(samples, 0, 2.0f)
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `gain values are correct`() {
        assertEquals(1.0f, MicrophoneSensitivity.NORMAL.gain, 0.001f)
        assertEquals(2.0f, MicrophoneSensitivity.HIGH.gain, 0.001f)
        assertEquals(4.0f, MicrophoneSensitivity.VERY_HIGH.gain, 0.001f)
    }

    @Test
    fun `preferenceValue strings are correct`() {
        assertEquals("normal", MicrophoneSensitivity.NORMAL.preferenceValue)
        assertEquals("high", MicrophoneSensitivity.HIGH.preferenceValue)
        assertEquals("very_high", MicrophoneSensitivity.VERY_HIGH.preferenceValue)
    }

    @Test
    fun `preferences read defaults to normal`() {
        assertEquals(MicrophoneSensitivity.NORMAL, MicrophoneSensitivityPreferences.read(context))
    }

    @Test
    fun `preferences read uses settings preference`() {
        context.getSharedPreferences(OpenBabyphoneApplication.SETTINGS_PREFS_NAME, Application.MODE_PRIVATE)
            .edit()
            .putString(MicrophoneSensitivityPreferences.KEY, MicrophoneSensitivity.HIGH.preferenceValue)
            .apply()

        assertEquals(MicrophoneSensitivity.HIGH, MicrophoneSensitivityPreferences.read(context))
    }

    @Test
    fun `preferences read migrates legacy pairing preference`() {
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

    @Test
    fun `preferences write persists selected sensitivity`() {
        assertEquals(
            true,
            MicrophoneSensitivityPreferences.write(context, MicrophoneSensitivity.HIGH)
        )

        assertEquals(MicrophoneSensitivity.HIGH, MicrophoneSensitivityPreferences.read(context))
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