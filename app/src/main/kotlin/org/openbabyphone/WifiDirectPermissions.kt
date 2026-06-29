/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open Babyphone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Babyphone. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openbabyphone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Resolves the runtime permission required for Wi-Fi Direct discovery based on
 * the Android version.
 *
 * Android 13+ uses [Manifest.permission.NEARBY_WIFI_DEVICES]; earlier versions
 * use [Manifest.permission.ACCESS_FINE_LOCATION] and additionally require
 * Location Services to be enabled.
 */
object WifiDirectPermissions {

    /**
     * The permission the app must request at runtime to use Wi-Fi Direct.
     */
    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

    /**
     * Returns `true` if the runtime permission for Wi-Fi Direct is granted.
     */
    fun isGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            requiredPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }
}