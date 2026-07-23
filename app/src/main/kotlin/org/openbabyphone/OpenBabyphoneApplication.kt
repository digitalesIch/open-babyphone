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

import android.app.Application
import android.content.Context

class OpenBabyphoneApplication : Application() {
    lateinit var trustedChildStore: TrustedChildStore
        private set
    val wifiDirectCleanupCoordinator = WifiDirectCleanupCoordinator()

    companion object {
        const val SETTINGS_PREFS_NAME = "settings"
    }

    override fun onCreate() {
        super.onCreate()
        check(getSharedPreferences("DiscoverPrefs", Context.MODE_PRIVATE).edit().clear().commit()) {
            "Failed to remove legacy parent pairing preferences"
        }
        trustedChildStore = TrustedChildStore(this)
        ThemePreferences.apply(ThemePreferences.read(this))
    }
}

fun Context.trustedChildStore(): TrustedChildStore =
    (applicationContext as OpenBabyphoneApplication).trustedChildStore

fun Context.wifiDirectCleanupCoordinator(): WifiDirectCleanupCoordinator =
    (applicationContext as OpenBabyphoneApplication).wifiDirectCleanupCoordinator
