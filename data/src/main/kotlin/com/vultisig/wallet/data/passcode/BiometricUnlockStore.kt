package com.vultisig.wallet.data.passcode

import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import timber.log.Timber

/**
 * An optional shortcut past the passcode, never a replacement for it.
 *
 * Holds a second copy of the data-encryption key, encrypted under an AndroidKeyStore key that the
 * hardware releases only on a strong biometric match. The passcode-wrapped copy is untouched and
 * always works, so this can be removed, invalidated, or fail without the user losing access to
 * anything.
 *
 * **Everything here fails closed.** Every error — no hardware, nothing enrolled, a changed
 * enrolment, a lockout, a missing blob — resolves to null or false, and the caller falls back to
 * the passcode. A biometric lock that fails *open* is worse than no lock at all.
 *
 * The keystore key is generated with `setInvalidatedByBiometricEnrollment`, so enrolling a new face
 * or finger destroys it: someone who adds their own biometric to an unlocked device does not
 * thereby inherit access to the wallet. That is the Android counterpart of iOS's
 * `.biometryCurrentSet`.
 *
 * Unlike iOS, the stored copy carries no binding to the wrap it was made for. It does not need one:
 * the app is `allowBackup="false"` and both halves — the preference and the keystore key — go with
 * the app on uninstall, so no copy can outlive the data key it holds. Within one install the
 * invariant is [PasscodeRepository]'s to keep: the key is minted only in `setPasscode` and retired
 * only in `disablePasscode`, and both clear this store. A `changePasscode` rewrites the wrap but
 * keeps the same data key, so this copy stays valid across one — which is why nothing here has to
 * be re-encrypted behind a second prompt.
 */
internal interface BiometricUnlockStore {

    /** True when a biometric copy of the data key is stored and its keystore key still exists. */
    fun isEnabled(): Boolean

    /**
     * A cipher for [store], or null when this device cannot hold the copy — no hardware, nothing
     * enrolled, or a keystore that refused. Must be authenticated by the biometric prompt first.
     *
     * Mints a fresh keystore key on every call, so enabling twice cannot leave the previous copy
     * readable.
     */
    fun encryptCipherOrNull(): Cipher?

    /**
     * A cipher for [readDataKeyOrNull], or null when there is no copy to read or the key behind it
     * is gone. Must be authenticated by the biometric prompt first.
     */
    fun decryptCipherOrNull(): Cipher?

    /** Encrypts [dataKey] with an authenticated [cipher] and persists it. False when it failed. */
    fun store(dataKey: ByteArray, cipher: Cipher): Boolean

    /** Decrypts the stored copy with an authenticated [cipher], or null when it failed. */
    fun readDataKeyOrNull(cipher: Cipher): ByteArray?

    /** Removes the copy and the keystore key behind it. */
    fun clear()
}

