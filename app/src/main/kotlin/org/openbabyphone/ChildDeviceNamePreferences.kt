package org.openbabyphone

import android.content.Context

object ChildDeviceNamePreferences {
    fun read(context: Context, defaultName: String): String {
        require(DeviceName.isValid(defaultName)) { "Default child name must be valid" }
        val prefs = context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(MonitorService.PREF_KEY_DEVICE_NAME, null)
        val normalized = stored?.let(DeviceName::normalize)
        val name = normalized?.takeIf(DeviceName::isValid) ?: defaultName
        if (stored != name) {
            check(prefs.edit().putString(MonitorService.PREF_KEY_DEVICE_NAME, name).commit()) {
                "Failed to persist child device name"
            }
        }
        return name
    }

    fun write(context: Context, name: String): String? {
        val normalized = DeviceName.normalize(name)
        if (!DeviceName.isValid(normalized)) return null
        return normalized.takeIf {
            context.getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(MonitorService.PREF_KEY_DEVICE_NAME, normalized)
                .commit()
        }
    }
}
