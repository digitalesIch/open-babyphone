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

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration-style tests for multi-parent lifecycle scenarios.
 *
 * These tests verify the ClientManager behavior for connect, disconnect,
 * reconnect, max-capacity, capacity recovery, and slow-client eviction
 * across multiple parents. They complement the unit tests in ClientManagerTest
 * by exercising multi-client sequences end-to-end through the ClientManager API.
 *
 * Full instrumentation tests requiring MonitorService, real sockets, and
 * NSD re-registration are documented in docs/testing.md (Multi-Parent section).
 */
class MultiParentIntegrationTest {

    private fun mockSocketWithBlockingOutput(): Socket {
        val socket = mock(Socket::class.java)
        `when`(socket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) = Unit
        })
        return socket
    }

    @Test
    fun twoParentsConnect_BothReceiveAudio() {
        val manager = ClientManager()
        val writeCounts = mutableListOf<AtomicInteger>()

        for (i in 0 until 2) {
            val socket = mock(Socket::class.java)
            val writeCount = AtomicInteger(0)
            `when`(socket.getOutputStream()).thenReturn(object : OutputStream() {
                override fun write(b: Int) = Unit
                override fun write(b: ByteArray, off: Int, len: Int) {
                    writeCount.incrementAndGet()
                }
            })
            writeCounts.add(writeCount)
            val client = manager.addClient(socket, "test")
            assertNotNull(client)
        }

        assertEquals(2, manager.getClientCount())

        for (i in 0 until 5) {
            manager.broadcastFrame(ByteArray(100))
        }

        val deadline = System.currentTimeMillis() + 2_000
        while (writeCounts.any { it.get() == 0 } && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }

        for (writeCount in writeCounts) {
            assertTrue("Each parent should receive frames", writeCount.get() > 0)
        }
    }

    @Test
    fun oneParentDisconnects_OtherParentContinues() {
        val manager = ClientManager()
        val socket1 = mockSocketWithBlockingOutput()
        val socket2 = mockSocketWithBlockingOutput()

        val client1 = manager.addClient(socket1, "test")
        val client2 = manager.addClient(socket2, "test")
        assertNotNull(client1)
        assertNotNull(client2)
        assertEquals(2, manager.getClientCount())

        manager.removeClient(client1!!)
        verify(socket1).close()
        assertEquals(1, manager.getClientCount())

        manager.broadcastFrame(ByteArray(100))

        val deadline = System.currentTimeMillis() + 1_000
        while (manager.getClientCount() == 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }

        assertEquals(1, manager.getClientCount())

        client2!!.stop()
    }

    @Test
    fun parentReconnects_AfterDisconnect() {
        val manager = ClientManager()
        val socket1 = mockSocketWithBlockingOutput()

        val client1 = manager.addClient(socket1, "test")
        assertNotNull(client1)
        assertEquals(1, manager.getClientCount())

        manager.removeClient(client1!!)
        assertEquals(0, manager.getClientCount())

        val socket2 = mockSocketWithBlockingOutput()
        val client2 = manager.addClient(socket2, "test")
        assertNotNull(client2)
        assertEquals(1, manager.getClientCount())

        client2!!.stop()
    }

    @Test
    fun fiveParentsConnect_MaxReached_SixthRejected() {
        val manager = ClientManager()
        val clients = mutableListOf<Client>()

        for (i in 0 until ClientManager.MAX_CLIENTS) {
            val socket = mockSocketWithBlockingOutput()
            val client = manager.addClient(socket, "test")
            assertNotNull(client)
            clients.add(client!!)
        }

        assertEquals(ClientManager.MAX_CLIENTS, manager.getClientCount())
        assertFalse(manager.canAcceptMoreClients())

        val extraSocket = mock(Socket::class.java)
        val rejectedClient = manager.addClient(extraSocket, "test")
        assertNull(rejectedClient)
        assertEquals(ClientManager.MAX_CLIENTS, manager.getClientCount())

        clients.forEach { it.stop() }
    }

    @Test
    fun parentDisconnectsFromMax_NewParentCanConnect() {
        val manager = ClientManager()
        val clients = mutableListOf<Client>()
        val sockets = mutableListOf<Socket>()

        for (i in 0 until ClientManager.MAX_CLIENTS) {
            val socket = mockSocketWithBlockingOutput()
            sockets.add(socket)
            val client = manager.addClient(socket, "test")
            clients.add(client!!)
        }

        assertFalse(manager.canAcceptMoreClients())

        manager.removeClient(clients.last())
        verify(sockets.last()).close()
        assertTrue(manager.canAcceptMoreClients())

        val newSocket = mockSocketWithBlockingOutput()
        val newClient = manager.addClient(newSocket, "test")
        assertNotNull(newClient)
        assertEquals(ClientManager.MAX_CLIENTS, manager.getClientCount())

        newClient!!.stop()
        for (i in 0 until clients.size - 1) {
            clients[i].stop()
        }
    }

    @Test
    fun slowParentDropped_OtherParentsContinue() {
        val manager = ClientManager()

        val fastSocket = mockSocketWithBlockingOutput()
        val fastClient = manager.addClient(fastSocket, "test")
        assertNotNull(fastClient)

        val slowWriteStarted = CountDownLatch(1)
        val slowWriteBlock = CountDownLatch(1)
        val slowSocket = mock(Socket::class.java)
        `when`(slowSocket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) {
                slowWriteStarted.countDown()
                try {
                    slowWriteBlock.await()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.IOException(e)
                }
            }
        })
        val slowClient = manager.addClient(slowSocket, "test")
        assertNotNull(slowClient)
        assertEquals(2, manager.getClientCount())

        manager.broadcastFrame(ByteArray(100))
        assertTrue("Slow parent send thread should start",
            slowWriteStarted.await(5, TimeUnit.SECONDS))

        for (i in 0 until Client.QUEUE_CAPACITY + Client.MAX_DROPPED_FRAMES + 10) {
            manager.broadcastFrame(ByteArray(100))
            if (i % 10 == 0) Thread.sleep(2)
        }

        assertEquals(1, manager.getClientCount())
        assertTrue(slowClient!!.getDroppedFrameCount() >= Client.MAX_DROPPED_FRAMES)

        val fastWriteCount = AtomicInteger(0)
        val fastSocket2 = mock(Socket::class.java)
        `when`(fastSocket2.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) {
                fastWriteCount.incrementAndGet()
            }
        })
        val fastClient2 = manager.addClient(fastSocket2, "test")
        assertNotNull(fastClient2)
        assertEquals(2, manager.getClientCount())

        manager.broadcastFrame(ByteArray(100))
        val deadline = System.currentTimeMillis() + 1_000
        while (fastWriteCount.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("Fast parent still receives frames after slow parent dropped",
            fastWriteCount.get() > 0)

        fastClient!!.stop()
        fastClient2!!.stop()
    }

    @Test
    fun clientCountListener_FiresForAllLifecycleEvents() {
        val manager = ClientManager()
        val counts = mutableListOf<Int>()
        manager.setClientCountListener { counts.add(it) }

        val sockets = mutableListOf<Socket>()
        val clients = mutableListOf<Client>()

        for (i in 0 until 3) {
            val socket = mockSocketWithBlockingOutput()
            sockets.add(socket)
            val client = manager.addClient(socket, "test")
            clients.add(client!!)
        }

        manager.removeClient(clients[0])

        manager.removeAllClients()

        assertEquals(listOf(1, 2, 3, 2, 0), counts)
    }

    @Test
    fun broadcastFrame_AllParentsReceiveSameFrame() {
        val manager = ClientManager()
        val receivedFrames = mutableListOf<ByteArray>()
        val latches = mutableListOf<CountDownLatch>()

        for (i in 0 until 3) {
            val latch = CountDownLatch(1)
            latches.add(latch)
            val socket = mock(Socket::class.java)
            `when`(socket.getOutputStream()).thenReturn(object : OutputStream() {
                override fun write(b: Int) = Unit
                override fun write(b: ByteArray, off: Int, len: Int) {
                    receivedFrames.add(b.copyOfRange(off, off + len))
                    latch.countDown()
                }
            })
            val client = manager.addClient(socket, "test")
            assertNotNull(client)
        }

        val testFrame = ByteArray(50) { 0x42.toByte() }
        manager.broadcastFrame(testFrame)

        for (latch in latches) {
            assertTrue("Each parent should receive the frame", latch.await(2, TimeUnit.SECONDS))
        }

        for (frame in receivedFrames) {
            assertArrayEquals(testFrame, frame)
        }

        assertEquals(3, manager.getClientCount())
    }

    @Test
    fun disconnectAllThenReconnect_WorksCorrectly() {
        val manager = ClientManager()

        for (i in 0 until 3) {
            val socket = mockSocketWithBlockingOutput()
            manager.addClient(socket, "test")
        }
        assertEquals(3, manager.getClientCount())

        manager.removeAllClients()
        assertEquals(0, manager.getClientCount())

        for (i in 0 until 3) {
            val socket = mockSocketWithBlockingOutput()
            val client = manager.addClient(socket, "test")
            assertNotNull(client)
        }
        assertEquals(3, manager.getClientCount())
    }

    @Test
    fun incrementalConnectAndDisconnect_MaintainsCorrectCount() {
        val manager = ClientManager()
        val clients = mutableListOf<Client>()

        for (i in 0 until 5) {
            val socket = mockSocketWithBlockingOutput()
            val client = manager.addClient(socket, "test")
            clients.add(client!!)
            assertEquals(i + 1, manager.getClientCount())
        }

        for (i in 4 downTo 0) {
            manager.removeClient(clients[i])
            assertEquals(i, manager.getClientCount())
        }
    }
}