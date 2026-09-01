package com.vultisig.wallet.data.passcode

import android.content.SharedPreferences
import com.vultisig.wallet.data.utils.InMemorySharedPreferences
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
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

    private fun dataKey() = ByteArray(PasscodeCipher.DATA_KEY_LENGTH) { it.toByte() }

    /**
     * A cipher the way the biometric prompt hands one back — authorised and initialised for
     * encryption. The key is an ordinary JCE one; the store only ever asks it for an IV and a
     * `doFinal`.
     */
    private fun encryptCipher(): Cipher {
        val key =
            KeyGenerator.getInstance("AES")
                .apply { init(PasscodeCipher.DATA_KEY_LENGTH * Byte.SIZE_BITS) }
                .generateKey()
        return Cipher.getInstance(PasscodeCipher.AES_GCM_NO_PADDING).apply {
            init(Cipher.ENCRYPT_MODE, key)
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
