package org.openbabyphone

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ThemePreferencesTest {
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(OpenBabyphoneApplication.SETTINGS_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun resetTheme() {
        ThemePreferences.apply(ThemeMode.SYSTEM)
    }

    @Test
    fun `theme defaults to system and resolves against system state`() {
        assertEquals(ThemeMode.SYSTEM, ThemePreferences.read(context))
        assertTrue(ThemeMode.SYSTEM.useDarkTheme(true))
        assertEquals(false, ThemeMode.SYSTEM.useDarkTheme(false))
    }

    @Test
    fun `write persists and immediately applies dark mode`() {
        assertTrue(ThemePreferences.write(context, ThemeMode.DARK))

        assertEquals(ThemeMode.DARK, ThemePreferences.read(context))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        assertTrue(ThemeMode.DARK.useDarkTheme(false))
    }
}
