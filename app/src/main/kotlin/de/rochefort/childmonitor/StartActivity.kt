/*
 * This file is part of Child Monitor.
 *
 * Child Monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Child Monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Child Monitor. If not, see <http://www.gnu.org/licenses/>.
 */
package de.rochefort.childmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class StartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "ChildMonitor launched")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
        val monitorButton = findViewById<Button>(R.id.useChildDevice)
        monitorButton.setOnClickListener { _: View? ->
            Log.i(TAG, "Starting up monitor")
            if (isAudioRecordingPermissionGranted && isNotificationPermissionGranted) {
                startActivity(Intent(applicationContext, MonitorActivity::class.java))
            } else {
                requestMonitorPermissions()
            }
        }
        val connectButton = findViewById<Button>(R.id.useParentDevice)
        connectButton.setOnClickListener { _: View? ->
            Log.i(TAG, "Starting connection activity")
            if (isMulticastPermissionGranted && isNotificationPermissionGranted) {
                val i = Intent(applicationContext, DiscoverActivity::class.java)
                startActivity(i)
            } else {
                requestDiscoverPermissions()
            }
        }
        val settingsButton = findViewById<Button>(R.id.settingsButton)
        settingsButton.setOnClickListener { _: View? ->
            Log.i(TAG, "Opening settings")
            startActivity(Intent(applicationContext, SettingsActivity::class.java))
        }
    }

    private val isMulticastPermissionGranted: Boolean
        get() = (ContextCompat.checkSelfPermission(this@StartActivity, Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)
                == PackageManager.PERMISSION_GRANTED)
    private val isAudioRecordingPermissionGranted: Boolean
        get() = (ContextCompat.checkSelfPermission(this@StartActivity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED)
    private val isNotificationPermissionGranted: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                (ContextCompat.checkSelfPermission(this@StartActivity, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED)

    private fun requestMonitorPermissions() {
        val permissions = mutableListOf<String>()
        if (!isAudioRecordingPermissionGranted) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (!isNotificationPermissionGranted) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this@StartActivity, permissions.toTypedArray(),
                    PERMISSIONS_REQUEST_RECORD_AUDIO)
        }
    }

    private fun requestDiscoverPermissions() {
        val permissions = mutableListOf<String>()
        if (!isMulticastPermissionGranted) {
            permissions.add(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)
        }
        if (!isNotificationPermissionGranted) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this@StartActivity, permissions.toTypedArray(),
                    PERMISSIONS_REQUEST_MULTICAST)
        } else {
            startActivity(Intent(applicationContext, DiscoverActivity::class.java))
        }
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(this@StartActivity, arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSIONS_REQUEST_RECORD_AUDIO)
    }

    private fun requestMulticastPermission() {
        ActivityCompat.requestPermissions(this@StartActivity, arrayOf(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE),
                PERMISSIONS_REQUEST_MULTICAST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO && grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(applicationContext, MonitorActivity::class.java))
        } else if (requestCode == PERMISSIONS_REQUEST_MULTICAST) {
            // its okay if the permission was denied... the user will have to type the address manually
            startActivity(Intent(applicationContext, DiscoverActivity::class.java))
        }
    }

    companion object {
        const val TAG = "ChildMonitor"
        private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 298349824
        private const val PERMISSIONS_REQUEST_MULTICAST = 298349825
    }
}
