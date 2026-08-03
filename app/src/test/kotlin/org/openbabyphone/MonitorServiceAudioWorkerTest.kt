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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.audio.AudioCaptureSource
import org.openbabyphone.audio.AudioFrameTiming
import org.openbabyphone.service.MonitorServiceRepository
import org.openbabyphone.service.MonitorSessionError
import org.openbabyphone.service.MonitorSessionState
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class MonitorServiceAudioWorkerTest {
    @Before
    fun setUp() {
        MonitorServiceRepository.reset()
    }

    @After
    fun tearDown() {
        MonitorServiceRepository.reset()
    }

    @Test
    fun `negative capture read publishes audio capture error`() {
        val controller = Robolectric.buildService(MonitorService::class.java).create()
        val service = controller.get()
        configureActiveStream(service)
        service.audioCaptureFactory = { ScriptedCaptureSource { -6 } }

        try {
            startAudioProducer(service)

            assertEquals(MonitorSessionError.AudioCapture, awaitError().type)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `invalid capture sample count publishes audio capture error`() {
        val controller = Robolectric.buildService(MonitorService::class.java).create()
        val service = controller.get()
        configureActiveStream(service)
        service.audioCaptureFactory = { ScriptedCaptureSource { AudioFrameTiming.FRAME_SAMPLES + 1 } }

        try {
            startAudioProducer(service)

            assertEquals(MonitorSessionError.AudioCapture, awaitError().type)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `capture exception publishes audio capture error`() {
        val controller = Robolectric.buildService(MonitorService::class.java).create()
        val service = controller.get()
        configureActiveStream(service)
        service.audioCaptureFactory = {
            ScriptedCaptureSource { throw IllegalStateException("capture failed") }
        }

        try {
            startAudioProducer(service)

            assertEquals(MonitorSessionError.AudioCapture, awaitError().type)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `unexpected interruption of active capture worker publishes audio capture error`() {
        val controller = Robolectric.buildService(MonitorService::class.java).create()
        val service = controller.get()
        configureActiveStream(service)
        val source = ScriptedCaptureSource { 0 }
        service.audioCaptureFactory = { source }

        try {
            startAudioProducer(service)
            assertTrue(source.firstRead.await(2, TimeUnit.SECONDS))

            audioProducerThread(service).interrupt()

            assertEquals(MonitorSessionError.AudioCapture, awaitError().type)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `intentional capture shutdown does not publish audio capture error`() {
        val controller = Robolectric.buildService(MonitorService::class.java).create()
        val service = controller.get()
        configureActiveStream(service)
        val source = ScriptedCaptureSource { 0 }
        service.audioCaptureFactory = { source }

        try {
            startAudioProducer(service)
            assertTrue(source.firstRead.await(2, TimeUnit.SECONDS))

            invokeNoArg(service, "retireSessionWorkers")

            assertFalse(MonitorServiceRepository.sessionState.value is MonitorSessionState.Error)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `stale capture worker cannot replace the active capture source`() {
        val controller = Robolectric.buildService(MonitorService::class.java).create()
        val service = controller.get()
        configureActiveStream(service)
        val oldFactoryEntered = CountDownLatch(1)
        val releaseOldFactory = CountDownLatch(1)
        val oldSource = ScriptedCaptureSource { 0 }
        service.audioCaptureFactory = {
            oldFactoryEntered.countDown()
            while (releaseOldFactory.count > 0) {
                try {
                    releaseOldFactory.await()
                } catch (_: InterruptedException) {
                    // Simulate a platform factory that completes after worker retirement.
                }
            }
            oldSource
        }

        try {
            startAudioProducer(service)
            assertTrue(oldFactoryEntered.await(2, TimeUnit.SECONDS))
            val oldThread = audioProducerThread(service)
            invokeNoArg(service, "retireSessionWorkers")

            configureActiveStream(service, startId = 2)
            val replacementSource = ScriptedCaptureSource { 0 }
            service.audioCaptureFactory = { replacementSource }
            startAudioProducer(service)
            assertTrue(replacementSource.firstRead.await(2, TimeUnit.SECONDS))

            releaseOldFactory.countDown()
            oldThread.join(2_000)

            assertSame(replacementSource, currentCaptureSource(service))
            invokeNoArg(service, "retireSessionWorkers")
            assertTrue(replacementSource.stopCalls.get() > 0)
            assertFalse(MonitorServiceRepository.sessionState.value is MonitorSessionState.Error)
        } finally {
            releaseOldFactory.countDown()
            controller.destroy()
        }
    }

    private fun configureActiveStream(service: MonitorService, startId: Int = 1) {
        setPrivateField(service, "streamSessionId", ByteArray(CryptoHelper.SESSION_ID_SIZE) { 1 })
        setPrivateField(service, "connectionToken", Any())
        val generation = service.javaClass.getDeclaredField("workerGeneration").run {
            isAccessible = true
            get(service) as WorkerGeneration
        }
        setPrivateField(service, "activeWorkerClaim", generation.claim(startId))
    }

    private fun startAudioProducer(service: MonitorService) {
        val claim = service.javaClass.getDeclaredField("activeWorkerClaim").run {
            isAccessible = true
            get(service) as WorkerClaim
        }
        service.javaClass.getDeclaredMethod("startAudioProducer", WorkerClaim::class.java).run {
            isAccessible = true
            invoke(service, claim)
        }
    }

    private fun awaitError(): MonitorSessionState.Error {
        repeat(100) {
            val state = MonitorServiceRepository.sessionState.value
            if (state is MonitorSessionState.Error) return state
            Thread.sleep(20)
        }
        throw AssertionError("Monitor service did not publish a terminal error")
    }

    private fun audioProducerThread(service: MonitorService): Thread =
        service.javaClass.getDeclaredField("audioProducerThread").run {
            isAccessible = true
            get(service) as Thread
        }

    private fun currentCaptureSource(service: MonitorService): AudioCaptureSource? =
        service.javaClass.getDeclaredField("currentCaptureSource").run {
            isAccessible = true
            get(service) as AudioCaptureSource?
        }

    private fun invokeNoArg(service: MonitorService, methodName: String) {
        service.javaClass.getDeclaredMethod(methodName).run {
            isAccessible = true
            invoke(service)
        }
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private class ScriptedCaptureSource(private val nextRead: () -> Int) : AudioCaptureSource {
        val firstRead = CountDownLatch(1)
        val stopCalls = AtomicInteger()

        override fun start() = Unit

        override fun read(samples: ShortArray, offset: Int, count: Int): Int {
            firstRead.countDown()
            return nextRead()
        }

        override fun stop() {
            stopCalls.incrementAndGet()
        }

        override fun release() = Unit
    }
}
