package org.openbabyphone

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class MonitorServiceHandshakeTest {

    @Test
    fun `open handshake omits kdf salt even when service has persisted salt`() {
        val controller = Robolectric.buildService(MonitorService::class.java)
        val service = controller.create().get()
        val sessionId = ByteArray(CryptoHelper.SESSION_ID_SIZE) { 0x42 }
        val persistedSalt = ByteArray(CryptoHelper.SALT_SIZE) { 0x77 }

        setPrivateField(service, "streamSessionId", sessionId)
        setPrivateField(service, "streamKey", null)
        setPrivateField(service, "streamKdfSalt", persistedSalt)

        ServerSocket(0).use { serverSocket ->
            val parentReadHandshake = CountDownLatch(1)
            val parentError = AtomicReference<Throwable?>()
            val parentThread = Thread {
                try {
                    Socket("127.0.0.1", serverSocket.localPort).use { socket ->
                        val handshake = Handshake.readHandshake(socket.getInputStream())
                            ?: throw AssertionError("Child did not send handshake")

                        assertArrayEquals(sessionId, handshake.sessionId)
                        assertFalse(handshake.authRequired)
                        assertNull(handshake.kdfSalt)

                        Handshake.writeCapabilityResponse(socket.getOutputStream())
                        parentReadHandshake.countDown()
                    }
                } catch (throwable: Throwable) {
                    parentError.set(throwable)
                }
            }

            parentThread.start()
            val childSocket = serverSocket.accept()
            val authenticated = invokeAuthenticateParent(service, childSocket)
            childSocket.close()
            parentThread.join(1000)

            assertTrue(authenticated)
            assertTrue(parentReadHandshake.await(1, TimeUnit.SECONDS))
            parentError.get()?.let { throw it }
        }

        controller.destroy()
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private fun invokeAuthenticateParent(service: MonitorService, socket: Socket): Boolean {
        return service.javaClass.getDeclaredMethod("authenticateParent", Socket::class.java).run {
            isAccessible = true
            invoke(service, socket) as Boolean
        }
    }
}
