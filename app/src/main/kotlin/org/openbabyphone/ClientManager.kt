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

import android.util.Log
import java.net.Socket

class ClientManager(
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        private const val TAG = "ClientManager"
        const val MAX_CLIENTS = 5
    }

    private val clients = mutableListOf<Client>()
    private var nextClientId = 0
    private var clientCountListener: ((Int) -> Unit)? = null

    fun setClientCountListener(listener: ((Int) -> Unit)?) {
        clientCountListener = listener
    }

    @Synchronized
    fun addClient(socket: Socket, pairingCode: String): Client? {
        if (clients.size >= MAX_CLIENTS) {
            Log.w(TAG, "Cannot add client - max clients ($MAX_CLIENTS) reached")
            return null
        }
        val client = Client(socket, nextClientId++, pairingCode, clock)
        clients.add(client)
        client.startSending()
        Log.i(TAG, "Client ${client.id} added, total: ${clients.size}/$MAX_CLIENTS")
        clientCountListener?.invoke(clients.size)
        return client
    }

    @Synchronized
    fun removeClient(client: Client) {
        if (clients.remove(client)) {
            client.stop()
            Log.i(TAG, "Client ${client.id} removed, total: ${clients.size}/$MAX_CLIENTS")
            clientCountListener?.invoke(clients.size)
        }
    }

    @Synchronized
    fun broadcastFrame(frame: ByteArray) {
        val iterator = clients.iterator()
        while (iterator.hasNext()) {
            val client = iterator.next()
            if (!client.queueFrame(frame)) {
                if (client.shouldDisconnect()) {
                    Log.w(TAG, "Disconnecting client ${client.id} - too many dropped frames")
                    iterator.remove()
                    client.stop()
                    Log.i(TAG, "Client ${client.id} removed, total: ${clients.size}/$MAX_CLIENTS")
                    clientCountListener?.invoke(clients.size)
                }
            }
        }
    }

    @Synchronized
    fun getClientCount(): Int = clients.size

    @Synchronized
    fun canAcceptMoreClients(): Boolean = clients.size < MAX_CLIENTS

    @Synchronized
    fun removeAllClients() {
        clients.forEach { it.stop() }
        clients.clear()
        Log.i(TAG, "All clients removed")
        clientCountListener?.invoke(0)
    }
}
