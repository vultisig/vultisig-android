package com.vultisig.wallet.data.passcode

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Base64
import kotlin.test.assertContentEquals
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
        store = SharedPreferencesPasscodeStore(prefs)
    }

    private fun storedAs(salt: String?, wrapped: String?) {
        every { prefs.getString(KEY_SALT, null) } returns salt
        every { prefs.getString(KEY_WRAPPED_KEY, null) } returns wrapped
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    @Test
    fun `both halves present decode into credentials`() {
        val salt = ByteArray(16) { it.toByte() }
        val wrapped = ByteArray(60) { (it * 3).toByte() }
        storedAs(encode(salt), encode(wrapped))

        val credentials = store.readCredentials()

        assertNotNull(credentials)
        assertContentEquals(salt, credentials.salt)
        assertContentEquals(wrapped, credentials.wrappedDataKey)
        verify(exactly = 0) { editor.remove(any()) }
    }

    @Test
    fun `nothing stored reads as no passcode without touching the store`() {
        storedAs(null, null)

        assertNull(store.readCredentials())

        // No half to clean up, so a read of an untouched install must not write anything.
        verify(exactly = 0) { editor.remove(any()) }
    }

    @Test
    fun `a stray salt with no wrapped key is discarded`() {
        // A torn write. Leaving the salt behind risks a later write pairing it with a key it does
        // not belong to, so the read drops both halves.
        storedAs(encode(ByteArray(16)), null)

        assertNull(store.readCredentials())

        verify { editor.remove(KEY_SALT) }
        verify { editor.remove(KEY_WRAPPED_KEY) }
        // Staging the removals on a relaxed editor mock proves nothing on its own — without the
        // apply() the half-credential survives the process and the next read repeats this.
        verify { editor.apply() }
    }

    @Test
    fun `a wrapped key with no salt is discarded`() {
        storedAs(null, encode(ByteArray(60)))

        assertNull(store.readCredentials())

        verify { editor.remove(KEY_SALT) }
        verify { editor.remove(KEY_WRAPPED_KEY) }
        // Staging the removals on a relaxed editor mock proves nothing on its own — without the
        // apply() the half-credential survives the process and the next read repeats this.
        verify { editor.apply() }
    }

    @Test
    fun `an undecodable half is discarded like a missing one`() {
        storedAs(encode(ByteArray(16)), "not-base64!!")

        assertNull(store.readCredentials())

        verify { editor.remove(KEY_SALT) }
        verify { editor.remove(KEY_WRAPPED_KEY) }
        // Staging the removals on a relaxed editor mock proves nothing on its own — without the
        // apply() the half-credential survives the process and the next read repeats this.
        verify { editor.apply() }
    }

    private companion object {
        // Deliberately the production constants, not copies: a local copy keeps passing after a
        // rename in the store, which is exactly the change that would strand every user's
        // credentials on upgrade.
        const val KEY_SALT = SharedPreferencesPasscodeStore.KEY_SALT
        const val KEY_WRAPPED_KEY = SharedPreferencesPasscodeStore.KEY_WRAPPED_KEY
    }
}
