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

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.openbabyphone.service.ListenServiceRepository
import org.openbabyphone.service.ListenSessionError
import org.openbabyphone.service.ListenSessionState
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class ListenServiceHandshakeTest {
    @Test
    fun `trusted identity mismatch is rejected before parent proof`() {
        val controller = Robolectric.buildService(ListenService::class.java)
        val service = controller.create().get()
        val bytesReceivedFromParent = AtomicInteger(Int.MIN_VALUE)

        ServerSocket(0).use { server ->
            val child = Thread {
                server.accept().use { socket ->
                    val hello = Handshake.createChildHello(
                        ChildDeviceIdentity("abcdefghijklmnop", "1234567890abcdef"),
                        ByteArray(CryptoHelper.SESSION_ID_SIZE),
                        ByteArray(CryptoHelper.SALT_SIZE),
                        ByteArray(CryptoHelper.CHALLENGE_SIZE)
                    )
                    Handshake.writeChildHello(socket.getOutputStream(), hello)
                    bytesReceivedFromParent.set(socket.getInputStream().read())
                }
            }
            child.start()
            Socket("127.0.0.1", server.localPort).use { parentSocket ->
                val result = invokePerformHandshake(
                    service,
                    parentSocket,
                    ExpectedChildIdentity("ponmlkjihgfedcba", "1234567890abcdef")
                )
                assertNull(result)
            }
            child.join(2_000)
            assertEquals(-1, bytesReceivedFromParent.get())
        }
        controller.destroy()
    }

    @Test
    fun `authenticated persistence failure keeps pending request and reports credential error`() {
        val application = RuntimeEnvironment.getApplication() as OpenBabyphoneApplication
        application.getSharedPreferences(TrustedChildStore.METADATA_PREFS_NAME, 0).edit().clear().commit()
        application.getSharedPreferences(ProtectedTrustedCredentialStore.PREFS_NAME, 0).edit().clear().commit()
        val field = application.javaClass.getDeclaredField("trustedChildStore").apply { isAccessible = true }
        val originalStore = field.get(application)
        field.set(application, TrustedChildStore(application, UnavailableCrypto()))
        ListenServiceRepository.reset()
        val identity = ExpectedChildIdentity("abcdefghijklmnop", "1234567890abcdef")

        try {
            ServerSocket(0).use { server ->
                val requestId = PendingConnections.store.put(
                    PendingConnection(
                        address = "127.0.0.1",
                        port = server.localPort,
                        name = "Nursery",
                        pairingCode = "ABCDEF12".toCharArray(),
                        expectedChildId = identity.childId,
                        expectedPairingId = identity.pairingId,
                        rememberAfterAuthentication = true
                    )
                )
                val child = Thread {
                    server.accept().use { socket ->
                        val salt = ByteArray(CryptoHelper.SALT_SIZE) { 1 }
                        val hello = Handshake.createChildHello(
                            ChildDeviceIdentity(identity.childId, identity.pairingId),
                            ByteArray(CryptoHelper.SESSION_ID_SIZE) { 2 },
                            salt,
                            ByteArray(CryptoHelper.CHALLENGE_SIZE) { 3 }
                        )
                        Handshake.writeChildHello(socket.getOutputStream(), hello)
                        val response = Handshake.readParentResponse(socket.getInputStream())!!
                        val baseKey = CryptoHelper.deriveKey("ABCDEF12", salt)
                        val authKey = CryptoHelper.deriveAuthKey(baseKey)
                        try {
                            val ack = Handshake.createChildAck(
                                hello,
                                response,
                                0,
                                authKey,
                                ByteArray(CryptoHelper.NONCE_SIZE) { 4 }
                            )
                            Handshake.writeChildAck(socket.getOutputStream(), ack)
                        } finally {
                            authKey.fill(0)
                            baseKey.fill(0)
                        }
                    }
                }.also(Thread::start)
                val intent = Intent(application, ListenService::class.java)
                    .putExtra("requestId", requestId)
                    .putExtra("expectedChildId", identity.childId)
                    .putExtra("expectedPairingId", identity.pairingId)
                val controller = Robolectric.buildService(ListenService::class.java, intent).create()

                controller.get().onStartCommand(intent, 0, 1)
                val error = awaitError()

                assertEquals(
                    ListenSessionError.CredentialStorage,
                    error.type
                )
                assertTrue(PendingConnections.store.contains(requestId))
                assertTrue(application.trustedChildStore.getAll().isEmpty())
                child.join(2_000)
                controller.destroy()
            }
        } finally {
            field.set(application, originalStore)
            PendingConnections.store.clear()
        }
    }

    @Test
    fun `wrong pairing proof is classified as authentication and retains pending request`() {
        ListenServiceRepository.reset()
        val application = RuntimeEnvironment.getApplication()
        val identity = ExpectedChildIdentity("abcdefghijklmnop", "1234567890abcdef")

        ServerSocket(0).use { server ->
            val requestId = PendingConnections.store.put(
                PendingConnection(
                    address = "127.0.0.1",
                    port = server.localPort,
                    name = "Nursery",
                    pairingCode = "WRONG123".toCharArray(),
                    expectedChildId = identity.childId,
                    expectedPairingId = identity.pairingId,
                    rememberAfterAuthentication = true
                )
            )
            val child = Thread {
                server.accept().use { socket ->
                    val salt = ByteArray(CryptoHelper.SALT_SIZE) { 1 }
                    val hello = Handshake.createChildHello(
                        ChildDeviceIdentity(identity.childId, identity.pairingId),
                        ByteArray(CryptoHelper.SESSION_ID_SIZE) { 2 },
                        salt,
                        ByteArray(CryptoHelper.CHALLENGE_SIZE) { 3 }
                    )
                    Handshake.writeChildHello(socket.getOutputStream(), hello)
                    val response = Handshake.readParentResponse(socket.getInputStream())!!
                    val baseKey = CryptoHelper.deriveKey("RIGHT123", salt)
                    val authKey = CryptoHelper.deriveAuthKey(baseKey)
                    try {
                        Handshake.writeChildAck(
                            socket.getOutputStream(),
                            Handshake.createChildAck(
                                hello,
                                response,
                                0,
                                authKey,
                                ByteArray(CryptoHelper.NONCE_SIZE) { 4 }
                            )
                        )
                    } finally {
                        authKey.fill(0)
                        baseKey.fill(0)
                    }
                }
            }.also(Thread::start)
            val intent = Intent(application, ListenService::class.java)
                .putExtra("requestId", requestId)
                .putExtra("expectedChildId", identity.childId)
                .putExtra("expectedPairingId", identity.pairingId)
            val controller = Robolectric.buildService(ListenService::class.java, intent).create()

            try {
                controller.get().onStartCommand(intent, 0, 1)
                val error = awaitError()

                assertEquals(
                    ListenSessionError.Authentication,
                    error.type
                )
                assertTrue(PendingConnections.store.contains(requestId))
                child.join(2_000)
            } finally {
                controller.destroy()
                PendingConnections.store.clear()
            }
        }
    }

    @Test
    fun `trusted credential read unavailability reports credential unavailable terminal error`() {
        val application = RuntimeEnvironment.getApplication() as OpenBabyphoneApplication
        application.getSharedPreferences(TrustedChildStore.METADATA_PREFS_NAME, 0).edit().clear().commit()
        application.getSharedPreferences(ProtectedTrustedCredentialStore.PREFS_NAME, 0).edit().clear().commit()
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = TrustedChildStore(application, AesGcmTrustedCredentialCrypto({ key }))
        assertEquals(
            CredentialStorageResult.Success,
            store.trustAuthenticated(
                "abcdefghijklmnop",
                "1234567890abcdef",
                "Nursery",
                "code1234".toCharArray(),
                "127.0.0.1",
                10_000
            )
        )
        val field = application.javaClass.getDeclaredField("trustedChildStore").apply { isAccessible = true }
        val originalStore = field.get(application)
        field.set(application, TrustedChildStore(application, SwitchableCrypto(AesGcmTrustedCredentialCrypto({ key })).also { it.available = false }))
        ListenServiceRepository.reset()

        val intent = Intent(application, ListenService::class.java)
            .putExtra("expectedChildId", "abcdefghijklmnop")
            .putExtra("expectedPairingId", "1234567890abcdef")
        val controller = Robolectric.buildService(ListenService::class.java, intent).create()

        try {
            controller.get().onStartCommand(intent, 0, 1)
            val error = awaitError()

            assertEquals(ListenSessionError.CredentialUnavailable, error.type)
        } finally {
            controller.destroy()
            field.set(application, originalStore)
            application.getSharedPreferences(TrustedChildStore.METADATA_PREFS_NAME, 0).edit().clear().commit()
            application.getSharedPreferences(ProtectedTrustedCredentialStore.PREFS_NAME, 0).edit().clear().commit()
        }
    }

    @Test
    fun `corrupt trusted credential reports credential corrupt terminal error and cleans up`() {
        val application = RuntimeEnvironment.getApplication() as OpenBabyphoneApplication
        application.getSharedPreferences(TrustedChildStore.METADATA_PREFS_NAME, 0).edit().clear().commit()
        val credentialsPrefs = application.getSharedPreferences(ProtectedTrustedCredentialStore.PREFS_NAME, 0)
        credentialsPrefs.edit().clear().commit()
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = TrustedChildStore(application, AesGcmTrustedCredentialCrypto({ key }))
        assertEquals(
            CredentialStorageResult.Success,
            store.trustAuthenticated(
                "abcdefghijklmnop",
                "1234567890abcdef",
                "Nursery",
                "code1234".toCharArray(),
                "127.0.0.1",
                10_000
            )
        )
        credentialsPrefs.edit().putString(credentialsPrefs.all.keys.single(), "not-json").commit()
        ListenServiceRepository.reset()

        val intent = Intent(application, ListenService::class.java)
            .putExtra("expectedChildId", "abcdefghijklmnop")
            .putExtra("expectedPairingId", "1234567890abcdef")
        val controller = Robolectric.buildService(ListenService::class.java, intent).create()

        try {
            controller.get().onStartCommand(intent, 0, 1)
            val error = awaitError()

            assertEquals(ListenSessionError.CredentialCorrupt, error.type)
            assertTrue(credentialsPrefs.all.isEmpty())
            assertTrue(store.getAll().isEmpty())
        } finally {
            controller.destroy()
            application.getSharedPreferences(TrustedChildStore.METADATA_PREFS_NAME, 0).edit().clear().commit()
            credentialsPrefs.edit().clear().commit()
        }
    }

    private fun invokePerformHandshake(
        service: ListenService,
        socket: Socket,
        expectedIdentity: ExpectedChildIdentity
    ): SessionInfo? = service.javaClass.getDeclaredMethod(
        "performHandshake",
        Socket::class.java,
        CharArray::class.java,
        ExpectedChildIdentity::class.java
    ).run {
        isAccessible = true
        invoke(service, socket, "ABCDEF12".toCharArray(), expectedIdentity) as SessionInfo?
    }

    private fun awaitError(): ListenSessionState.Error {
        repeat(100) {
            val state = ListenServiceRepository.sessionState.value
            if (state is ListenSessionState.Error) return state
            Thread.sleep(20)
        }
        throw AssertionError("Listen service did not publish a terminal error")
    }

    private class UnavailableCrypto : TrustedCredentialCrypto {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ProtectedCredential =
            throw CredentialCryptoUnavailableException()
        override fun decrypt(protectedCredential: ProtectedCredential, aad: ByteArray): ByteArray =
            throw CredentialCryptoUnavailableException()
    }

    private class SwitchableCrypto(private val delegate: TrustedCredentialCrypto) : TrustedCredentialCrypto {
        var available = true
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ProtectedCredential {
            if (!available) throw CredentialCryptoUnavailableException()
            return delegate.encrypt(plaintext, aad)
        }
        override fun decrypt(protectedCredential: ProtectedCredential, aad: ByteArray): ByteArray {
            if (!available) throw CredentialCryptoUnavailableException()
            return delegate.decrypt(protectedCredential, aad)
        }
    }
}
