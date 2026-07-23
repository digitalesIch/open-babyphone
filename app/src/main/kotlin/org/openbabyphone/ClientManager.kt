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

import android.os.SystemClock
import android.util.Log
import java.net.Socket

class ClientManager(
    private val clock: () -> Long = SystemClock::elapsedRealtime
) {
    companion object {
        private const val TAG = "ClientManager"
        const val MAX_CLIENTS = 5
        const val MAX_FRAME_BUFFER_BYTES = MAX_CLIENTS * Client.MAX_FRAME_BUFFER_BYTES
    }

    private val clients = mutableListOf<Client>()
    private val broadcastLock = Any()
    private val removedClients = arrayOfNulls<Client>(MAX_CLIENTS)
    private var nextClientId = 0
    @Volatile private var clientCountListener: ((Int) -> Unit)? = null

    fun setClientCountListener(listener: ((Int) -> Unit)?) {
        clientCountListener = listener
    }

    fun addClient(socket: Socket, pairingCode: String): Client? {
        val result = synchronized(this) {
            if (clients.size >= MAX_CLIENTS) {
                Log.w(TAG, "Cannot add client - max clients ($MAX_CLIENTS) reached")
                return@synchronized null
            }
            Client(socket, nextClientId++, pairingCode, clock).also { client ->
                clients.add(client)
                client.startSending()
                Log.i(TAG, "Client ${client.id} added, total: ${clients.size}/$MAX_CLIENTS")
            } to clients.size
        }
        result ?: return null
        clientCountListener?.invoke(result.second)
        return result.first
    }

    fun removeClient(client: Client) {
        val count = synchronized(this) {
            if (!clients.remove(client)) return
            clients.size
        }
        client.stop()
        Log.i(TAG, "Client ${client.id} removed, total: $count/$MAX_CLIENTS")
        clientCountListener?.invoke(count)
    }

    fun broadcastFrame(frame: ByteArray) {
        broadcastFrame(frame, 0, frame.size)
    }

    fun broadcastFrame(frame: ByteArray, offset: Int, length: Int) {
        var removedCount = 0
        var count = 0
        synchronized(broadcastLock) {
            synchronized(this) {
                var index = 0
                while (index < clients.size) {
                    val client = clients[index]
                    if (!client.queueFrame(frame, offset, length) && client.shouldDisconnect()) {
                        Log.w(TAG, "Disconnecting client ${client.id} - too many dropped frames")
                        clients.removeAt(index)
                        removedClients[removedCount++] = client
                    } else {
                        index++
                    }
                }
                count = clients.size
            }
            var index = 0
            while (index < removedCount) {
                removedClients[index]!!.stop()
                removedClients[index] = null
                index++
            }
        }
        if (removedCount > 0) {
            Log.i(TAG, "Removed $removedCount slow client(s), total: $count/$MAX_CLIENTS")
            clientCountListener?.invoke(count)
        }
    }

    @Synchronized
    fun getClientCount(): Int = clients.size

    @Synchronized
    fun canAcceptMoreClients(): Boolean = clients.size < MAX_CLIENTS

    fun removeAllClients() {
        val removed = synchronized(this) {
            clients.toList().also { clients.clear() }
        }
        removed.forEach { it.stop() }
        Log.i(TAG, "All clients removed")
        clientCountListener?.invoke(0)
    }
}
