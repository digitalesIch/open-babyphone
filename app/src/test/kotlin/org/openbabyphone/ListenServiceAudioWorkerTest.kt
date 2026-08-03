/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.audio.AudioFrameTiming
import org.openbabyphone.audio.AudioPlaybackSink
import org.openbabyphone.audio.FrameCodec
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.ListenSessionError
import org.openbabyphone.service.ListenSessionState
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class ListenServiceAudioWorkerTest {
    @Before
    fun setUp() {
        ListenServiceRepository.reset()
    }

    @After
    fun tearDown() {
        ListenServiceRepository.reset()
    }

    @Test
    fun `partial writes publish listening only after a complete decoded frame`() {
        val controller = Robolectric.buildService(ListenService::class.java).create()
        val service = controller.get()
        val sink = RecordingSink(maximumWrite = 16)
        val delivered = CountDownLatch(1)
        service.audioPlaybackFactory = { sink }
        service.onUpdate = { delivered.countDown() }
        val result = runStream(service = service) { delivered.await(2, TimeUnit.SECONDS) }

        try {
            assertEquals(0, delivered.count)
            assertTrue(sink.stateAtFirstWrite.get() !is ListenSessionState.Listening)
            assertEquals(ListenSessionState.Listening, ListenServiceRepository.sessionState.value)
            assertTrue(sink.writtenSamples.get() >= AudioFrameTiming.FRAME_SAMPLES)
            assertStreamResult(result, "Reconnect")
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `repeated zero progress writes fail the stream as playback`() {
        val controller = Robolectric.buildService(ListenService::class.java).create()
        val service = controller.get()
        val sink = RecordingSink(maximumWrite = 0)
        var now = 0L
        service.audioPlaybackFactory = { sink }
        service.audioWriteElapsedRealtime = { now }
        service.audioWriteRetryPause = { now += 5_000L }
        val result = runStream(waitForServiceClose = true, service = service) {
            sink.firstWrite.await(2, TimeUnit.SECONDS)
        }

        try {
            assertTrue(sink.firstWrite.await(2, TimeUnit.SECONDS))
            assertFatal(result, ListenSessionError.Playback)
            assertTrue(ListenServiceRepository.sessionState.value !is ListenSessionState.Listening)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `playback sink exception fails the stream as playback`() {
        val controller = Robolectric.buildService(ListenService::class.java).create()
        val service = controller.get()
        val sink = ThrowingSink()
        service.audioPlaybackFactory = { sink }

        val result = runStream(waitForServiceClose = true, service = service) {
            sink.firstWrite.await(2, TimeUnit.SECONDS)
        }

        try {
            assertFatal(result, ListenSessionError.Playback)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `unexpected interruption of active playback worker fails the stream as playback`() {
        val controller = Robolectric.buildService(ListenService::class.java).create()
        val service = controller.get()
        val sink = RecordingSink(maximumWrite = 0)
        service.audioPlaybackFactory = { sink }
        service.audioWriteElapsedRealtime = { 0L }
        service.audioWriteRetryPause = { throw InterruptedException("worker interrupted") }

        val result = runStream(waitForServiceClose = true, service = service) {
            sink.firstWrite.await(2, TimeUnit.SECONDS)
        }

        try {
            assertFatal(result, ListenSessionError.Playback)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `jitter overflow disrupts playback until a complete real frame is written`() {
        val controller = Robolectric.buildService(ListenService::class.java).create()
        val service = controller.get()
        val sink = BlockingSink()
        val recovered = CountDownLatch(1)
        service.audioPlaybackFactory = { sink }
        service.onUpdate = { recovered.countDown() }

        val result = runStream(frameCount = OVERFLOW_FRAME_COUNT, service = service) {
            val writeStarted = sink.firstWrite.await(2, TimeUnit.SECONDS)
            val disrupted = awaitState(ListenSessionState.Disrupted)
            sink.releaseWrite.countDown()
            writeStarted && disrupted && recovered.await(2, TimeUnit.SECONDS)
        }

        try {
            assertStreamResult(result, "Reconnect")
            assertEquals(ListenSessionState.Listening, ListenServiceRepository.sessionState.value)
        } finally {
            sink.releaseWrite.countDown()
            controller.destroy()
        }
    }

    private fun runStream(
        frameCount: Int = JITTER_PRE_ROLL_FRAMES,
        waitForServiceClose: Boolean = false,
        service: ListenService,
        awaitWrite: () -> Boolean
    ): Any {
        val claim = configureActiveWorker(service)
        val sessionId = ByteArray(CryptoHelper.SESSION_ID_SIZE) { 3 }
        val streamKey = ByteArray(CryptoHelper.KEY_SIZE) { 7 }
        val session = SessionInfo(sessionId, streamKey, 0, "abcdefghijklmnop", "1234567890abcdef")
        val result = AtomicReference<Any?>()
        val failure = AtomicReference<Throwable?>()
        val finished = CountDownLatch(1)

        ServerSocket(0).use { server ->
            val child = Thread {
                try {
                    server.accept().use { socket ->
                        socket.getOutputStream().use { output ->
                            repeat(frameCount) { sequence ->
                                output.write(
                                    FrameCodec.encodeFrame(
                                        ByteArray(AudioFrameTiming.FRAME_SAMPLES) { 0x7f },
                                        sequence,
                                        sequence * AudioFrameTiming.FRAME_DURATION_MS,
                                        streamKey,
                                        sessionId
                                    )
                                )
                            }
                            output.flush()
                            assertTrue(awaitWrite())
                            if (waitForServiceClose) socket.getInputStream().read()
                        }
                    }
                } catch (throwable: Throwable) {
                    failure.set(throwable)
                }
            }.also(Thread::start)
            Socket("127.0.0.1", server.localPort).use { socket ->
                Thread {
                    try {
                        result.set(invokeStreamAudio(service, socket, session, claim))
                    } catch (throwable: Throwable) {
                        failure.set(throwable)
                    } finally {
                        finished.countDown()
                    }
                }.start()
                assertTrue(finished.await(STREAM_FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
            child.join(STREAM_FINISH_TIMEOUT_SECONDS * 1_000L)
        }
        failure.get()?.let { throw it }
        return requireNotNull(result.get())
    }

    private fun awaitState(expected: ListenSessionState): Boolean {
        repeat(100) {
            if (ListenServiceRepository.sessionState.value == expected) return true
            Thread.sleep(20)
        }
        return false
    }

    private fun assertStreamResult(result: Any, expectedName: String) {
        assertEquals(expectedName, result.javaClass.simpleName)
    }

    private fun assertFatal(result: Any, expectedType: ListenSessionError) {
        assertStreamResult(result, "Fatal")
        val type = result.javaClass.getDeclaredField("type").run {
            isAccessible = true
            get(result) as ListenSessionError
        }
        assertEquals(expectedType, type)
    }

    private fun configureActiveWorker(service: ListenService): WorkerClaim {
        val generation = service.javaClass.getDeclaredField("workerGeneration").run {
            isAccessible = true
            get(service) as WorkerGeneration
        }
        val claim = generation.claim(1)
        setPrivateField(service, "isRunning", true)
        return claim
    }

    private fun invokeStreamAudio(
        service: ListenService,
        socket: Socket,
        session: SessionInfo,
        claim: WorkerClaim
    ): Any {
        return service.javaClass.getDeclaredMethod(
            "streamAudio",
            Socket::class.java,
            SessionInfo::class.java,
            WorkerClaim::class.java,
            AtomicBoolean::class.java,
            AtomicBoolean::class.java
        ).run {
            isAccessible = true
            requireNotNull(invoke(service, socket, session, claim, AtomicBoolean(false), AtomicBoolean(false)))
        }
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private class RecordingSink(private val maximumWrite: Int) : AudioPlaybackSink {
        val firstWrite = CountDownLatch(1)
        val writtenSamples = AtomicLong()
        val stateAtFirstWrite = AtomicReference<ListenSessionState?>()

        override fun start() = Unit

        override fun write(samples: ShortArray, offset: Int, count: Int): Int {
            stateAtFirstWrite.compareAndSet(null, ListenServiceRepository.sessionState.value)
            firstWrite.countDown()
            val written = minOf(maximumWrite, count)
            writtenSamples.addAndGet(written.toLong())
            return written
        }

        override fun stop() = Unit

        override fun release() = Unit
    }

    private class ThrowingSink : AudioPlaybackSink {
        val firstWrite = CountDownLatch(1)

        override fun start() = Unit

        override fun write(samples: ShortArray, offset: Int, count: Int): Int {
            firstWrite.countDown()
            throw IllegalStateException("playback failed")
        }

        override fun stop() = Unit

        override fun release() = Unit
    }

    private class BlockingSink : AudioPlaybackSink {
        val firstWrite = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)

        override fun start() = Unit

        override fun write(samples: ShortArray, offset: Int, count: Int): Int {
            firstWrite.countDown()
            if (!releaseWrite.await(BLOCKED_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IllegalStateException("blocked write was not released")
            }
            return count
        }

        override fun stop() = Unit

        override fun release() = Unit
    }

    private companion object {
        const val JITTER_PRE_ROLL_FRAMES = 3
        const val OVERFLOW_FRAME_COUNT = 10
        const val BLOCKED_WRITE_TIMEOUT_SECONDS = 7L
        const val STREAM_FINISH_TIMEOUT_SECONDS = 8L
    }
}
