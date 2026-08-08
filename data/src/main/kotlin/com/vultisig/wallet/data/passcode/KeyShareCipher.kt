package com.vultisig.wallet.data.passcode

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import timber.log.Timber

/**
 * Identifies the row a keyshare ciphertext belongs to. Bound into the GCM tag as additional
 * authenticated data, so a ciphertext lifted from one row and pasted into another fails to decrypt
 * instead of silently handing back the wrong vault's share.
 */
internal data class KeyShareIdentity(val vaultId: String, val pubKey: String) {
    /**
     * Canonical bytes for the AAD. Length-prefixed rather than joined by a separator, so the
     * encoding is injective whatever the fields contain: no two distinct rows can produce the same
     * bytes, which a bare delimiter cannot promise once that delimiter appears inside a field.
     */
    fun toAad(): ByteArray =
        "${vaultId.length}:$vaultId:${pubKey.length}:$pubKey".toByteArray(Charsets.UTF_8)
}

/**
 * Encrypts individual vault keyshares under the passcode-derived data key.
 *
 * Ciphertext is tagged with [PREFIX] so plaintext and encrypted rows can coexist in the same table.
 * That matters twice over: users who never set a passcode keep plaintext rows forever, and a bulk
 * migration killed halfway through leaves a mix that the next launch still reads correctly.
 *
 * The key is passed per call rather than held, so this stays a pure function of its inputs and does
 * not need to observe lock state.
 */
internal class KeyShareCipher @Inject constructor() {

    // SecureRandom is thread-safe; Cipher is not, so it is created per call.
    private val random = SecureRandom()

    /** True when [stored] was written by [encrypt] rather than stored in the clear. */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    /**
     * Returns [plaintext] encrypted under [dataKey] and bound to [identity], tagged with [PREFIX].
     */
    fun encrypt(plaintext: String, dataKey: ByteArray, identity: KeyShareIdentity): String {
        val key = SecretKeySpec(dataKey, AES)
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(identity.toAad())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    /**
     * Returns the plaintext for [stored]: unchanged when it was never encrypted, decrypted under
     * [dataKey] when it was, or null when it is encrypted and [dataKey] cannot open it under
     * [identity] — a wrong key, a damaged blob, or a row it was not written for.
     */
    fun decrypt(stored: String, dataKey: ByteArray?, identity: KeyShareIdentity): String? {
        if (!isEncrypted(stored)) return stored
        if (dataKey == null) return null
        return try {
            val blob = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
            require(blob.size > IV_LENGTH) { "Encrypted keyshare is too short: ${blob.size}" }
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(dataKey, AES),
                GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_LENGTH),
            )
            cipher.updateAAD(identity.toAad())
            String(cipher.doFinal(blob, IV_LENGTH, blob.size - IV_LENGTH), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            Timber.e(e, "Failed to decrypt keyshare")
            null
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Malformed encrypted keyshare")
            null
        }
    }

    private companion object {
        const val AES = "AES"
        const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val GCM_TAG_BITS = 128

        /**
         * Marks a keyshare as encrypted. Not valid base64url, so it cannot collide with a share.
         */
        const val PREFIX = "vlpc1:"
    }
}
