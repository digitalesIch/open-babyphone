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
        
        val client = manager.addClient(socket, "test")
        assertNotNull(client)
        
        for (i in 0 until Client.QUEUE_CAPACITY + 10) {
            val frame = ByteArray(100)
            manager.broadcastFrame(frame)
        }
        
        client!!.stop()
        assertTrue(client.getDroppedFrameCount() > 0)
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
}
