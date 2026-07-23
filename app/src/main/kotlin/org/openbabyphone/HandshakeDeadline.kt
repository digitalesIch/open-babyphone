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

import java.io.FilterInputStream
import java.io.InputStream
import java.net.SocketTimeoutException

internal class HandshakeDeadline(
    timeoutMs: Long,
    private val elapsedRealtime: () -> Long
) {
    private val deadlineMs = elapsedRealtime() + timeoutMs

    init {
        require(timeoutMs > 0)
    }

    fun check() {
        if (remainingMillis() <= 0) throw SocketTimeoutException("Handshake deadline exceeded")
    }

    fun input(input: InputStream, setReadTimeout: (Int) -> Unit): InputStream =
        object : FilterInputStream(input) {
            override fun read(): Int {
                applyTimeout(setReadTimeout)
                return super.read()
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                applyTimeout(setReadTimeout)
                return super.read(buffer, offset, length)
            }
        }

    private fun applyTimeout(setReadTimeout: (Int) -> Unit) {
        val remaining = remainingMillis()
        if (remaining <= 0) throw SocketTimeoutException("Handshake deadline exceeded")
        setReadTimeout(remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1))
    }

    private fun remainingMillis(): Long = deadlineMs - elapsedRealtime()
}
