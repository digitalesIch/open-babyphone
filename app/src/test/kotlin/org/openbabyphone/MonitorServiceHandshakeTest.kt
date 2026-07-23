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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class MonitorServiceHandshakeTest {
    private val pairingCode = "ABCDEF12"
    private val salt = ByteArray(CryptoHelper.SALT_SIZE) { 0x77 }
    private val sessionId = ByteArray(CryptoHelper.SESSION_ID_SIZE) { 0x42 }

    @Test
    fun `socket handshake requires mutual authentication before accepting parent`() {
        val controller = Robolectric.buildService(MonitorService::class.java)
        val service = controller.create().get()
        configure(service)
        val parentError = AtomicReference<Throwable?>()

        ServerSocket(0).use { server ->
            val parent = Thread {
                try {
                    Socket("127.0.0.1", server.localPort).use { socket ->
                        val hello = Handshake.readChildHello(socket.getInputStream())!!
                        val baseKey = CryptoHelper.deriveKey(pairingCode, hello.kdfSalt)
                        val authKey = CryptoHelper.deriveAuthKey(baseKey)
                        val response = Handshake.createParentResponse(hello, authKey)
                        Handshake.writeParentResponse(socket.getOutputStream(), response)
                        val ack = Handshake.readChildAck(socket.getInputStream())
                        assertNotNull(ack)
                        assertTrue(Handshake.verifyChildAck(hello, response, ack!!, authKey))
                        assertTrue(ack.firstSequence == 0)
                    }
                } catch (throwable: Throwable) {
                    parentError.set(throwable)
                }
            }
            parent.start()
            val childSocket = server.accept()
            assertTrue(invokeHandleClient(service, childSocket, currentClaim(service)))
            parent.join(2000)
            parentError.get()?.let { throw it }
        }
        controller.destroy()
    }

    @Test
    fun `socket handshake rejects wrong pairing code without child ack`() {
        val controller = Robolectric.buildService(MonitorService::class.java)
        val service = controller.create().get()
        configure(service)
        val childAccepted = AtomicReference<Boolean>()

        ServerSocket(0).use { server ->
            val child = Thread {
                server.accept().use { socket ->
                    childAccepted.set(invokeHandleClient(service, socket, currentClaim(service)))
                }
            }
            child.start()
            Socket("127.0.0.1", server.localPort).use { socket ->
                val hello = Handshake.readChildHello(socket.getInputStream())!!
                val wrongBaseKey = CryptoHelper.deriveKey("WRONG999", hello.kdfSalt)
                val wrongAuthKey = CryptoHelper.deriveAuthKey(wrongBaseKey)
                Handshake.writeParentResponse(
                    socket.getOutputStream(),
                    Handshake.createParentResponse(hello, wrongAuthKey)
                )
            }
            child.join(2000)
            assertFalse(childAccepted.get())
        }
        controller.destroy()
    }

    @Test
    fun `destroy closes an in-progress authenticating socket`() {
        val controller = Robolectric.buildService(MonitorService::class.java)
        val service = controller.create().get()
        val socket = mock(Socket::class.java)
        setPrivateField(service, "currentAuthenticatingSocket", socket)

        controller.destroy()

        verify(socket).close()
    }

    private fun configure(service: MonitorService) {
        setPrivateField(service, "pairingCodeSnapshot", pairingCode)
        setPrivateField(service, "streamSessionId", sessionId)
        setPrivateField(service, "streamKdfSalt", salt)
        setPrivateField(service, "streamBaseKey", CryptoHelper.deriveKey(pairingCode, salt))
        setPrivateField(service, "streamKey", ByteArray(CryptoHelper.KEY_SIZE) { 1 })
        setPrivateField(service, "connectionToken", Any())
        val generation = service.javaClass.getDeclaredField("workerGeneration").run {
            isAccessible = true
            get(service) as WorkerGeneration
        }
        setPrivateField(service, "activeWorkerClaim", generation.claim(1))
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private fun currentClaim(service: MonitorService): WorkerClaim =
        service.javaClass.getDeclaredField("activeWorkerClaim").run {
            isAccessible = true
            get(service) as WorkerClaim
        }

    private fun invokeHandleClient(
        service: MonitorService,
        socket: Socket,
        claim: WorkerClaim
    ): Boolean =
        service.javaClass.getDeclaredMethod(
            "handleClient",
            Socket::class.java,
            WorkerClaim::class.java
        ).run {
            isAccessible = true
            invoke(service, socket, claim) as Boolean
        }
}
