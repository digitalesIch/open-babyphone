package org.openbabyphone

import android.app.Application
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BatteryOptimizationTest {
    @Test
    fun `request exemption intent targets this app package`() {
        val context = RuntimeEnvironment.getApplication() as Application
        val intent = BatteryOptimization.requestExemptionIntent(context)

        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }

    @Test
    fun `settings intent opens battery optimization settings`() {
        val intent = BatteryOptimization.settingsIntent()

        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intent.action)
    }
}
