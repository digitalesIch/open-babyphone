package org.openbabyphone

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ChildDeviceNamePreferencesTest {
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `read persists automatic child phone name`() {
        assertEquals("Child phone", ChildDeviceNamePreferences.read(context, "Child phone"))
        assertEquals(
            "Child phone",
            context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(MonitorService.PREF_KEY_DEVICE_NAME, null)
        )
    }

    @Test
    fun `write normalizes and persists a valid name`() {
        assertEquals("Nursery", ChildDeviceNamePreferences.write(context, "  Nursery  "))
        assertEquals("Nursery", ChildDeviceNamePreferences.read(context, "Child phone"))
    }

    @Test
    fun `write rejects invalid names without replacing saved value`() {
        ChildDeviceNamePreferences.write(context, "Nursery")

        assertNull(ChildDeviceNamePreferences.write(context, "   "))
        assertNull(ChildDeviceNamePreferences.write(context, "a".repeat(64)))
        assertEquals("Nursery", ChildDeviceNamePreferences.read(context, "Child phone"))
    }
}
