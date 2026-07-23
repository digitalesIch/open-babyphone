package org.openbabyphone

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
class TrustedChildStoreTest {
    private lateinit var context: Application
    private lateinit var crypto: TrustedCredentialCrypto

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as Application
        context.getSharedPreferences(TrustedChildStore.METADATA_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().clear().commit()
        credentialsPrefs().edit().clear().commit()
        PendingConnections.store.clear()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        crypto = AesGcmTrustedCredentialCrypto({ key })
    }

    @Test
    fun `authenticated creation stores wrapped credential outside metadata`() {
        val store = newStore()

        assertEquals(CredentialStorageResult.Success, trust(store, "child1", "pair1", "code1234"))

        assertFalse(metadataJson().contains("pairingCode"))
        assertFalse(credentialsPrefs().all.values.joinToString().contains("code1234"))
        assertEquals("code1234", resolvedCode(store, "child1", "pair1"))
    }

    @Test
    fun `legacy plaintext migrates exactly once and rewrites metadata`() {
        writeLegacyMetadata("child1", "pair1", "code1234")
        var encryptions = 0
        val counting = object : TrustedCredentialCrypto {
            override fun encrypt(plaintext: ByteArray, aad: ByteArray): ProtectedCredential {
                encryptions++
                return crypto.encrypt(plaintext, aad)
            }
            override fun decrypt(protectedCredential: ProtectedCredential, aad: ByteArray): ByteArray =
                crypto.decrypt(protectedCredential, aad)
        }

        val first = TrustedChildStore(context, counting)
        val second = TrustedChildStore(context, counting)

        assertEquals("code1234", resolvedCode(first, "child1", "pair1"))
        assertEquals(1, second.getAll().size)
        assertEquals(1, encryptions)
        assertFalse(metadataJson().contains("pairingCode"))
    }

    @Test
    fun `unavailable migration drops legacy plaintext instead of retaining fallback`() {
        writeLegacyMetadata("child1", "pair1", "code1234")

        val store = TrustedChildStore(context, UnavailableCrypto())

        assertTrue(store.getAll().isEmpty())
        assertFalse(metadataJson().contains("code1234"))
    }

