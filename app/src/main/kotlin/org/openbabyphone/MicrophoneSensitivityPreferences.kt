package org.openbabyphone

import android.content.Context

object MicrophoneSensitivityPreferences {
    const val KEY = "microphoneSensitivity"

    fun read(context: Context): MicrophoneSensitivity {
        val settingsPrefs = context.getSharedPreferences(OpenBabyphoneApplication.SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
        val settingsValue = settingsPrefs.getString(KEY, null)
        if (settingsValue != null) {
            return MicrophoneSensitivity.fromPreferenceValue(settingsValue)
        }

        val legacyPrefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyValue = legacyPrefs.getString(MonitorService.PREF_KEY_MICROPHONE_SENSITIVITY, null)
        if (legacyValue != null) {
            settingsPrefs.edit().putString(KEY, legacyValue).apply()
        }
        return MicrophoneSensitivity.fromPreferenceValue(legacyValue)
    }
}
