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
package de.rochefort.childmonitor

import android.util.Log
import java.io.IOException
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue

class Client(
    val socket: Socket,
    val id: Int,
    val pairingCode: String
) {
    companion object {
        private const val TAG = "Client"
        const val QUEUE_CAPACITY = 100
        const val MAX_DROPPED_FRAMES = 50
    }

    private val frameQueue = ArrayBlockingQueue<ByteArray>(QUEUE_CAPACITY)
    private var droppedFrames = 0
    private var sendThread: Thread? = null
    @Volatile private var isRunning = false

    fun startSending() {
        isRunning = true
        sendThread = Thread {
            try {
                val out = socket.getOutputStream()
                socket.soTimeout = 0
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    val frame = frameQueue.take()
                    try {
                        out.write(frame)
                        out.flush()
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to send frame to client $id", e)
                        droppedFrames += MAX_DROPPED_FRAMES
                        break
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Send thread interrupted for client $id")
            } finally {
                isRunning = false
            }
        }
        sendThread?.start()
    }

    fun queueFrame(frame: ByteArray): Boolean {
        if (!isRunning) {
            return false
        }
        val success = frameQueue.offer(frame)
        if (!success) {
            droppedFrames++
            Log.w(TAG, "Queue full for client $id, dropped frame $droppedFrames/$MAX_DROPPED_FRAMES")
        }
        return success
    }

    fun shouldDisconnect(): Boolean {
        return droppedFrames >= MAX_DROPPED_FRAMES
    }

    fun getDroppedFrameCount(): Int = droppedFrames

    fun stop() {
        isRunning = false
        sendThread?.interrupt()
        try {
            socket.close()
        } catch (e: IOException) {
            Log.d(TAG, "Failed to close socket for client $id", e)
        }
    }
}
