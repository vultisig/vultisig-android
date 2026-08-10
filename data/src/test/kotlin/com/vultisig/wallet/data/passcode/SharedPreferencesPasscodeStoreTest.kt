package com.vultisig.wallet.data.passcode

import android.content.SharedPreferences
import com.vultisig.wallet.data.utils.InMemorySharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [SharedPreferencesPasscodeStore]. */
internal class SharedPreferencesPasscodeStoreTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var store: SharedPreferencesPasscodeStore

    @BeforeEach
    fun setUp() {
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.commit() } returns true
        store = SharedPreferencesPasscodeStore(prefs)
    }

    private fun storedAs(salt: String?, wrapped: String?) {
        every { prefs.getString(KEY_SALT, null) } returns salt
        every { prefs.getString(KEY_WRAPPED_KEY, null) } returns wrapped
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun assertReadLeavesTheStoreAlone(salt: String?, wrapped: String?) {
        storedAs(salt, wrapped)

        assertNull(store.readCredentials())

        verify(exactly = 0) { prefs.edit() }
    }

    @Test
    fun `both halves present decode into credentials`() {
        val salt = ByteArray(16) { it.toByte() }
        val wrapped = ByteArray(60) { (it * 3).toByte() }
        storedAs(encode(salt), encode(wrapped))

        val credentials = store.readCredentials()

        assertNotNull(credentials)
        assertContentEquals(salt, credentials.salt)
        assertContentEquals(wrapped, credentials.wrappedDataKey)
        verify(exactly = 0) { prefs.edit() }
    }

    @Test
    fun `nothing stored reads as no passcode`() {
        assertReadLeavesTheStoreAlone(salt = null, wrapped = null)
    }

    @Test
    fun `a salt whose partner does not read back is left in place`() {
        assertReadLeavesTheStoreAlone(salt = encode(ByteArray(16)), wrapped = null)
    }

    @Test
    fun `a wrapped key whose partner does not read back is left in place`() {
        assertReadLeavesTheStoreAlone(salt = null, wrapped = encode(ByteArray(60)))
    }

    @Test
    fun `an undecodable half is left in place`() {
        assertReadLeavesTheStoreAlone(salt = encode(ByteArray(16)), wrapped = "not-base64!!")
    }

    @Test
    fun `a half that did not decrypt on one read still opens on the next`() {
        // Over a store that remembers what it was given, so a read that deleted a half would show
        // up as a wrap that is no longer there once the fault clears.
        val flakyPrefs = FlakyPreferences(InMemorySharedPreferences(), KEY_WRAPPED_KEY)
        val flakyStore = SharedPreferencesPasscodeStore(flakyPrefs)
        val credentials =
            PasscodeCredentials(
                salt = ByteArray(16) { it.toByte() },
                wrappedDataKey = ByteArray(60) { (it * 3).toByte() },
            )
        flakyStore.writeCredentials(credentials)

        flakyPrefs.failing = true

        assertNull(flakyStore.readCredentials())

        flakyPrefs.failing = false

        assertEquals(credentials, flakyStore.readCredentials())
    }

    @Test
    fun `writing credentials commits both halves before returning`() {
        val salt = ByteArray(16) { it.toByte() }
        val wrapped = ByteArray(60) { (it * 3).toByte() }

        store.writeCredentials(PasscodeCredentials(salt = salt, wrappedDataKey = wrapped))

        verify { editor.putString(KEY_SALT, encode(salt)) }
        verify { editor.putString(KEY_WRAPPED_KEY, encode(wrapped)) }
        // An apply() would let a process kill drop the wrap while the keyshare ciphertext
        // setPasscode writes next survives.
        verify { editor.commit() }
        verify(exactly = 0) { editor.apply() }
    }

    @Test
    fun `clearing credentials commits both removals before returning`() {
        store.clearCredentials()

        verify { editor.remove(KEY_SALT) }
        verify { editor.remove(KEY_WRAPPED_KEY) }
        verify { editor.commit() }
        verify(exactly = 0) { editor.apply() }
    }

    @Test
    fun `a write the disk refused is reported, not passed off as stored`() {
        every { editor.commit() } returns false

        assertFailsWith<IllegalStateException> {
            store.writeCredentials(
                PasscodeCredentials(salt = ByteArray(16), wrappedDataKey = ByteArray(60))
            )
        }
    }

    @Test
    fun `a clear the disk refused is reported, not passed off as removed`() {
        every { editor.commit() } returns false

        assertFailsWith<IllegalStateException> { store.clearCredentials() }
    }

    /**
     * Preferences whose reads of [failingKey] come back empty while [failing] is set, standing in
     * for a keystore that decrypts one stored value this launch but not the other.
     */
    private class FlakyPreferences(
        private val delegate: SharedPreferences,
        private val failingKey: String,
    ) : SharedPreferences by delegate {
        var failing = false

        override fun getString(key: String, defValue: String?): String? =
            if (failing && key == failingKey) defValue else delegate.getString(key, defValue)
    }

    private companion object {
        // Deliberately the production constants, not copies: a local copy keeps passing after a
        // rename in the store, which is exactly the change that would strand every user's
        // credentials on upgrade.
        const val KEY_SALT = SharedPreferencesPasscodeStore.KEY_SALT
        const val KEY_WRAPPED_KEY = SharedPreferencesPasscodeStore.KEY_WRAPPED_KEY
    }
}
