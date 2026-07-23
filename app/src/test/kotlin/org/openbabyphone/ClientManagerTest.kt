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

import org.openbabyphone.ClientManager
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.io.IOException
import java.io.OutputStream
import java.net.Socket

class ClientManagerTest {

    @Test
    fun addClient_MaxReached_ReturnsNull() {
        val manager = ClientManager()
        val sockets = mutableListOf<Socket>()
        
        for (i in 0 until ClientManager.MAX_CLIENTS) {
            val socket = mock(Socket::class.java)
            sockets.add(socket)
            val client = manager.addClient(socket, "test")
            assertNotNull(client)
        }
        
        val extraSocket = mock(Socket::class.java)
        val extraClient = manager.addClient(extraSocket, "test")
        
        assertNull(extraClient)
    }

    @Test
    fun addClient_BelowMax_ReturnsClient() {
        val manager = ClientManager()
        val socket = mock(Socket::class.java)
        
        val client = manager.addClient(socket, "test")
        
        assertNotNull(client)
        assertEquals(0, client!!.id)
    }

    @Test
    fun removeClient_StopsClient() {
        val manager = ClientManager()
        val socket = mock(Socket::class.java)
        val client = manager.addClient(socket, "test")
        
        manager.removeClient(client!!)
        
        verify(socket, times(1)).close()
    }

    @Test
    fun broadcastFrame_QueueFull_Dropped() {
        val manager = ClientManager()
        val socket = mock(Socket::class.java)
        `when`(socket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit

            override fun write(b: ByteArray, off: Int, len: Int) {
                try {
                    Thread.sleep(10_000)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException(e)
                }
            }
        })
        
        val client = manager.addClient(socket, "test")
        assertNotNull(client)
        
        for (i in 0 until Client.QUEUE_CAPACITY + 10) {
            val frame = ByteArray(100)
            manager.broadcastFrame(frame)
        }
        
