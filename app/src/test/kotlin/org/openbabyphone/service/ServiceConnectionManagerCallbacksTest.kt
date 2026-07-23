package org.openbabyphone.service

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import org.openbabyphone.ListenService
import org.openbabyphone.MonitorService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

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

    @Test
    fun `disposeServiceBinding does not stop an active listen service`() {
        val intent = Intent(context, ListenService::class.java)
        val binding = ServiceConnectionManager.ServiceBinding(
            intent = intent,
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName, service: android.os.IBinder) {}
                override fun onServiceDisconnected(name: android.content.ComponentName) {}
            },
            bound = false
        )

        ServiceConnectionManager.disposeServiceBinding(context, binding)

        val stoppedService = shadowOf(context).nextStoppedService
        assertNull("Composition disposal must not stop an active listen service", stoppedService)
    }

    @Test
    fun `disposeServiceBinding does not stop an active monitor service`() {
        val intent = Intent(context, MonitorService::class.java)
        val binding = ServiceConnectionManager.ServiceBinding(
            intent = intent,
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName, service: android.os.IBinder) {}
                override fun onServiceDisconnected(name: android.content.ComponentName) {}
            },
            bound = false
        )

        ServiceConnectionManager.disposeServiceBinding(context, binding)

        assertNull("Composition disposal must not stop an active monitor service", shadowOf(context).nextStoppedService)
    }

    @Test
    fun `stopMonitorService explicitly stops monitor service`() {
        ServiceConnectionManager.stopMonitorService(context)

        assertEquals(
            MonitorService::class.java.name,
            shadowOf(context).nextStoppedService.component?.className
        )
    }

    @Test
    fun `disposeServiceBinding only unbinds resume listen binding`() {
        val intent = Intent(context, ListenService::class.java)
        val binding = ServiceConnectionManager.ServiceBinding(
            intent = intent,
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName, service: android.os.IBinder) {}
                override fun onServiceDisconnected(name: android.content.ComponentName) {}
            },
            bound = false
        )

        ServiceConnectionManager.disposeServiceBinding(context, binding)

        assertNull("Resume listen binding must not stop service on dispose", shadowOf(context).nextStoppedService)
    }

    @Test
    fun `monitor foreground start runtime failure publishes retryable startup error`() {
        MonitorServiceRepository.reset()
        val failingContext = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? {
                throw IllegalStateException("Foreground start denied")
            }
        }

        assertFalse(ServiceConnectionManager.startMonitorService(failingContext))
        val state = MonitorServiceRepository.sessionState.value
        assertTrue(state is MonitorSessionState.Error)
        assertEquals(MonitorSessionError.Startup, (state as MonitorSessionState.Error).type)
    }

    @Test
    fun `monitor bind runtime failure publishes startup error and remains unbound`() {
        MonitorServiceRepository.reset()
        val failingContext = object : ContextWrapper(context) {
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
                throw IllegalStateException("Bind denied")
            }

            override fun stopService(name: Intent): Boolean = true
        }

        val binding = ServiceConnectionManager.bindMonitorService(failingContext)

        assertFalse(binding.bound)
        val state = MonitorServiceRepository.sessionState.value
        assertTrue(state is MonitorSessionState.Error)
        assertEquals(MonitorSessionError.Startup, (state as MonitorSessionState.Error).type)
    }

    @Test
    fun `monitor attachment never auto creates the service`() {
        var bindFlags = -1
        val recordingContext = object : ContextWrapper(context) {
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
                bindFlags = flags
                return true
            }
        }

        val binding = ServiceConnectionManager.bindMonitorService(recordingContext)

        assertTrue(binding.bound)
        assertEquals(0, bindFlags)
    }
}
