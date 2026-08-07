package com.vultisig.wallet.data.passcode

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Base64
import javax.inject.Inject
import timber.log.Timber

/**
 * The passcode-derived material persisted between launches: the KDF [salt] and the [wrappedDataKey]
 * it protects. Neither is secret on its own — the passcode is the missing factor — but both are
 * stored in the AndroidKeyStore-encrypted preferences anyway so that an attacker with the raw
 * preference file still cannot mount an offline guessing attack.
 */
internal class PasscodeCredentials(val salt: ByteArray, val wrappedDataKey: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is PasscodeCredentials &&
                salt.contentEquals(other.salt) &&
                wrappedDataKey.contentEquals(other.wrappedDataKey))

    override fun hashCode(): Int = 31 * salt.contentHashCode() + wrappedDataKey.contentHashCode()
}

/** Persistence seam for passcode material, kept narrow so tests can swap in an in-memory map. */
internal interface PasscodeStore {
    fun readCredentials(): PasscodeCredentials?

    fun writeCredentials(credentials: PasscodeCredentials)

    fun clearCredentials()

    fun readLockout(): PasscodeLockoutState

    fun writeLockout(state: PasscodeLockoutState)
}

/**
 * [PasscodeStore] backed by the app's AndroidKeyStore-encrypted [SharedPreferences]. Byte arrays
 * are Base64-encoded with [java.util.Base64] (API 26+, so it also runs unchanged under plain JVM
 * unit tests) because the underlying preferences store strings.
 */
internal class SharedPreferencesPasscodeStore
@Inject
constructor(private val prefs: SharedPreferences) : PasscodeStore {

    override fun readCredentials(): PasscodeCredentials? {
        val salt = prefs.getString(KEY_SALT, null)?.let(::decodeOrNull)
        val wrapped = prefs.getString(KEY_WRAPPED_KEY, null)?.let(::decodeOrNull)
        if (salt == null || wrapped == null) {
            // Half a credential is not a passcode, it is a torn write or a corrupted store. Drop
            // the remaining half so the app reports "no passcode" consistently rather than leaving
            // a stray salt that a later write would pair with a mismatched key.
            if (salt != null || wrapped != null) clearCredentials()
            return null
        }
        return PasscodeCredentials(salt = salt, wrappedDataKey = wrapped)
    }

    override fun writeCredentials(credentials: PasscodeCredentials) {
        prefs.edit {
            putString(KEY_SALT, encode(credentials.salt))
            putString(KEY_WRAPPED_KEY, encode(credentials.wrappedDataKey))
        }
    }

    override fun clearCredentials() {
        prefs.edit {
            remove(KEY_SALT)
            remove(KEY_WRAPPED_KEY)
        }
    }

    override fun readLockout(): PasscodeLockoutState =
        PasscodeLockoutState(
            failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            lockedOutUntilMillis = prefs.getLong(KEY_LOCKED_OUT_UNTIL, 0L),
            lockedOutAtMillis = prefs.getLong(KEY_LOCKED_OUT_AT, 0L),
        )

    override fun writeLockout(state: PasscodeLockoutState) {
        // commit, not apply: the caller charges an attempt before spending hundreds of milliseconds
        // deriving the key, precisely so that force-stopping the process mid-derivation cannot
        // discard it. An asynchronous apply() would let exactly that write be lost.
        prefs.edit(commit = true) {
            putInt(KEY_FAILED_ATTEMPTS, state.failedAttempts)
            putLong(KEY_LOCKED_OUT_UNTIL, state.lockedOutUntilMillis)
            putLong(KEY_LOCKED_OUT_AT, state.lockedOutAtMillis)
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decodeOrNull(encoded: String): ByteArray? =
        try {
            Base64.getDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Discarding malformed passcode material")
            null
        }

    /**
     * Internal rather than private so tests bind to the real keys. A test carrying its own copy of
     * these strings passes happily while a rename in here loses every user's credentials on upgrade
     * — the one failure this class must never ship.
     */
    internal companion object {
        const val KEY_SALT = "passcode_salt"
        const val KEY_WRAPPED_KEY = "passcode_wrapped_data_key"
        const val KEY_FAILED_ATTEMPTS = "passcode_failed_attempts"
        const val KEY_LOCKED_OUT_UNTIL = "passcode_locked_out_until"
        const val KEY_LOCKED_OUT_AT = "passcode_locked_out_at"
    }
}
