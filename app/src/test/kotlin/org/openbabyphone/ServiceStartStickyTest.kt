package org.openbabyphone

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ServiceStartStickyTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
    }

    @Test
    fun `ListenService returns START_REDELIVER_INTENT`() {
        val intent = Intent(context, ListenService::class.java).apply {
            putExtra("name", "Nursery")
            putExtra("address", "127.0.0.1")
            putExtra("port", 10000)
            putExtra("pairingCode", "test")
        }
        val controller = Robolectric.buildService(ListenService::class.java, intent)
        controller.create()
        val service = controller.get()
        val result = service.onStartCommand(intent, 0, 0)
        assertEquals(
            "ListenService should return START_REDELIVER_INTENT for restart after low-memory kill",
            android.app.Service.START_REDELIVER_INTENT,
            result
        )
        controller.destroy()
    }
}