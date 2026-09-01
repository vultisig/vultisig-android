package com.vultisig.wallet.data.passcode

import android.content.SharedPreferences
import com.vultisig.wallet.data.utils.InMemorySharedPreferences
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the half of [KeyStoreBiometricUnlockStore] that does not need a keystore.
 *
 * Minting and reading the key are AndroidKeyStore's, and belong on a device. What is left — whether
 * a copy actually reached the disk before the store says it did — is the part that decides what the
 * settings switch shows, and it answers on the JVM.
 */
internal class KeyStoreBiometricUnlockStoreTest {

    @Test
    fun `a stored copy is reported as stored and can be read back`() {
        val prefs = InMemorySharedPreferences()

        assertTrue(KeyStoreBiometricUnlockStore(prefs).store(dataKey(), encryptCipher()))

        assertNotNull(prefs.getString(KEY_WRAPPED_KEY, null))
    }

    @Test
    fun `a copy the disk refused is reported rather than passed off as stored`() {
        // commit() updates the in-memory map before it writes the file and leaves it updated when
        // that write fails, so a store that ignores the answer reports success *and* goes on
        // reading the copy back: the switch shows ON until the process restarts and the shortcut
        // turns out never to have existed.
        val prefs = RefusingPreferences(InMemorySharedPreferences())

        assertFalse(KeyStoreBiometricUnlockStore(prefs).store(dataKey(), encryptCipher()))

        // And the map is put back, so nothing reads as enabled in the meantime either.
        assertNull(prefs.getString(KEY_WRAPPED_KEY, null))
    }

    @Test
    fun `a copy is read back with the key it was made under`() {
        val prefs = InMemorySharedPreferences()
        val key = aesKey()
        val store = KeyStoreBiometricUnlockStore(prefs)
        store.store(dataKey(), encryptCipher(key))

        assertContentEquals(dataKey(), store.readDataKeyOrNull(decryptCipher(prefs, key)))

        // And is still there afterwards: the shortcut survives being used.
        assertNotNull(prefs.getString(KEY_WRAPPED_KEY, null))
    }

    @Test
    fun `a copy made under another key is dropped rather than left to fail at every unlock`() {
        // What a copy that outlived its alias meets: the tag says this ciphertext was never made
        // under the key that just opened it, and no retry can change that. Leaving it would keep
        // isEnabled() — which asks whether the alias exists — offering a shortcut that only fails.
        val prefs = InMemorySharedPreferences()
        val store = KeyStoreBiometricUnlockStore(prefs)
        store.store(dataKey(), encryptCipher(aesKey()))

        assertNull(store.readDataKeyOrNull(decryptCipher(prefs, aesKey())))

        assertNull(prefs.getString(KEY_WRAPPED_KEY, null))
    }

    private fun dataKey() = ByteArray(PasscodeCipher.DATA_KEY_LENGTH) { it.toByte() }

    private fun aesKey(): SecretKey =
        KeyGenerator.getInstance("AES")
            .apply { init(PasscodeCipher.DATA_KEY_LENGTH * Byte.SIZE_BITS) }
            .generateKey()

    /**
     * A cipher the way the biometric prompt hands one back — authorised and initialised for
     * encryption. The key is an ordinary JCE one; the store only ever asks it for an IV and a
     * `doFinal`.
     */
    private fun encryptCipher(key: SecretKey = aesKey()): Cipher =
        Cipher.getInstance(PasscodeCipher.AES_GCM_NO_PADDING).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }

    /**
     * A cipher over the stored copy's own IV, the way `decryptCipherOrNull` builds one — with
     * whichever [key] the caller wants to stand in for the alias behind it.
     */
    private fun decryptCipher(prefs: SharedPreferences, key: SecretKey): Cipher {
        val blob = Base64.getDecoder().decode(prefs.getString(KEY_WRAPPED_KEY, null))
        return Cipher.getInstance(PasscodeCipher.AES_GCM_NO_PADDING).apply {
            init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(PasscodeCipher.GCM_TAG_BITS, blob, 0, PasscodeCipher.IV_LENGTH),
            )
        }
    }

    /**
     * Preferences that apply an edit to memory and then report the disk write as refused, the way
     * `SharedPreferencesImpl.commit` does when the file cannot be written.
     */
    private class RefusingPreferences(private val delegate: SharedPreferences) :
        SharedPreferences by delegate {
        override fun edit(): SharedPreferences.Editor = RefusingEditor(delegate.edit())
    }

    /** The mutators return `this`, so a chained call cannot escape back to the real editor. */
    private class RefusingEditor(private val editor: SharedPreferences.Editor) :
        SharedPreferences.Editor by editor {
        override fun putString(key: String, value: String?) = also { editor.putString(key, value) }

        override fun remove(key: String) = also { editor.remove(key) }

        override fun apply() = editor.apply()

        override fun commit(): Boolean {
            editor.commit()
            return false
        }
    }

    private companion object {
        // Deliberately the production constant, not a copy: a local one keeps passing after a
        // rename, which is the change that would strand every user's stored copy on upgrade.
        const val KEY_WRAPPED_KEY = KeyStoreBiometricUnlockStore.KEY_WRAPPED_KEY
    }
}