internal class KeyStoreBiometricUnlockStore
@Inject
constructor(private val prefs: SharedPreferences) : BiometricUnlockStore {

    override fun isEnabled(): Boolean = readBlob() != null && loadKeyOrNull() != null

    override fun encryptCipherOrNull(): Cipher? =
        try {
            // Replaces whatever is there: generating over an existing alias overwrites it, so a
            // second enable cannot leave the first copy readable.
            val key = generateKey()
            // And drops the copy that key used to open, in the same breath. The blob is already
            // unreadable at this point, and a prompt the user then cancels would otherwise leave
            // an unreadable copy behind that isEnabled() — which asks whether the alias exists —
            // would go on reporting as a working shortcut.
            prefs.edit(commit = true) { remove(KEY_WRAPPED_KEY) }
            Cipher.getInstance(PasscodeCipher.AES_GCM_NO_PADDING).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
        } catch (e: GeneralSecurityException) {
            // The common one is "at least one biometric must be enrolled", thrown at generation
            // time. The caller has already asked BiometricManager why, so this only has to refuse.
            Timber.w(e, "Cannot mint a biometric unlock key on this device")
            null
        } catch (e: IllegalStateException) {
            // AndroidKeyStore raises this for a keystore that is not usable at all.
            Timber.w(e, "The keystore refused a biometric unlock key")
            null
        }

    override fun decryptCipherOrNull(): Cipher? {
        val blob = readBlob() ?: return null
        if (blob.size < PasscodeCipher.IV_LENGTH + PasscodeCipher.GCM_TAG_BYTES) {
            Timber.w("Discarding a biometric data key that is too short: %d bytes", blob.size)
            clear()
            return null
        }
        val key = loadKeyOrNull() ?: return null
        return try {
            Cipher.getInstance(PasscodeCipher.AES_GCM_NO_PADDING).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(PasscodeCipher.GCM_TAG_BITS, blob, 0, PasscodeCipher.IV_LENGTH),
                )
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            // The enrolled biometrics changed, so the key is gone for good. Clearing turns the
            // shortcut off rather than leaving a link that can only ever fail.
            Timber.i(e, "The biometric unlock key was invalidated by a new enrolment; removing it")
            clear()
            null
        } catch (e: GeneralSecurityException) {
            Timber.w(e, "Cannot prepare a biometric unlock")
            null
        }
    }

    override fun store(dataKey: ByteArray, cipher: Cipher): Boolean =
        try {
            // The IV is the cipher's own, generated when it was initialised for encryption, and it
            // is not secret — it is stored beside the ciphertext exactly as PasscodeCipher does.
            val blob = cipher.iv + cipher.doFinal(dataKey)
            prefs.edit(commit = true) { putString(KEY_WRAPPED_KEY, encode(blob)) }
            true
        } catch (e: GeneralSecurityException) {
            // Reached when the prompt's authentication did not actually cover this cipher.
            Timber.e(e, "Failed to store the biometric data key")
            clear()
            false
        } catch (e: IllegalStateException) {
            Timber.e(e, "Failed to store the biometric data key")
            clear()
            false
        }

    override fun readDataKeyOrNull(cipher: Cipher): ByteArray? {
        val blob = readBlob() ?: return null
        return try {
            val key =
                cipher.doFinal(blob, PasscodeCipher.IV_LENGTH, blob.size - PasscodeCipher.IV_LENGTH)
            if (key.size == PasscodeCipher.DATA_KEY_LENGTH) {
                key
            } else {
                // The match succeeded and what came back is not a data key. Nothing can be done
                // with it, and leaving it would fail the same way at every unlock.
                Timber.e("The biometric data key has an unexpected length: %d", key.size)
                key.fill(0)
                clear()
                null
            }
        } catch (e: GeneralSecurityException) {
            Timber.e(e, "Failed to read the biometric data key")
            null
        } catch (e: IllegalStateException) {
            Timber.e(e, "Failed to read the biometric data key")
            null
        }
    }

    override fun clear() {
        prefs.edit(commit = true) { remove(KEY_WRAPPED_KEY) }
        try {
            keyStoreOrNull()?.deleteEntry(KEY_ALIAS)
        } catch (e: GeneralSecurityException) {
            // The preference is gone, so isEnabled() already reads false and no unlock can be
            // attempted. An orphaned keystore entry is overwritten by the next enable.
            Timber.w(e, "Could not delete the biometric unlock key")
        }
    }

    private fun generateKey(): SecretKey {
        val spec =
            KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(PasscodeCipher.DATA_KEY_LENGTH * Byte.SIZE_BITS)
                // Refuses a caller-supplied IV, so the same key can never encrypt twice under one.
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(true)
                // Destroys the key when a biometric is added or removed, so a newly enrolled
                // fingerprint cannot open a wallet it was never shown to.
                .setInvalidatedByBiometricEnrollment(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Per use, and only a strong biometric: a timeout of zero means every
                        // operation carries its own authentication, and the device credential is
                        // deliberately not accepted — someone who knows the device PIN must not
                        // reach a wallet the app passcode exists to protect.
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    } else {
                        // The pre-R spelling of the same thing: -1 is per-use authentication, which
                        // on those versions accepts a strong biometric only.
                        @Suppress("DEPRECATION") setUserAuthenticationValidityDurationSeconds(-1)
                    }
                }
                .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply { init(spec) }
            .generateKey()
    }

    private fun loadKeyOrNull(): SecretKey? =
        try {
            keyStoreOrNull()?.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: UnrecoverableKeyException) {
            // The entry is there and cannot be used — the state a restored or corrupted keystore
            // leaves behind. Clearing keeps the shortcut from advertising itself as available.
            Timber.w(e, "The biometric unlock key is unusable; removing it")
            clear()
            null
        } catch (e: GeneralSecurityException) {
            Timber.w(e, "Could not read the biometric unlock key")
            null
        }

    /**
     * The keystore itself, or null when it will not open.
     *
     * `load` declares [IOException], which is not a [GeneralSecurityException] and so would
     * otherwise travel up through [clear] and [isEnabled] into the repository's `setPasscode` and
     * `disablePasscode`, neither of which catches anything — a failure that breaks setting a
     * passcode at all, for a shortcut that is optional. Refusing here keeps it fail-closed like
     * everything else in this file.
     */
    private fun keyStoreOrNull(): KeyStore? =
        try {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        } catch (e: GeneralSecurityException) {
            Timber.w(e, "Could not open the keystore")
            null
        } catch (e: IOException) {
            Timber.w(e, "Could not open the keystore")
            null
        }

    private fun readBlob(): ByteArray? =
        prefs.getString(KEY_WRAPPED_KEY, null)?.let { encoded ->
            try {
                Base64.getDecoder().decode(encoded)
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Discarding a malformed biometric data key")
                null
            }
        }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /**
     * Internal for the same reason as [SharedPreferencesPasscodeStore]'s: a rename is a data loss.
     */
    internal companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "vultisig_passcode_biometric_key"
        const val KEY_WRAPPED_KEY = "passcode_biometric_wrapped_data_key"
    }
}
