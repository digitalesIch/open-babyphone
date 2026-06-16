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

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import de.rochefort.childmonitor.MonitorService.MonitorBinder

class MonitorActivity : Activity() {
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to an explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            val bs = (service as MonitorBinder).service
            bs.monitorActivity = this@MonitorActivity
            bs.updateMonitorActivity()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            Toast.makeText(this@MonitorActivity, R.string.disconnected,
                    Toast.LENGTH_SHORT).show()
        }
    }
    private var shouldUnbind = false
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "ChildMonitor start")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)
        val pairingCodePreferences = getSharedPreferences(MonitorService.PAIRING_PREFS_NAME, MODE_PRIVATE)
        val pairingCodeField = findViewById<EditText>(R.id.pairingCodeField)
        pairingCodeField.setText(pairingCodePreferences.getString(MonitorService.PREF_KEY_PAIRING_CODE, ""))
        pairingCodeField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                pairingCodePreferences.edit()
                        .putString(MonitorService.PREF_KEY_PAIRING_CODE, s?.toString()?.trim() ?: "")
                        .apply()
            }
        })
        val addressText = findViewById<TextView>(R.id.address)
        val listenAddresses = listenAddresses
        if (listenAddresses.isNotEmpty()) {
            val sb = StringBuilder()
            for (i in listenAddresses.indices) {
                val listenAddress = listenAddresses[i]
                sb.append(listenAddress)
                if (i != listenAddresses.size - 1) {
                    sb.append("\n\n")
                }
            }
            addressText.text = sb.toString()
        } else {
            addressText.setText(R.string.notConnected)
        }
        ensureServiceRunningAndBind()
    }

    private val listenAddresses: List<String>
        get() {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val listenAddresses: MutableList<String> = ArrayList()
            for (network in cm.allNetworks) {
                val networkInfo = cm.getNetworkInfo(network)
                val connected = networkInfo!!.isConnected
                if (connected) {
                    val linkAddresses = cm.getLinkProperties(network)!!.linkAddresses
                    for (linkAddress in linkAddresses) {
                        val address = linkAddress.address
                        val hostAddress = address.hostAddress
                        if (!address.isLinkLocalAddress && !address.isLoopbackAddress && hostAddress != null) {
                            listenAddresses.add(hostAddress + " (" + networkInfo.typeName + ")")
                        }
                    }
                }
            }
            return listenAddresses
        }

    public override fun onDestroy() {
        doUnbindAndStopService()
        super.onDestroy()
    }

    private fun ensureServiceRunningAndBind() {
        val context: Context = this
        val intent = Intent(context, MonitorService::class.java)
        ContextCompat.startForegroundService(context, intent)
        // Attempts to establish a connection with the service.  We use an
        // explicit class name because we want a specific service
        // implementation that we know will be running in our own process
        // (and thus won't be supporting component replacement by other
        // applications).
        if (bindService(intent, connection, BIND_AUTO_CREATE)) {
            this.shouldUnbind = true
            Log.i(TAG, "Bound monitor service")
        } else {
            Log.e(TAG, "Error: The requested service doesn't " +
                    "exist, or this client isn't allowed access to it.")
        }
    }

    private fun doUnbindAndStopService() {
        if (this.shouldUnbind) {
            this.shouldUnbind = false
            // Release information about the service's state.
            unbindService(connection)
        }
        val context: Context = this
        val intent = Intent(context, MonitorService::class.java)
        context.stopService(intent)
    }

    companion object {
        const val TAG = "ChildMonitor"
    }
}
