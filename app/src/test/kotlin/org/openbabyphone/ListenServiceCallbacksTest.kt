package org.openbabyphone

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListenServiceCallbacksTest {

    @Test
    fun `clearCallbacks nulls all service callbacks`() {
        val controller = Robolectric.buildService(ListenService::class.java)
        val service = controller.create().get()

        service.onError = { }
        service.onUpdate = { }
        service.onStatusChange = { }

        assertNotNull("onError should be set", service.onError)
        assertNotNull("onUpdate should be set", service.onUpdate)
        assertNotNull("onStatusChange should be set", service.onStatusChange)

        service.clearCallbacks()

        assertNull("onError should be null after clearCallbacks", service.onError)
        assertNull("onUpdate should be null after clearCallbacks", service.onUpdate)
        assertNull("onStatusChange should be null after clearCallbacks", service.onStatusChange)

        controller.destroy()
    }
}