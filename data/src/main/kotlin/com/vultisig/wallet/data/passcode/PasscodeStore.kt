package com.vultisig.wallet.data.passcode

import android.content.SharedPreferences
import androidx.core.content.edit
import com.vultisig.wallet.data.utils.InMemorySharedPreferences
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
    /**
     * False when this store is a transient stand-in that will not survive the process — the
     * fallback `MainDataModule` installs after a keystore failure it expects to self-heal.
     *
     * The distinction is not cosmetic. Reads return nothing, which is indistinguishable from "no
     * passcode was ever set", and writes go nowhere, so anything durable done on the strength of
     * either is done against credentials that will not exist on the next launch.
     */
    fun isPersistent(): Boolean

    fun readCredentials(): PasscodeCredentials?

    /** @throws IllegalStateException if the credentials did not reach the disk. */
    fun writeCredentials(credentials: PasscodeCredentials)

    /** @throws IllegalStateException if the credentials are still on the disk afterwards. */
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

    // Matched by type because that is what the fallback is: MainDataModule hands back an
    // InMemorySharedPreferences when the keystore fails in a way it expects to recover from, and
    // nothing else about the instance says so.
    override fun isPersistent(): Boolean = prefs !is InMemorySharedPreferences

    override fun readCredentials(): PasscodeCredentials? {
        val salt = prefs.getString(KEY_SALT, null)?.let(::decodeOrNull)
        val wrapped = prefs.getString(KEY_WRAPPED_KEY, null)?.let(::decodeOrNull)
        // A half that failed to decrypt is indistinguishable from one that was never stored, so it
        // is never grounds for deleting the other.
        if (salt == null || wrapped == null) return null
        return PasscodeCredentials(salt = salt, wrappedDataKey = wrapped)
    }

    override fun writeCredentials(credentials: PasscodeCredentials) {
        // The wrap has to be on disk before setPasscode seals the keyshares under it.
        commitBothHalves("Passcode credentials were not written to disk") {
            putString(KEY_SALT, encode(credentials.salt))
            putString(KEY_WRAPPED_KEY, encode(credentials.wrappedDataKey))
        }
    }

    override fun clearCredentials() {
        // A clear that did not land brings back a passcode the user removed.
        commitBothHalves("Passcode credentials were not removed from disk") {
            remove(KEY_SALT)
            remove(KEY_WRAPPED_KEY)
        }
    }

    /**
     * Applies [mutation] to both halves, or puts back what they held and throws [failure].
     *
     * `commit` updates the in-memory map before it writes the file and leaves it updated when that
     * write fails, while the file keeps its previous contents. Putting the map back is what makes a
     * refused write change nothing.
     */
    private fun commitBothHalves(failure: String, mutation: SharedPreferences.Editor.() -> Unit) {
        val salt = prefs.getString(KEY_SALT, null)
        val wrapped = prefs.getString(KEY_WRAPPED_KEY, null)
        val editor = prefs.edit()
        editor.mutation()
        if (editor.commit()) return
        prefs.edit().putString(KEY_SALT, salt).putString(KEY_WRAPPED_KEY, wrapped).apply()
        error(failure)
    }

    override fun readLockout(): PasscodeLockoutState =
        PasscodeLockoutState(
            failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            penaltyMillis = prefs.getLong(KEY_PENALTY_MILLIS, 0L),
            anchorElapsedMillis = prefs.getLong(KEY_PENALTY_ANCHOR_ELAPSED, 0L),
        )

    override fun writeLockout(state: PasscodeLockoutState) {
        // commit, not apply: the caller charges an attempt before spending hundreds of milliseconds
        // deriving the key, precisely so that force-stopping the process mid-derivation cannot
        // discard it. An asynchronous apply() would let exactly that write be lost.
        prefs.edit(commit = true) {
            putInt(KEY_FAILED_ATTEMPTS, state.failedAttempts)
            putLong(KEY_PENALTY_MILLIS, state.penaltyMillis)
            putLong(KEY_PENALTY_ANCHOR_ELAPSED, state.anchorElapsedMillis)
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
        const val KEY_PENALTY_MILLIS = "passcode_lockout_penalty_millis"
        const val KEY_PENALTY_ANCHOR_ELAPSED = "passcode_lockout_anchor_elapsed"
    }
}