        client!!.stop()
        assertTrue(client.getDroppedFrameCount() > 0)
        val deadline = System.currentTimeMillis() + 2_000
        while (client.getAvailableFrameSlotCount() != Client.FRAME_SLOT_COUNT &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
        }
        assertEquals(Client.FRAME_SLOT_COUNT, client.getAvailableFrameSlotCount())
        assertEquals(0, client.getQueuedFrameCount())
    }

    @Test
    fun successfulWrites_ResetConsecutiveDroppedFramesAfterShortBurst() {
        val manager = ClientManager()
        val writeStarted = java.util.concurrent.CountDownLatch(1)
        val writeBlock = java.util.concurrent.CountDownLatch(1)
        val socket = mock(Socket::class.java)
        `when`(socket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit

            override fun write(b: ByteArray, off: Int, len: Int) {
                writeStarted.countDown()
                try {
                    writeBlock.await()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException(e)
                }
            }
        })

        val client = manager.addClient(socket, "test")
        assertNotNull(client)

        manager.broadcastFrame(ByteArray(100))
        assertTrue(
            "Client send thread should start blocking",
            writeStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)
        )

        repeat(Client.QUEUE_CAPACITY + 10) {
            manager.broadcastFrame(ByteArray(100))
        }

        assertTrue(client!!.getDroppedFrameCount() > 0)
        assertFalse("Short transient burst should stay below disconnect threshold", client.shouldDisconnect())

        writeBlock.countDown()
        val deadline = System.currentTimeMillis() + 2_000
        while (client.getConsecutiveDroppedFrameCount() != 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }

        assertEquals(0, client.getConsecutiveDroppedFrameCount())
        assertFalse(client.shouldDisconnect())
        client.stop()
    }

    @Test
    fun getClientCount_CorrectCount() {
        val manager = ClientManager()
        val sockets = mutableListOf<Socket>()
        
        for (i in 0 until 3) {
            val socket = mock(Socket::class.java)
            sockets.add(socket)
            manager.addClient(socket, "test")
        }
        
        assertEquals(3, manager.getClientCount())
    }

    @Test
    fun canAcceptMoreClients_BelowMax_ReturnsTrue() {
        val manager = ClientManager()
        
        for (i in 0 until ClientManager.MAX_CLIENTS - 1) {
            val socket = mock(Socket::class.java)
            manager.addClient(socket, "test")
        }
        
        assertTrue(manager.canAcceptMoreClients())
    }

    @Test
    fun canAcceptMoreClients_AtMax_ReturnsFalse() {
        val manager = ClientManager()
        
        for (i in 0 until ClientManager.MAX_CLIENTS) {
            val socket = mock(Socket::class.java)
            manager.addClient(socket, "test")
        }
        
        assertFalse(manager.canAcceptMoreClients())
    }

    @Test
    fun removeAllClients_StopsAllClients() {
        val manager = ClientManager()
        val sockets = mutableListOf<Socket>()
        
        for (i in 0 until 3) {
            val socket = mock(Socket::class.java)
            sockets.add(socket)
            manager.addClient(socket, "test")
        }
        
        manager.removeAllClients()
        
        for (socket in sockets) {
            verify(socket, times(1)).close()
        }
        
        assertEquals(0, manager.getClientCount())
    }

    @Test
    fun disconnectFromMax_ThenCanAcceptAgain() {
        val manager = ClientManager()
        val sockets = mutableListOf<Socket>()
        val clients = mutableListOf<Client>()

        for (i in 0 until ClientManager.MAX_CLIENTS) {
            val socket = mock(Socket::class.java)
            sockets.add(socket)
            val client = manager.addClient(socket, "test")
            clients.add(client!!)
        }

        assertFalse(manager.canAcceptMoreClients())

        manager.removeClient(clients.last())
        verify(sockets.last(), times(1)).close()

        assertTrue(manager.canAcceptMoreClients())
        assertEquals(ClientManager.MAX_CLIENTS - 1, manager.getClientCount())

        val newSocket = mock(Socket::class.java)
        val newClient = manager.addClient(newSocket, "test")
        assertNotNull(newClient)
        assertEquals(ClientManager.MAX_CLIENTS, manager.getClientCount())
    }

    @Test
    fun slowClient_IsDroppedAndOtherClientsContinue() {
        var fakeNow = 0L
        val manager = ClientManager { fakeNow }

        val fastSocket = mock(Socket::class.java)
        `when`(fastSocket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) = Unit
        })
        val fastClient = manager.addClient(fastSocket, "test")
        assertNotNull(fastClient)

        val slowWriteStarted = java.util.concurrent.CountDownLatch(1)
        val slowWriteBlock = java.util.concurrent.CountDownLatch(1)
        val slowSocket = mock(Socket::class.java)
        `when`(slowSocket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) {
                slowWriteStarted.countDown()
                try {
                    slowWriteBlock.await()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException(e)
                }
            }
        })
        val slowClient = manager.addClient(slowSocket, "test")
        assertNotNull(slowClient)

        assertEquals(2, manager.getClientCount())

        manager.broadcastFrame(ByteArray(100))

        assertTrue("Slow client send thread should start blocking within timeout",
            slowWriteStarted.await(5, java.util.concurrent.TimeUnit.SECONDS))

        fakeNow = 1_000L
        for (i in 0 until Client.QUEUE_CAPACITY + Client.MAX_DROPPED_FRAMES + 10) {
            manager.broadcastFrame(ByteArray(100))
        }

        assertFalse("Client should not disconnect within grace period",
            manager.getClientCount() == 1)

        Thread.sleep(200)
        fakeNow = 1_000L + Client.SLOW_CLIENT_GRACE_MS + 1
        manager.broadcastFrame(ByteArray(100))

        assertEquals(1, manager.getClientCount())
        assertTrue(slowClient!!.getDroppedFrameCount() >= Client.MAX_DROPPED_FRAMES)

        slowWriteBlock.countDown()
        fastClient!!.stop()
    }

    @Test
    fun broadcastFrame_DeliversToAllClients() {
        val manager = ClientManager()
        val clients = mutableListOf<Client>()
        val writeCounts = mutableListOf<java.util.concurrent.atomic.AtomicInteger>()

        for (i in 0 until 3) {
            val socket = mock(Socket::class.java)
            val writeCount = java.util.concurrent.atomic.AtomicInteger(0)
            `when`(socket.getOutputStream()).thenReturn(object : OutputStream() {
                override fun write(b: Int) = Unit
                override fun write(b: ByteArray, off: Int, len: Int) {
                    writeCount.incrementAndGet()
                }
            })
            writeCounts.add(writeCount)
            val client = manager.addClient(socket, "test")
            clients.add(client!!)
        }

        manager.broadcastFrame(ByteArray(50))

        val deadline = System.currentTimeMillis() + 1_000
        while (writeCounts.any { it.get() == 0 } && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }

        assertEquals(3, manager.getClientCount())
        for (writeCount in writeCounts) {
            assertTrue(writeCount.get() > 0)
        }

        for (client in clients) {
            client.stop()
        }
    }

    @Test
    fun removeClient_DecreasesCount() {
        val manager = ClientManager()
        val socket1 = mock(Socket::class.java)
        val socket2 = mock(Socket::class.java)

        val client1 = manager.addClient(socket1, "test")
        manager.addClient(socket2, "test")
        assertEquals(2, manager.getClientCount())

        manager.removeClient(client1!!)
        assertEquals(1, manager.getClientCount())
    }

    @Test
    fun addClient_AssignsIncrementingIds() {
        val manager = ClientManager()

        for (i in 0 until 3) {
            val socket = mock(Socket::class.java)
            val client = manager.addClient(socket, "test")
            assertEquals(i, client!!.id)
        }
    }

    @Test
    fun removeAllClients_AllowsNewClientsAfterClear() {
        val manager = ClientManager()

        for (i in 0 until ClientManager.MAX_CLIENTS) {
            val socket = mock(Socket::class.java)
            manager.addClient(socket, "test")
        }
        assertFalse(manager.canAcceptMoreClients())

        manager.removeAllClients()
        assertTrue(manager.canAcceptMoreClients())
        assertEquals(0, manager.getClientCount())

        val newSocket = mock(Socket::class.java)
        val newClient = manager.addClient(newSocket, "test")
        assertNotNull(newClient)
    }

    @Test
    fun clientCountListener_NotifiedOnAdd() {
        val manager = ClientManager()
        val counts = mutableListOf<Int>()
        manager.setClientCountListener { counts.add(it) }

        val socket = mock(Socket::class.java)
        manager.addClient(socket, "test")

        assertEquals(listOf(1), counts)
    }

    @Test
    fun clientCountListener_NotifiedOnRemove() {
        val manager = ClientManager()
        val counts = mutableListOf<Int>()
        val socket = mock(Socket::class.java)
        val client = manager.addClient(socket, "test")

        manager.setClientCountListener { counts.add(it) }
        manager.removeClient(client!!)

        assertEquals(listOf(0), counts)
    }

    @Test
    fun clientCountListener_NotifiedOnRemoveAll() {
        val manager = ClientManager()
        val counts = mutableListOf<Int>()
        for (i in 0 until 3) {
            val socket = mock(Socket::class.java)
            manager.addClient(socket, "test")
        }

        manager.setClientCountListener { counts.add(it) }
        manager.removeAllClients()

        assertEquals(listOf(0), counts)
    }

    @Test
    fun setClientCountListener_NullRemovesListener() {
        val manager = ClientManager()
        val counts = mutableListOf<Int>()
        val socket = mock(Socket::class.java)

        manager.setClientCountListener { counts.add(it) }
        manager.addClient(socket, "test")
        assertEquals(1, counts.size)

        manager.setClientCountListener(null)
        val socket2 = mock(Socket::class.java)
        manager.addClient(socket2, "test")
        assertEquals(1, counts.size)
    }

    @Test
    fun clientCountListener_RunsOutsideManagerMonitor() {
        val manager = ClientManager()
        val callbackCompleted = java.util.concurrent.CountDownLatch(1)
        manager.setClientCountListener {
            val observer = Thread {
                manager.getClientCount()
                callbackCompleted.countDown()
            }
            observer.start()
            assertTrue(
                "Listener held the ClientManager monitor",
                callbackCompleted.await(1, java.util.concurrent.TimeUnit.SECONDS)
            )
            observer.join()
        }

        manager.addClient(mock(Socket::class.java), "test")
    }

    @Test
    fun shortTransientBurst_WithinGracePeriod_DoesNotDisconnect() {
        var fakeNow = 0L
        val manager = ClientManager { fakeNow }

        val slowWriteStarted = java.util.concurrent.CountDownLatch(1)
        val slowWriteBlock = java.util.concurrent.CountDownLatch(1)
        val slowSocket = mock(Socket::class.java)
        `when`(slowSocket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) {
                slowWriteStarted.countDown()
                try {
                    slowWriteBlock.await()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException(e)
                }
            }
        })
        val slowClient = manager.addClient(slowSocket, "test")
        assertNotNull(slowClient)

        manager.broadcastFrame(ByteArray(100))
        assertTrue(slowWriteStarted.await(5, java.util.concurrent.TimeUnit.SECONDS))

        fakeNow = 1_000L
        repeat(Client.QUEUE_CAPACITY + Client.MAX_DROPPED_FRAMES + 10) {
            manager.broadcastFrame(ByteArray(100))
        }

        assertTrue(slowClient!!.getDroppedFrameCount() >= Client.MAX_DROPPED_FRAMES)
        assertFalse("Client should not disconnect within grace period", slowClient.shouldDisconnect())
        assertEquals(1, manager.getClientCount())

        slowWriteBlock.countDown()
        slowClient.stop()
    }

    @Test
    fun sustainedSlowClient_PastGracePeriod_Disconnects() {
        var fakeNow = 0L
        val manager = ClientManager { fakeNow }

        val slowWriteStarted = java.util.concurrent.CountDownLatch(1)
        val slowWriteBlock = java.util.concurrent.CountDownLatch(1)
        val slowSocket = mock(Socket::class.java)
        `when`(slowSocket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) {
                slowWriteStarted.countDown()
                try {
                    slowWriteBlock.await()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException(e)
                }
            }
        })
        val slowClient = manager.addClient(slowSocket, "test")
        assertNotNull(slowClient)

        manager.broadcastFrame(ByteArray(100))
        assertTrue(slowWriteStarted.await(5, java.util.concurrent.TimeUnit.SECONDS))

        fakeNow = 1_000L
        repeat(Client.QUEUE_CAPACITY + Client.MAX_DROPPED_FRAMES + 10) {
            manager.broadcastFrame(ByteArray(100))
        }
        assertFalse(slowClient!!.shouldDisconnect())

        fakeNow = 1_000L + Client.SLOW_CLIENT_GRACE_MS + 1
        manager.broadcastFrame(ByteArray(100))

        assertEquals(0, manager.getClientCount())
        assertTrue(slowClient.getDroppedFrameCount() >= Client.MAX_DROPPED_FRAMES)

        slowWriteBlock.countDown()
    }

    @Test
    fun queueFrame_CopiesRequestedRangeAndRecyclesPreallocatedSlot() {
        val manager = ClientManager()
        val received = java.util.concurrent.atomic.AtomicReference<ByteArray>()
        val written = java.util.concurrent.CountDownLatch(1)
        val socket = mock(Socket::class.java)
        `when`(socket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit

            override fun write(b: ByteArray, off: Int, len: Int) {
                received.set(b.copyOfRange(off, off + len))
                written.countDown()
            }
        })
        val client = manager.addClient(socket, "test")!!
        val slotsAtInitialization = client.getAllocatedFrameSlotCount()
        val producerBuffer = byteArrayOf(99, 1, 2, 3, 88)

        manager.broadcastFrame(producerBuffer, 1, 3)
        producerBuffer.fill(0)

        assertTrue(written.await(2, java.util.concurrent.TimeUnit.SECONDS))
        assertArrayEquals(byteArrayOf(1, 2, 3), received.get())
        val deadline = System.currentTimeMillis() + 2_000
        while (client.getAvailableFrameSlotCount() != Client.FRAME_SLOT_COUNT &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
        }
        assertEquals(Client.FRAME_SLOT_COUNT, client.getAvailableFrameSlotCount())
        assertEquals(slotsAtInitialization, client.getAllocatedFrameSlotCount())
        client.stop()
    }

    @Test
    fun broadcastFrame_UsesIsolatedStorageForEveryClient() {
        val manager = ClientManager()
        val firstMutated = java.util.concurrent.CountDownLatch(1)
        val secondWritten = java.util.concurrent.CountDownLatch(1)
        val secondReceived = java.util.concurrent.atomic.AtomicReference<ByteArray>()

        val firstSocket = mock(Socket::class.java)
        `when`(firstSocket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit

            override fun write(b: ByteArray, off: Int, len: Int) {
                b[off] = 0x7f
                firstMutated.countDown()
            }
        })
        val secondSocket = mock(Socket::class.java)
        `when`(secondSocket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit

            override fun write(b: ByteArray, off: Int, len: Int) {
                assertTrue(firstMutated.await(2, java.util.concurrent.TimeUnit.SECONDS))
                secondReceived.set(b.copyOfRange(off, off + len))
                secondWritten.countDown()
            }
        })
        val firstClient = manager.addClient(firstSocket, "test")!!
        val secondClient = manager.addClient(secondSocket, "test")!!
        val producerBuffer = byteArrayOf(10, 20, 30)

        manager.broadcastFrame(producerBuffer)

        assertTrue(secondWritten.await(2, java.util.concurrent.TimeUnit.SECONDS))
        assertArrayEquals(byteArrayOf(10, 20, 30), secondReceived.get())
        assertArrayEquals(byteArrayOf(10, 20, 30), producerBuffer)
        firstClient.stop()
        secondClient.stop()
    }

    @Test
    fun partialFailedWrite_RecyclesInFlightSlot() {
        val manager = ClientManager()
        val writeAttempted = java.util.concurrent.CountDownLatch(1)
        val socket = mock(Socket::class.java)
        `when`(socket.getOutputStream()).thenReturn(object : OutputStream() {
            override fun write(b: Int) = Unit

            override fun write(b: ByteArray, off: Int, len: Int) {
                assertEquals(1, b[off].toInt())
                assertEquals(2, b[off + 1].toInt())
                writeAttempted.countDown()
                throw IOException("connection failed after a partial transport write")
            }
        })
        val client = manager.addClient(socket, "test")!!

        manager.broadcastFrame(byteArrayOf(1, 2, 3, 4))

        assertTrue(writeAttempted.await(2, java.util.concurrent.TimeUnit.SECONDS))
        val deadline = System.currentTimeMillis() + 2_000
        while (client.getAvailableFrameSlotCount() != Client.FRAME_SLOT_COUNT &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
        }
        assertEquals(Client.FRAME_SLOT_COUNT, client.getAvailableFrameSlotCount())
        assertEquals(0, client.getQueuedFrameCount())
        assertEquals(Client.FRAME_SLOT_COUNT, client.getAllocatedFrameSlotCount())
        client.stop()
    }
}