    @Test
    fun `corrupt credential removes exact profile`() {
        val store = newStore()
        trust(store, "child1", "pair1", "code1234")
        credentialsPrefs().edit().putString(credentialsPrefs().all.keys.single(), "not-json").commit()

        assertEquals(TrustedConnectionResult.Missing, store.resolveConnection("child1", "pair1"))
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun `temporarily unavailable credential preserves profile and ciphertext`() {
        val switchable = SwitchableCrypto(crypto)
        val store = TrustedChildStore(context, switchable)
        assertEquals(CredentialStorageResult.Success, trust(store, "child1", "pair1", "code1234"))
        val ciphertext = credentialsPrefs().all.toMap()
        switchable.available = false

        assertEquals(TrustedConnectionResult.Unavailable, store.resolveConnection("child1", "pair1"))
        assertEquals(1, store.getAll().size)
        assertEquals(ciphertext, credentialsPrefs().all)
    }

    @Test
    fun `two live instances merge updates without stale snapshot loss`() {
        val first = newStore()
        val second = newStore()

        trust(first, "child1", "pair1", "code1234")
        trust(second, "child2", "pair2", "code5678")

        assertEquals(setOf("child1", "child2"), first.getAll().map { it.childId }.toSet())
        assertEquals(setOf("child1", "child2"), second.getAll().map { it.childId }.toSet())
    }

    @Test
    fun `two live instances forget one child without deleting another update`() {
        val first = newStore()
        val second = newStore()
        trust(first, "child1", "pair1", "code1234")
        trust(second, "child2", "pair2", "code5678")

        assertTrue(first.forget("child1"))

        assertNull(first.findById("child1"))
        assertEquals("code5678", resolvedCode(second, "child2", "pair2"))
    }

    @Test
    fun `constructor reconciles metadata written by an existing instance`() {
        val first = newStore()
        trust(first, "child1", "pair1", "code1234")

        val constructedAfterWrite = newStore()

        assertEquals("code1234", resolvedCode(constructedAfterWrite, "child1", "pair1"))
    }

    @Test
    fun `old generation lookup cannot delete newly rotated profile`() {
        val first = newStore()
        val second = newStore()
        trust(first, "child1", "pair1", "code1234")
        trust(second, "child1", "pair2", "newcode8")

        assertEquals(TrustedConnectionResult.Missing, first.resolveConnection("child1", "pair1"))
        assertEquals("newcode8", resolvedCode(second, "child1", "pair2"))
    }

    @Test
    fun `failed reset leaves old generation usable`() {
        val switchable = SwitchableCrypto(crypto)
        val store = TrustedChildStore(context, switchable)
        trust(store, "child1", "pair1", "code1234")
        switchable.available = false

        assertEquals(CredentialStorageResult.Unavailable, trust(store, "child1", "pair2", "newcode8"))
        switchable.available = true

        assertEquals("code1234", resolvedCode(store, "child1", "pair1"))
    }

    @Test
    fun `forget and clear durably remove credentials and pending requests`() {
        val store = newStore()
        trust(store, "child1", "pair1", "code1234")
        val requestId = PendingConnections.store.put(
            PendingConnection("host", 10000, "Nursery", null, "child1", "pair1")
        )

        assertTrue(store.forget("child1"))
        assertFalse(PendingConnections.store.contains(requestId))
        assertTrue(credentialsPrefs().all.isEmpty())

        trust(store, "child2", "pair2", "code5678")
        assertTrue(store.clear())
        assertTrue(store.getAll().isEmpty())
        assertTrue(credentialsPrefs().all.isEmpty())
    }

    @Test
    fun `forget revokes matching active listen session`() {
        val store = newStore()
        trust(store, "child1", "pair1", "code1234")
        ActiveListenSessionRegistry.register(ExpectedChildIdentity("child1", "pair1"))

        assertTrue(store.forget("child1"))

        assertEquals(ListenService::class.java.name, shadowOf(context).nextStoppedService.component?.className)
    }

    private fun newStore() = TrustedChildStore(context, crypto)

    private fun trust(
        store: TrustedChildStore,
        childId: String,
        pairingId: String,
        code: String
    ): CredentialStorageResult {
        val chars = code.toCharArray()
        return try {
            store.trustAuthenticated(childId, pairingId, childId, chars, "host", 10000)
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun resolvedCode(store: TrustedChildStore, childId: String, pairingId: String): String? {
        val result = store.resolveConnection(childId, pairingId) as? TrustedConnectionResult.Available
            ?: return null
        return try {
            result.pairingCode.concatToString()
        } finally {
            result.pairingCode.fill('\u0000')
        }
    }

    private fun credentialsPrefs() = context.getSharedPreferences(
        ProtectedTrustedCredentialStore.PREFS_NAME,
        Application.MODE_PRIVATE
    )

    private fun metadataJson(): String = context.getSharedPreferences(
        TrustedChildStore.METADATA_PREFS_NAME,
        Application.MODE_PRIVATE
    ).getString(TrustedChildStore.KEY_TRUSTED_CHILDREN, null).orEmpty()

    private fun writeLegacyMetadata(childId: String, pairingId: String, code: String) {
        val value = JSONArray().put(
            JSONObject().put("childId", childId).put("pairingId", pairingId)
                .put("displayName", "Nursery").put("pairingCode", code)
                .put("lastKnownAddress", JSONObject.NULL).put("lastKnownPort", JSONObject.NULL)
                .put("lastSeenAt", 0)
        )
        context.getSharedPreferences(TrustedChildStore.METADATA_PREFS_NAME, Application.MODE_PRIVATE)
            .edit().putString(TrustedChildStore.KEY_TRUSTED_CHILDREN, value.toString()).commit()
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
