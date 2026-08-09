package com.vultisig.wallet.data.passcode

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import timber.log.Timber

/** Number of digits in an app passcode. */
const val PASSCODE_LENGTH = 6

/**
 * Derives a key-encryption key (KEK) from the user's passcode and uses it to wrap the random
 * data-encryption key (DEK) that protects local vault storage.
 *
 * The passcode never encrypts vault data directly: a random 256-bit DEK does, and only that DEK is
 * wrapped by the passcode. Changing the passcode therefore rewraps a few dozen bytes instead of
 * re-encrypting every keyshare, and the DEK can additionally be wrapped under a biometric-gated
 * AndroidKeyStore key without a second copy of the plaintext data.
 *
 * AES-256-GCM authenticates the wrap, so [unwrap] returning null *is* the passcode check — there is
 * no separate verifier to keep in sync.
 */
internal class PasscodeCipher @Inject constructor() {

    // SecureRandom is documented as thread-safe; Cipher and SecretKeyFactory are not, so they are
    // created per call. This class is a @Singleton reached from unlock and from the manage flows.
    private val random = SecureRandom()

    /** Returns a fresh random salt for [wrap]/[unwrap] key derivation. */
    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH).also(random::nextBytes)

    /** Returns a fresh random 256-bit data-encryption key. */
    fun newDataKey(): ByteArray = ByteArray(DATA_KEY_LENGTH).also(random::nextBytes)

    /** Wraps [dataKey] under the key derived from [passcode] and [salt]; returns `IV || ct`. */
    fun wrap(dataKey: ByteArray, passcode: String, salt: ByteArray): ByteArray {
        val kek = deriveKey(passcode, salt)
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv + cipher.doFinal(dataKey)
    }

    /**
     * Unwraps a blob produced by [wrap], returning the data-encryption key, or null when [passcode]
     * is wrong or the blob is damaged. A null return is not distinguishable between those two cases
     * by design — callers surface a single "wrong passcode" outcome either way.
     */
    fun unwrap(blob: ByteArray, passcode: String, salt: ByteArray): ByteArray? {
        if (blob.size < IV_LENGTH + GCM_TAG_BYTES) {
            Timber.w("Wrapped passcode key is too short: %d bytes", blob.size)
            return null
        }
        val kek = deriveKey(passcode, salt)
        return try {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(
                Cipher.DECRYPT_MODE,
                kek,
                GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_LENGTH),
            )
            cipher.doFinal(blob, IV_LENGTH, blob.size - IV_LENGTH)
        } catch (e: GeneralSecurityException) {
            // Expected on every wrong-passcode attempt; logged at debug so a user fat-fingering
            // their passcode does not read as an error in the wild.
            Timber.d(e, "Failed to unwrap passcode key")
            null
        }
    }

    /**
     * Derives the key-encryption key from [passcode] and [salt].
     *
     * What can be wiped, is: the passcode's char array, the [PBEKeySpec]'s copy of it, and the
     * array PBKDF2 returns, all zeroed before this returns. What cannot is the copy [SecretKeySpec]
     * takes on construction — `destroy()` is unsupported on Android and `getEncoded()` hands back a
     * clone, so no reference reaches it. Short-lived and unreachable is the most this can offer;
     * the key is not held beyond the single wrap or unwrap that needs it.
     */
    private fun deriveKey(passcode: String, salt: ByteArray): SecretKeySpec {
        val passcodeChars = passcode.toCharArray()
        val spec =
            PBEKeySpec(passcodeChars, salt, PBKDF2_ITERATIONS, DATA_KEY_LENGTH * Byte.SIZE_BITS)
        try {
            val derived =
                SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
            return SecretKeySpec(derived, AES).also { derived.fill(0) }
        } finally {
            spec.clearPassword()
            passcodeChars.fill('\u0000')
        }
    }

    // Internal rather than private so the KDF cost is pinned by a test: it is the only thing
    // standing between a stolen preferences file and an offline guess at a 6-digit passcode, and a
    // well-meaning "unlock feels slow" tweak would otherwise weaken it silently.
    internal companion object {
        const val AES = "AES"
        const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"

        const val SALT_LENGTH = 16
        const val IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / Byte.SIZE_BITS
        const val DATA_KEY_LENGTH = 32

        /**
         * Below OWASP's recommendation for this PRF, deliberately. Their figure for
         * PBKDF2-HMAC-SHA256 is 600k — which is what exported vault backups use
         * ([com.vultisig.wallet.data.usecases.Pbkdf2AesEncryption]) — and 210k is the number they
         * give for PBKDF2-HMAC-SHA512, a costlier PRF per iteration.
         *
         * A backup file travels off the device and faces unlimited offline guessing, so it buys
         * every iteration it can afford. The wrapped passcode key never leaves the device: it is
         * stored inside the AndroidKeyStore-encrypted preferences, so reaching it already requires
         * defeating hardware-backed key storage, and online guessing is rate-limited by
         * [PasscodeLockout]. What is bought instead is an unlock that stays responsive on low-end
         * devices, which matters when auto-lock can fire after a minute in the background.
         */
        const val PBKDF2_ITERATIONS = 210_000
    }
}
