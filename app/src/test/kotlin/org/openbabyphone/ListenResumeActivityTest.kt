/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.navigation.Listen
import org.openbabyphone.service.ListenServiceRepository
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
class ListenResumeActivityTest {
    private lateinit var application: OpenBabyphoneApplication

    @Before
    fun setup() {
        application = RuntimeEnvironment.getApplication() as OpenBabyphoneApplication
        application.getSharedPreferences(TrustedChildStore.METADATA_PREFS_NAME, 0).edit().clear().commit()
        application.getSharedPreferences(ProtectedTrustedCredentialStore.PREFS_NAME, 0).edit().clear().commit()
        PendingConnections.store.clear()
        InternalListenRouteRegistry.clear()
        ActiveListenSessionRegistry.clearForTests()
        ListenServiceRepository.reset()
    }

    @Test
    fun `live notification creates registry validated resume-only route`() {
        val token = ActiveListenSessionRegistry.register(null, "manual-request")
        ListenServiceRepository.startConnecting("Nursery")
        ListenServiceRepository.updateListening()

        val started = launchResume(token)
        val route = consumeStartedRoute(started)

        assertEquals(Listen(resumeOnly = true), route)
        assertFalse(started.hasExtra("resumeOnly"))
        assertFalse(started.hasExtra("pairingCode"))
    }

    @Test
    fun `stopped trusted notification creates internal retry request`() {
        withTrustedChild { store ->
            val token = ActiveListenSessionRegistry.register(
                ExpectedChildIdentity("child1", "pair1")
            )
            ActiveListenSessionRegistry.markInactive(token)

            val started = launchResume(token)
            val route = consumeStartedRoute(started)

            assertNotNull(route)
            assertFalse(route!!.resumeOnly)
            assertEquals("child1", route.expectedChildId)
            val pending = PendingConnections.store.lease(route.requestId)
            assertEquals("child1", pending?.expectedChildId)
            assertNull(pending?.pairingCode)
            assertTrue(store.getAll().isNotEmpty())
        }
    }

    @Test
    fun `terminal lost notification creates retry instead of active resume`() {
        withTrustedChild {
            val token = ActiveListenSessionRegistry.register(
                ExpectedChildIdentity("child1", "pair1")
            )
            ListenServiceRepository.updateLost()

            val route = consumeStartedRoute(launchResume(token))

            assertNotNull(route)
            assertFalse(route!!.resumeOnly)
            assertTrue(route.requestId.isNotBlank())
        }
    }

    @Test
    fun `retained manual request is reused after stopped session`() {
        val requestId = PendingConnections.store.put(
            PendingConnection("host", 10000, "Nursery", "code1234".toCharArray())
        )
        val token = ActiveListenSessionRegistry.register(null, requestId)
        ActiveListenSessionRegistry.markInactive(token)

        val route = consumeStartedRoute(launchResume(token))

        assertEquals(requestId, route?.requestId)
        assertTrue(PendingConnections.store.contains(requestId))
    }

    @Test
    fun `invalid token cannot forge an internal listen route`() {
        val started = launchResume(9999L)

        assertNull(started.getStringExtra(MainActivity.EXTRA_INTERNAL_ROUTE_ID))
    }

    @Test
    fun `onNewIntent route consumption is one shot`() {
        val route = Listen("request", "child", "pair")
        val routeId = InternalListenRouteRegistry.put(route)
        val intent = Intent().putExtra(MainActivity.EXTRA_INTERNAL_ROUTE_ID, routeId)

        assertEquals(route, consumeInternalListenRoute(intent))
        assertNull(consumeInternalListenRoute(intent))
    }

    private fun launchResume(token: Long): Intent {
        val intent = Intent(application, ListenResumeActivity::class.java)
            .putExtra(ListenResumeActivity.EXTRA_SESSION_TOKEN, token)
        Robolectric.buildActivity(ListenResumeActivity::class.java, intent).create()
        return shadowOf(application).nextStartedActivity
    }

    private fun consumeStartedRoute(started: Intent): Listen? {
        assertEquals(MainActivity::class.java.name, started.component?.className)
        val routeId = started.getStringExtra(MainActivity.EXTRA_INTERNAL_ROUTE_ID)
        assertNotNull(routeId)
        return InternalListenRouteRegistry.consume(routeId!!)
    }

    private fun withTrustedChild(block: (TrustedChildStore) -> Unit) {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = TrustedChildStore(application, AesGcmTrustedCredentialCrypto({ key }))
        val code = "code1234".toCharArray()
        assertEquals(
            CredentialStorageResult.Success,
            store.trustAuthenticated("child1", "pair1", "Nursery", code, "host", 10000)
        )
        code.fill('\u0000')
        val field = application.javaClass.getDeclaredField("trustedChildStore").apply { isAccessible = true }
        val originalStore = field.get(application)
        field.set(application, store)
        try {
            block(store)
        } finally {
            field.set(application, originalStore)
            PendingConnections.store.clear()
        }
    }
}
