package org.openbabyphone

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Presents a WSS/WebSocket connection as a java.net.Socket so the existing
 * authenticated handshake and encrypted audio framing can remain unchanged.
 *
 * The relay transports opaque binary WebSocket messages only. TLS terminates
 * on the phone, while the relay never sees the decrypted application stream.
 */
class WebSocketByteStreamSocket(
    private val sessionId: String,
    private val role: Role
) : Socket() {
    enum class Role { CHILD, PARENT }

    companion object {
        private const val TAG = "RelaySocket"
        private const val WEBSOCKET_VERSION = "13"
        private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val MAX_HTTP_HEADER_BYTES = 32 * 1024
        private const val MAX_FRAME_PAYLOAD = 64 * 1024
        private const val MAX_MESSAGE_BYTES = 512 * 1024
        private const val MAX_CONTROL_PAYLOAD = 125

        private val RANDOM = SecureRandom()
        private val TLS_FACTORY: SSLSocketFactory =
            SSLSocketFactory.getDefault() as SSLSocketFactory
    }

    private val opened = CountDownLatch(1)
    private val closed = CountDownLatch(1)
    private val lock = Object()
    private val frameWriteLock = Any()
    private val chunks = ArrayDeque<ByteArray>()
    private var chunkOffset = 0
    private var queuedBytes = 0
    private var failure: IOException? = null
    private var socketSoTimeoutMs = 0

    private var sslSocket: SSLSocket? = null
    private var socketInput: BufferedInputStream? = null
    private var socketOutput: OutputStream? = null
    private var readerThread: Thread? = null
    private val isClosed = AtomicBoolean(false)
    private val closeFrameSent = AtomicBoolean(false)

    private val input = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
            if (length == 0) return 0

            synchronized(lock) {
                while (queuedBytes == 0 && failure == null && !isClosed.get()) {
                    waitForData()
                }
                if (queuedBytes == 0) {
                    failure?.let { throw it }
                    return -1
                }

                var copied = 0
                while (copied < length && chunks.isNotEmpty()) {
                    val chunk = chunks.first()
                    val available = chunk.size - chunkOffset
                    val take = minOf(length - copied, available)
                    chunk.copyInto(buffer, offset + copied, chunkOffset, chunkOffset + take)
                    copied += take
                    chunkOffset += take
                    queuedBytes -= take
                    if (chunkOffset == chunk.size) {
                        chunks.removeFirst()
                        chunkOffset = 0
                    }
                }
                return copied
            }
        }

        override fun available(): Int = synchronized(lock) { queuedBytes }

        override fun close() {
            this@WebSocketByteStreamSocket.close()
        }
    }

    private val output = object : OutputStream() {
        override fun write(value: Int) {
            write(byteArrayOf(value.toByte()))
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
            if (length == 0) return

            synchronized(lock) {
                if (isClosed.get()) throw IOException("Relay socket is closed")
                if (socketOutput == null) throw IOException("Relay WebSocket is not open")
            }

            synchronized(frameWriteLock) {
                synchronized(lock) {
                    if (isClosed.get()) throw IOException("Relay socket is closed")
                }
                sendFrame(opcode = 0x2, payload = buffer, offset = offset, length = length, fin = true)
            }
        }
    }

    private fun waitForData() {
        val timeout = socketSoTimeoutMs
        if (timeout <= 0) {
            try {
                lock.wait()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while waiting for relay data", e)
            }
            return
        }

        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout.toLong())
        var remaining = deadline - System.nanoTime()
        while (remaining > 0L && queuedBytes == 0 && failure == null && !isClosed.get()) {
            try {
                val millis = TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1L)
                val nanos = (remaining - TimeUnit.MILLISECONDS.toNanos(millis))
                    .toInt()
                    .coerceIn(0, 999_999)
                lock.wait(millis, nanos)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while waiting for relay data", e)
            }
            remaining = deadline - System.nanoTime()
        }

        if (queuedBytes == 0 && failure == null && !isClosed.get()) {
            throw SocketTimeoutException("Relay socket read timed out")
        }
    }

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        synchronized(lock) {
            if (sslSocket != null || isClosed.get()) {
                throw IOException("Relay socket already used")
            }
        }

        val uri = URI(RelayConfig.WSS_ENDPOINT)
        if (uri.scheme != "wss") throw IOException("Relay endpoint must use wss://")
        val host = uri.host ?: throw IOException("Relay endpoint has no host")
        val port = if (uri.port > 0) uri.port else 443
        if (uri.path.isNullOrBlank()) throw IOException("Relay endpoint has no path")

        val plainSocket = Socket()
        var socketForFailure: SSLSocket? = null
        try {
            plainSocket.connect(InetSocketAddress(host, port), timeout.coerceAtLeast(1))
            val rawSocket = TLS_FACTORY.createSocket(plainSocket, host, port, true) as SSLSocket
            socketForFailure = rawSocket
            rawSocket.useClientMode = true
            rawSocket.sslParameters = rawSocket.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
                serverNames = listOf(SNIHostName(host))
            }
            rawSocket.startHandshake()

            val requestKeyBytes = ByteArray(16).also(RANDOM::nextBytes)
            val requestKey = Base64.getEncoder().encodeToString(requestKeyBytes)
            val requestTarget = buildRequestTarget(uri)

            val request = buildString {
                append("GET ")
                append(requestTarget)
                append(" HTTP/1.1\r\n")
                append("Host: ")
                append(host)
                if (port != 443) append(":").append(port)
                append("\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: ")
                append(requestKey)
                append("\r\n")
                append("Sec-WebSocket-Version: ")
                append(WEBSOCKET_VERSION)
                append("\r\n")
                append("\r\n")
            }

            val output = rawSocket.outputStream
            output.write(request.toByteArray(StandardCharsets.ISO_8859_1))
            output.flush()

            rawSocket.soTimeout = timeout.coerceAtLeast(1)
            val input = BufferedInputStream(rawSocket.inputStream)
            val response = readHttpResponse(input)
            validateHandshake(response.statusCode, response.headers, requestKey)
            rawSocket.soTimeout = 0

            synchronized(lock) {
                if (isClosed.get()) throw IOException("Relay socket was closed while connecting")
                sslSocket = rawSocket
                socketInput = input
                socketOutput = output
            }

            readerThread = Thread {
                runReader(input)
            }.apply {
                name = "BabyphoneRelayReader-${role.name.lowercase()}"
                isDaemon = true
                start()
            }

            opened.countDown()
        } catch (e: Exception) {
            try {
                socketForFailure?.close()
            } catch (_: IOException) {
                // Ignore cleanup failure.
            }
            if (socketForFailure == null) {
                try {
                    plainSocket.close()
                } catch (_: IOException) {
                    // Ignore cleanup failure.
                }
            }
            synchronized(lock) {
                failure = if (e is IOException) e else IOException("Relay WebSocket connect failed", e)
                isClosed.set(true)
                lock.notifyAll()
            }
            opened.countDown()
            closed.countDown()
            if (e is SocketTimeoutException) throw e
            throw if (e is IOException) e else IOException("Relay WebSocket connect failed", e)
        }
    }

    private fun buildRequestTarget(uri: URI): String {
        val path = uri.rawPath.takeIf { it.isNotEmpty() } ?: "/"
        val query = uri.rawQuery?.takeIf { it.isNotEmpty() }
        val base = if (query == null) path else "$path?$query"
        val separator = if (query == null) "?" else "&"
        return "$base${separator}session=$sessionId&role=${role.name.lowercase()}"
    }

    override fun getInputStream(): InputStream = input
    override fun getOutputStream(): OutputStream = output

    override fun setSoTimeout(timeout: Int) {
        require(timeout >= 0)
        synchronized(lock) { socketSoTimeoutMs = timeout }
    }

    override fun getSoTimeout(): Int = synchronized(lock) { socketSoTimeoutMs }

    override fun close() {
        if (!isClosed.compareAndSet(false, true)) return

        synchronized(lock) {
            if (failure == null) failure = IOException("Relay socket closed")
            lock.notifyAll()
        }

        try {
            synchronized(frameWriteLock) {
                sendCloseFrame()
            }
        } catch (_: IOException) {
            // Best effort close handshake.
        }

        closeUnderlyingSocket()
        closed.countDown()
    }

    fun awaitClosed(timeoutMs: Long): Boolean = closed.await(timeoutMs, TimeUnit.MILLISECONDS)

    override fun isClosed(): Boolean = isClosed.get()
    override fun isConnected(): Boolean = sslSocket != null && !isClosed.get()

    private fun runReader(input: InputStream) {
        var fragmentedOpcode = -1
        var fragmentedPayload = ByteArrayOutputStream()

        try {
            while (!isClosed.get()) {
                val first = input.read()
                if (first < 0) throw IOException("Relay WebSocket closed by peer")
                val second = readByte(input)

                val fin = first and 0x80 != 0
                val rsv = first and 0x70
                val opcode = first and 0x0f
                val masked = second and 0x80 != 0
                var length = second and 0x7f

                if (rsv != 0) throw IOException("Relay WebSocket used unsupported extensions")

                if (length == 126) {
                    length = readUnsignedShort(input)
                } else if (length == 127) {
                    val longLength = readUnsignedLong(input)
                    if (longLength < 0 || longLength > MAX_FRAME_PAYLOAD.toLong()) {
                        throw IOException("Relay WebSocket frame is too large")
                    }
                    length = longLength.toInt()
                }

                if ((opcode and 0x8) != 0 && (!fin || length > MAX_CONTROL_PAYLOAD)) {
                    throw IOException("Invalid WebSocket control frame")
                }
                if (length > MAX_FRAME_PAYLOAD) throw IOException("Relay WebSocket frame is too large")

                val mask = if (masked) ByteArray(4).also { readFully(input, it, 0, 4) } else null
                val payload = ByteArray(length)
                readFully(input, payload, 0, payload.size)
                if (mask != null) {
                    for (index in payload.indices) {
                        payload[index] = (payload[index].toInt() xor (mask[index and 3].toInt())).toByte()
                    }
                }

                when (opcode) {
                    0x0 -> {
                        if (fragmentedOpcode < 0) throw IOException("Unexpected continuation frame")
                        appendFragment(fragmentedPayload, payload)
                        if (fin) {
                            if (fragmentedOpcode == 0x2) enqueueBinary(fragmentedPayload.toByteArray())
                            fragmentedOpcode = -1
                            fragmentedPayload = ByteArrayOutputStream()
                        }
                    }

                    0x1, 0x2 -> {
                        if (fragmentedOpcode >= 0) throw IOException("Nested fragmented WebSocket message")
                        if (fin) {
                            if (opcode == 0x2) enqueueBinary(payload)
                        } else {
                            fragmentedOpcode = opcode
                            fragmentedPayload = ByteArrayOutputStream()
                            appendFragment(fragmentedPayload, payload)
                        }
                    }

                    0x8 -> {
                        synchronized(frameWriteLock) {
                            if (!closeFrameSent.get()) {
                                sendCloseFrame(payload)
                            }
                        }
                        markClosed(IOException("Relay WebSocket closed by peer"))
                        return
                    }

                    0x9 -> {
                        synchronized(frameWriteLock) {
                            sendFrame(0xA, payload, 0, payload.size, true)
                        }
                    }

                    0xA -> {
                        // Pong is handled internally; no application data is exposed.
                    }

                    else -> throw IOException("Unsupported WebSocket opcode: $opcode")
                }
            }
        } catch (e: Exception) {
            if (!isClosed.get()) {
                Log.w(TAG, "Relay WebSocket reader failed for $role", e)
                markClosed(if (e is IOException) e else IOException("Relay WebSocket reader failed", e))
            }
        }
    }

    private fun appendFragment(buffer: ByteArrayOutputStream, payload: ByteArray) {
        if (buffer.size() + payload.size > MAX_MESSAGE_BYTES) {
            throw IOException("Relay WebSocket message is too large")
        }
        buffer.write(payload)
    }

    private fun enqueueBinary(data: ByteArray) {
        synchronized(lock) {
            if (isClosed.get()) return
            if (queuedBytes + data.size > MAX_MESSAGE_BYTES) {
                failure = IOException("Relay receive buffer overflow")
                lock.notifyAll()
                throw failure!!
            }
            chunks.addLast(data)
            queuedBytes += data.size
            lock.notifyAll()
        }
    }

    private fun sendCloseFrame(payload: ByteArray = byteArrayOf(0x03, 0xE8.toByte(), 'c'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte(), 's'.code.toByte(), 'e'.code.toByte())) {
        if (!closeFrameSent.compareAndSet(false, true)) return
        try {
            sendFrame(0x8, payload, 0, payload.size.coerceAtMost(MAX_CONTROL_PAYLOAD), true)
        } catch (e: IOException) {
            Log.d(TAG, "Failed to write relay close frame", e)
        }
    }

    private fun sendFrame(opcode: Int, payload: ByteArray, offset: Int, length: Int, fin: Boolean) {
        val output = synchronized(lock) {
            socketOutput ?: throw IOException("Relay WebSocket is not open")
        }
        if (length < 0 || offset < 0 || offset + length > payload.size) {
            throw IllegalArgumentException("Invalid WebSocket payload bounds")
        }

        val header = ByteArrayOutputStream(14)
        header.write((if (fin) 0x80 else 0) or (opcode and 0x0f))

        when {
            length <= 125 -> {
                header.write(0x80 or length)
            }

            length <= 0xFFFF -> {
                header.write(0x80 or 126)
                header.write((length ushr 8) and 0xff)
                header.write(length and 0xff)
            }

            else -> {
                header.write(0x80 or 127)
                for (shift in 56 downTo 0 step 8) {
                    header.write((length.toLong() ushr shift).toInt() and 0xff)
                }
            }
        }

        val mask = ByteArray(4).also(RANDOM::nextBytes)
        header.write(mask)

        val maskedPayload = ByteArray(length)
        for (index in 0 until length) {
            maskedPayload[index] = (payload[offset + index].toInt() xor mask[index and 3].toInt()).toByte()
        }

        output.write(header.toByteArray())
        output.write(maskedPayload)
        output.flush()
    }

    private fun markClosed(reason: IOException) {
        if (!isClosed.compareAndSet(false, true)) return
        synchronized(lock) {
            if (failure == null) failure = reason
            lock.notifyAll()
        }
        closeUnderlyingSocket()
        closed.countDown()
    }

    private fun closeUnderlyingSocket() {
        synchronized(lock) {
            try {
                socketOutput?.flush()
            } catch (_: IOException) {
                // Ignore.
            }
            try {
                sslSocket?.close()
            } catch (e: IOException) {
                Log.d(TAG, "Failed to close relay TLS socket", e)
            }
            socketOutput = null
            socketInput = null
            sslSocket = null
        }
        closed.countDown()
    }

    private data class HttpResponse(
        val statusCode: Int,
        val headers: Map<String, String>
    )

    private fun readHttpResponse(input: InputStream): HttpResponse {
        val bytes = ByteArrayOutputStream()
        var previous1 = -1
        var previous2 = -1
        var previous3 = -1

        while (bytes.size() < MAX_HTTP_HEADER_BYTES) {
            val value = input.read()
            if (value < 0) throw IOException("Relay closed during WebSocket handshake")
            bytes.write(value)
            if (previous3 == '\r'.code && previous2 == '\n'.code &&
                previous1 == '\r'.code && value == '\n'.code
            ) {
                break
            }
            previous3 = previous2
            previous2 = previous1
            previous1 = value
        }

        val text = bytes.toByteArray().toString(StandardCharsets.ISO_8859_1)
        val lines = text.split("\r\n")
        if (lines.isEmpty()) throw IOException("Invalid relay WebSocket response")
        val statusParts = lines[0].split(" ", limit = 3)
        val statusCode = statusParts.getOrNull(1)?.toIntOrNull()
            ?: throw IOException("Invalid relay WebSocket status line")

        val headers = linkedMapOf<String, String>()
        for (line in lines.drop(1)) {
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            headers[name] = if (headers.containsKey(name)) {
                headers.getValue(name) + ", " + value
            } else {
                value
            }
        }
        return HttpResponse(statusCode, headers)
    }

    private fun validateHandshake(statusCode: Int, headers: Map<String, String>, requestKey: String) {
        if (statusCode != 101) throw IOException("Relay WebSocket upgrade failed: HTTP $statusCode")
        if (!headers["upgrade"].orEmpty().equals("websocket", ignoreCase = true)) {
            throw IOException("Relay WebSocket response is missing Upgrade: websocket")
        }
        if (!headers["connection"].orEmpty().split(',').any { it.trim().equals("upgrade", ignoreCase = true) }) {
            throw IOException("Relay WebSocket response is missing Connection: Upgrade")
        }

        val accepted = headers["sec-websocket-accept"]
            ?: throw IOException("Relay WebSocket response is missing Sec-WebSocket-Accept")
        val expected = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((requestKey + WEBSOCKET_GUID).toByteArray(StandardCharsets.ISO_8859_1))
        )
        if (accepted != expected) throw IOException("Relay WebSocket handshake validation failed")
    }

    private fun readByte(input: InputStream): Int {
        val value = input.read()
        if (value < 0) throw IOException("Relay WebSocket closed unexpectedly")
        return value
    }

    private fun readUnsignedShort(input: InputStream): Int {
        return (readByte(input) shl 8) or readByte(input)
    }

    private fun readUnsignedLong(input: InputStream): Long {
        var value = 0L
        repeat(8) {
            value = (value shl 8) or readByte(input).toLong()
        }
        return value
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int) {
        var remaining = length
        var position = offset
        while (remaining > 0) {
            val count = input.read(buffer, position, remaining)
            if (count < 0) throw IOException("Relay WebSocket closed during frame read")
            if (count == 0) continue
            position += count
            remaining -= count
        }
    }
}
