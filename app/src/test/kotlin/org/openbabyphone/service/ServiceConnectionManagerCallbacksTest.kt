package org.openbabyphone.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import org.openbabyphone.ListenService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ServiceConnectionManagerCallbacksTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
    }

    @Test
    fun `unbindAndStopService clears service callbacks`() {
        val intent = Intent(context, ListenService::class.java).apply {
            putExtra("name", "Nursery")
            putExtra("address", "127.0.0.1")
            putExtra("port", 10000)
            putExtra("pairingCode", "test")
        }
        val controller = Robolectric.buildService(ListenService::class.java, intent)
        controller.create()
        val service = controller.get()

        service.onError = { }
        service.onUpdate = { }
        service.onStatusChange = { }

        assertNotNull("onError should be set", service.onError)
        assertNotNull("onUpdate should be set", service.onUpdate)

        val binding = ServiceConnectionManager.ServiceBinding(
            intent = intent,
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName, s: android.os.IBinder) {}
                override fun onServiceDisconnected(name: android.content.ComponentName) {}
            },
            bound = false,
            clearCallbacks = { service.clearCallbacks() }
        )

        ServiceConnectionManager.unbindAndStopService(context, binding)

        assertNull("onError should be null after unbind", service.onError)
        assertNull("onUpdate should be null after unbind", service.onUpdate)
        assertNull("onStatusChange should be null after unbind", service.onStatusChange)

        controller.destroy()
    }
}