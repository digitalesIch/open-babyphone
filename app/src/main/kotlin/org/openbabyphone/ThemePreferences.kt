package org.openbabyphone

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(val preferenceValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun useDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromPreferenceValue(value: String?): ThemeMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

object ThemePreferences {
    const val KEY = "theme_mode"
    const val DYNAMIC_COLORS_KEY = "use_system_colors"

    fun read(context: Context): ThemeMode = ThemeMode.fromPreferenceValue(
        context.getSharedPreferences(
            OpenBabyphoneApplication.SETTINGS_PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(KEY, null)
    )

    fun write(context: Context, mode: ThemeMode): Boolean {
        val saved = context.getSharedPreferences(
            OpenBabyphoneApplication.SETTINGS_PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit().putString(KEY, mode.preferenceValue).commit()
        if (saved) apply(mode)
        return saved
    }

    fun apply(mode: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }

    fun readDynamicColors(context: Context): Boolean =
        context.getSharedPreferences(
            OpenBabyphoneApplication.SETTINGS_PREFS_NAME,
            Context.MODE_PRIVATE
        ).getBoolean(DYNAMIC_COLORS_KEY, false)

    fun writeDynamicColors(context: Context, enabled: Boolean): Boolean =
        context.getSharedPreferences(
            OpenBabyphoneApplication.SETTINGS_PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit().putBoolean(DYNAMIC_COLORS_KEY, enabled).commit()
}
