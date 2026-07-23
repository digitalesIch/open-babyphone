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

import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest

internal sealed interface CredentialReadResult {
    data class Available(val value: CharArray) : CredentialReadResult
    data object Missing : CredentialReadResult
    data object Corrupt : CredentialReadResult
    data object Unavailable : CredentialReadResult
}

internal enum class CredentialWriteResult {
    Success,
    Unavailable,
    Failed
}

internal class ProtectedTrustedCredentialStore(
    private val prefs: SharedPreferences,
    private val crypto: TrustedCredentialCrypto
) {
    fun put(childId: String, pairingId: String, pairingCode: CharArray): CredentialWriteResult {
        val normalized = PairingCode.normalize(pairingCode.concatToString())
        if (!PairingCode.isValid(normalized)) return CredentialWriteResult.Failed
        val plaintext = normalized.toByteArray(Charsets.UTF_8)
        return try {
            val protected = crypto.encrypt(plaintext, aad(childId, pairingId))
            if (protected.nonce.size != AesGcmTrustedCredentialCrypto.NONCE_SIZE_BYTES) {
                CredentialWriteResult.Failed
            } else {
                val record = JSONObject()
                    .put("version", FORMAT_VERSION)
                    .put("nonce", Base64.encodeToString(protected.nonce, BASE64_FLAGS))
                    .put("ciphertext", Base64.encodeToString(protected.ciphertext, BASE64_FLAGS))
                if (prefs.edit().putString(key(childId, pairingId), record.toString()).commit()) {
                    CredentialWriteResult.Success
                } else {
                    CredentialWriteResult.Failed
                }
            }
        } catch (_: CredentialCryptoUnavailableException) {
            CredentialWriteResult.Unavailable
        } catch (_: Exception) {
            CredentialWriteResult.Failed
        } finally {
            plaintext.fill(0)
        }
    }

    fun get(childId: String, pairingId: String): CredentialReadResult {
        val raw = prefs.getString(key(childId, pairingId), null) ?: return CredentialReadResult.Missing
        var plaintext: ByteArray? = null
        return try {
            val record = JSONObject(raw)
            if (record.getInt("version") != FORMAT_VERSION) return CredentialReadResult.Corrupt
            val nonce = Base64.decode(record.getString("nonce"), BASE64_FLAGS)
            val ciphertext = Base64.decode(record.getString("ciphertext"), BASE64_FLAGS)
            if (nonce.size != AesGcmTrustedCredentialCrypto.NONCE_SIZE_BYTES) {
                return CredentialReadResult.Corrupt
            }
            plaintext = crypto.decrypt(ProtectedCredential(nonce, ciphertext), aad(childId, pairingId))
            val normalized = PairingCode.normalize(plaintext.toString(Charsets.UTF_8))
            if (PairingCode.isValid(normalized)) {
                CredentialReadResult.Available(normalized.toCharArray())
            } else {
                CredentialReadResult.Corrupt
            }
        } catch (_: CredentialCryptoUnavailableException) {
            CredentialReadResult.Unavailable
        } catch (_: Exception) {
            CredentialReadResult.Corrupt
        } finally {
            plaintext?.fill(0)
        }
    }

    fun remove(childId: String, pairingId: String): Boolean =
        prefs.edit().remove(key(childId, pairingId)).commit()

    fun clear(): Boolean = prefs.all.isEmpty() || prefs.edit().clear().commit()

    fun retain(identities: Collection<Pair<String, String>>): Boolean {
        val retainedKeys = identities.mapTo(mutableSetOf()) { (childId, pairingId) -> key(childId, pairingId) }
        val staleKeys = prefs.all.keys.filterNot(retainedKeys::contains)
        if (staleKeys.isEmpty()) return true
        val editor = prefs.edit()
        staleKeys.forEach(editor::remove)
        return editor.commit()
    }

    companion object {
        const val PREFS_NAME = "trusted_child_credentials"
        private const val FORMAT_VERSION = 1
        private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE

        internal fun aad(childId: String, pairingId: String): ByteArray =
            "$childId\u0000$pairingId".toByteArray(Charsets.UTF_8)

        private fun key(childId: String, pairingId: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(aad(childId, pairingId))
            return Base64.encodeToString(digest, BASE64_FLAGS)
        }
    }
}
