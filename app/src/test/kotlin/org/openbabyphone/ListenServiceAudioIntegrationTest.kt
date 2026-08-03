/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone

import android.app.Application
import android.content.Intent
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.audio.AudioFrameTiming
import org.openbabyphone.audio.AudioPlaybackSink
import org.openbabyphone.audio.FrameCodec
import org.openbabyphone.audio.JitterBuffer
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.ListenSessionError
import org.openbabyphone.service.ListenSessionState
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class ListenServiceAudioIntegrationTest {
    private lateinit var application: Application

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        clearTrustedChildren()
        ActiveListenSessionRegistry.clearForTests()
        PendingConnections.store.clear()
        ListenServiceRepository.reset()
    }

    @After
    fun tearDown() {
        clearTrustedChildren()
        ActiveListenSessionRegistry.clearForTests()
        PendingConnections.store.clear()
        ListenServiceRepository.reset()
    }

    @Test
    fun `authenticated connection becomes listening only after a complete audio write`() {
        ServerSocket(0).use { server ->
            val heartbeatSent = CountDownLatch(1)
            val startAudio = CountDownLatch(1)
            val belowPreRollSent = CountDownLatch(1)
            val sendRemainingAudio = CountDownLatch(1)
            val closeChild = CountDownLatch(1)
            val child = startAuthenticatedChild(server) { _, output, streamKey, sessionId ->
                output.write(FrameCodec.encodeHeartbeat(0, 0, streamKey, sessionId))
                output.flush()
                heartbeatSent.countDown()
                check(startAudio.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                writeAudioFrames(output, streamKey, sessionId, 1 until JitterBuffer.BASE_TARGET_FRAMES)
                belowPreRollSent.countDown()
                check(sendRemainingAudio.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                writeAudioFrames(
                    output,
                    streamKey,
                    sessionId,
                    JitterBuffer.BASE_TARGET_FRAMES..JitterBuffer.MAX_TARGET_FRAMES
                )
                check(closeChild.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
            val intent = listenIntent(server.localPort)
            val controller = Robolectric.buildService(ListenService::class.java, intent).create()
            val service = controller.get()
            val sink = GatedPartialSink()
            val delivered = CountDownLatch(1)
            service.audioPlaybackFactory = { sink }
            service.onUpdate = { delivered.countDown() }

            try {
                service.onStartCommand(intent, 0, 1)
                assertTrue(heartbeatSent.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                assertTrue(ListenServiceRepository.sessionState.value !is ListenSessionState.Listening)

                startAudio.countDown()
                assertTrue(belowPreRollSent.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                assertEquals(1L, sink.firstWrite.count)
                assertTrue(ListenServiceRepository.sessionState.value !is ListenSessionState.Listening)

                sendRemainingAudio.countDown()
                assertTrue(sink.completionWriteEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                assertTrue(ListenServiceRepository.sessionState.value !is ListenSessionState.Listening)

                sink.releaseCompletion.countDown()
                assertTrue(delivered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                assertEquals(ListenSessionState.Listening, ListenServiceRepository.sessionState.value)

                val expected = ShortArray(AudioFrameTiming.FRAME_SAMPLES)
                AudioCodecDefines.CODEC.decode(expected, ULAW_FRAME, ULAW_FRAME.size, 0)
                assertArrayEquals(expected, sink.firstFrame())
                assertEquals(
                    listOf(
                        0 to AudioFrameTiming.FRAME_SAMPLES,
                        PARTIAL_WRITE_SAMPLES to AudioFrameTiming.FRAME_SAMPLES - PARTIAL_WRITE_SAMPLES
                    ),
                    sink.firstWriteRequests()
                )
            } finally {
                startAudio.countDown()
                sendRemainingAudio.countDown()
                sink.releaseCompletion.countDown()
                closeChild.countDown()
                controller.destroy()
                child.thread.join(CHILD_JOIN_TIMEOUT_MILLIS)
            }
            assertFalse(child.thread.isAlive)
            child.failure.get()?.let { throw it }
        }
    }

    @Test
    fun `authenticated playback stall publishes terminal playback error`() {
        ServerSocket(0).use { server ->
            val childObservedClose = CountDownLatch(1)
            val child = startAuthenticatedChild(server) { socket, output, streamKey, sessionId ->
                output.write(FrameCodec.encodeHeartbeat(0, 0, streamKey, sessionId))
                writeAudioFrames(output, streamKey, sessionId, 1..JitterBuffer.MAX_TARGET_FRAMES)
                check(socket.getInputStream().read() == -1)
                childObservedClose.countDown()
            }
            val intent = listenIntent(server.localPort)
            val controller = Robolectric.buildService(ListenService::class.java, intent).create()
            val service = controller.get()
            val sink = ZeroProgressSink()
            val now = AtomicLong()
            service.audioPlaybackFactory = { sink }
            service.audioWriteElapsedRealtime = now::get
            service.audioWriteRetryPause = { now.addAndGet(5_000L) }

            try {
                service.onStartCommand(intent, 0, 1)
                assertTrue(sink.firstWrite.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                val error = awaitError()

                assertEquals(ListenSessionError.Playback, error.type)
                assertTrue(childObservedClose.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            } finally {
                controller.destroy()
                child.thread.join(CHILD_JOIN_TIMEOUT_MILLIS)
            }
            assertFalse(child.thread.isAlive)
            child.failure.get()?.let { throw it }
        }
    }

    private fun listenIntent(port: Int): Intent {
        val requestId = PendingConnections.store.put(
            PendingConnection(
                address = "127.0.0.1",
                port = port,
                name = "Nursery",
                pairingCode = PAIRING_CODE.toCharArray(),
                expectedChildId = CHILD_ID,
                expectedPairingId = PAIRING_ID
            )
        )
        return Intent(application, ListenService::class.java)
            .putExtra("requestId", requestId)
            .putExtra("expectedChildId", CHILD_ID)
            .putExtra("expectedPairingId", PAIRING_ID)
    }

    private fun startAuthenticatedChild(
        server: ServerSocket,
        stream: (Socket, OutputStream, ByteArray, ByteArray) -> Unit
    ): ChildHandle {
        val failure = AtomicReference<Throwable?>()
        val thread = Thread {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = CHILD_SOCKET_TIMEOUT_MILLIS.toInt()
                    val sessionId = ByteArray(CryptoHelper.SESSION_ID_SIZE) { 2 }
                    val salt = ByteArray(CryptoHelper.SALT_SIZE) { 1 }
                    val hello = Handshake.createChildHello(
                        ChildDeviceIdentity(CHILD_ID, PAIRING_ID),
                        sessionId,
                        salt,
                        ByteArray(CryptoHelper.CHALLENGE_SIZE) { 3 }
                    )
                    val output = socket.getOutputStream()
                    Handshake.writeChildHello(output, hello)
                    val response = checkNotNull(Handshake.readParentResponse(socket.getInputStream()))
                    val baseKey = CryptoHelper.deriveKey(PAIRING_CODE, salt)
                    val authKey = CryptoHelper.deriveAuthKey(baseKey)
                    val streamKey = CryptoHelper.deriveStreamKey(baseKey, Handshake.streamKeyContext(hello))
                    try {
                        check(Handshake.verifyParentResponse(hello, response, authKey))
                        Handshake.writeChildAck(
                            output,
                            Handshake.createChildAck(
                                hello,
                                response,
                                0,
                                authKey,
                                ByteArray(CryptoHelper.NONCE_SIZE) { 4 }
                            )
                        )
                        stream(socket, output, streamKey, sessionId)
                    } finally {
                        streamKey.fill(0)
                        authKey.fill(0)
                        baseKey.fill(0)
                    }
                }
            } catch (throwable: Throwable) {
                failure.set(throwable)
            }
        }.also(Thread::start)
        return ChildHandle(thread, failure)
    }

    private fun writeAudioFrames(
        output: OutputStream,
        streamKey: ByteArray,
        sessionId: ByteArray,
        sequences: IntRange
    ) {
        sequences.forEach { sequence ->
            output.write(
                FrameCodec.encodeFrame(
                    ULAW_FRAME,
                    sequence,
                    sequence * AudioFrameTiming.FRAME_DURATION_MS,
                    streamKey,
                    sessionId
                )
            )
        }
        output.flush()
    }

    private fun awaitError(): ListenSessionState.Error {
        repeat(200) {
            val state = ListenServiceRepository.sessionState.value
            if (state is ListenSessionState.Error) return state
            Thread.sleep(20)
        }
        throw AssertionError("Listen service did not publish a terminal error")
    }

    private fun clearTrustedChildren() {
        application.getSharedPreferences(TrustedChildStore.METADATA_PREFS_NAME, 0).edit().clear().commit()
        application.getSharedPreferences(ProtectedTrustedCredentialStore.PREFS_NAME, 0).edit().clear().commit()
    }

    private data class ChildHandle(
        val thread: Thread,
        val failure: AtomicReference<Throwable?>
    )

    private class GatedPartialSink : AudioPlaybackSink {
        val firstWrite = CountDownLatch(1)
        val completionWriteEntered = CountDownLatch(1)
        val releaseCompletion = CountDownLatch(1)
        private val writeCalls = AtomicInteger()
        private val recordedSamples = mutableListOf<Short>()
        private val writeRequests = mutableListOf<Pair<Int, Int>>()

        override fun start() = Unit

        override fun write(samples: ShortArray, offset: Int, count: Int): Int {
            val call = writeCalls.incrementAndGet()
            synchronized(writeRequests) { writeRequests += offset to count }
            val written = if (call == 1) minOf(PARTIAL_WRITE_SAMPLES, count) else count
            if (call == 1) firstWrite.countDown()
            if (call == 2) {
                completionWriteEntered.countDown()
                check(releaseCompletion.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
            synchronized(recordedSamples) {
                for (index in offset until offset + written) recordedSamples += samples[index]
            }
            return written
        }

        fun firstFrame(): ShortArray = synchronized(recordedSamples) {
            recordedSamples.take(AudioFrameTiming.FRAME_SAMPLES).toShortArray()
        }

        fun firstWriteRequests(): List<Pair<Int, Int>> = synchronized(writeRequests) {
            writeRequests.take(2)
        }

        override fun stop() = Unit

        override fun release() = Unit
    }

    private class ZeroProgressSink : AudioPlaybackSink {
        val firstWrite = CountDownLatch(1)

        override fun start() = Unit

        override fun write(samples: ShortArray, offset: Int, count: Int): Int {
            firstWrite.countDown()
            return 0
        }

        override fun stop() = Unit

        override fun release() = Unit
    }

    private companion object {
        const val PAIRING_CODE = "ABCDEF12"
        const val CHILD_ID = "abcdefghijklmnop"
        const val PAIRING_ID = "1234567890abcdef"
        const val PARTIAL_WRITE_SAMPLES = 16
        const val TEST_TIMEOUT_SECONDS = 8L
        const val CHILD_SOCKET_TIMEOUT_MILLIS = 5_000L
        const val CHILD_JOIN_TIMEOUT_MILLIS = 7_000L
        val ULAW_FRAME = ByteArray(AudioFrameTiming.FRAME_SAMPLES) { it.toByte() }
    }
}
