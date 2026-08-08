package com.vultisig.wallet.data.passcode

import com.vultisig.wallet.data.DefaultDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Whether a passcode is configured, and whether the app is currently unlocked with it. */
sealed interface PasscodeState {
    /** Persisted state has not been read yet; callers should show neither the app nor the lock. */
    data object Unknown : PasscodeState

    /** No passcode configured — the app is fully usable and storage is not passcode-encrypted. */
    data object Disabled : PasscodeState

    /** A passcode is configured and has not been entered in this session. */
    data object Locked : PasscodeState

    /** A passcode is configured and has been entered; the data key is in memory. */
    data object Unlocked : PasscodeState

    /**
     * Encrypted keyshares exist but the credentials that unwrap them are gone, so no passcode can
     * ever open them again.
     *
     * Reachable when the keystore-encrypted preferences are destroyed underneath us — the
     * destructive-recovery and in-memory-fallback paths in `MainDataModule` both hand back a store
     * with no passcode material. Treating that as [Disabled] would report "no passcode", drop every
     * encrypted share from each vault read, and start writing new shares in the clear beside
     * ciphertext nothing can decrypt. The user has to re-import from a backup, and needs telling.
     */
    data object KeyUnavailable : PasscodeState
}

/** True once a passcode exists, whether or not it has been entered in this session. */
val PasscodeState.isConfigured: Boolean
    get() =
        when (this) {
            PasscodeState.Locked,
            PasscodeState.Unlocked -> true
            PasscodeState.KeyUnavailable,
            PasscodeState.Disabled,
            PasscodeState.Unknown -> false
        }

/** Outcome of any operation that requires the user to prove they know the current passcode. */
sealed interface PasscodeUnlockResult {
    data object Success : PasscodeUnlockResult

    /** Wrong passcode; [remainingAttempts] tries remain before a delay is imposed. */
    data class Wrong(val remainingAttempts: Int) : PasscodeUnlockResult

    /** Too many wrong attempts; no entry is accepted for another [retryAfterMillis]. */
    data class LockedOut(val retryAfterMillis: Long) : PasscodeUnlockResult

    /**
     * The passcode was right but the operation could not be completed, and nothing was changed.
     * Surfaced rather than thrown so a single unreadable keyshare cannot crash the app and strand
     * the user with a passcode they are unable to remove.
     */
    data object Failed : PasscodeUnlockResult
}

/**
 * Owns the app passcode: setting it, proving it, and holding the resulting data-encryption key in
 * memory for as long as the app stays unlocked.
 */
interface PasscodeRepository {

    /** Current lock state; starts at [PasscodeState.Unknown] until [initialize] has run. */
    val state: StateFlow<PasscodeState>

    /** Reads persisted state off the main thread. Safe to call more than once. */
    suspend fun initialize()

    /** Configures [passcode] for the first time and leaves the app unlocked. */
    suspend fun setPasscode(passcode: String)

    /** Re-wraps the existing data key under [newPasscode] after verifying [currentPasscode]. */
    suspend fun changePasscode(currentPasscode: String, newPasscode: String): PasscodeUnlockResult

    /** Removes the passcode after verifying it, leaving the app permanently unlocked. */
    suspend fun disablePasscode(passcode: String): PasscodeUnlockResult

    /** Verifies [passcode] and, on success, unlocks the app for this session. */
    suspend fun unlock(passcode: String): PasscodeUnlockResult

    /** Drops the in-memory data key and returns to [PasscodeState.Locked]. */
    fun lock()
}

/**
 * Data-module-internal access to the in-memory data-encryption key. Kept off [PasscodeRepository]
 * so UI code cannot reach the raw key material — only the storage layer needs it.
 */
internal interface PasscodeDataKeySource {
    /**
     * A copy of the data-encryption key while unlocked, or null when locked or not configured.
     *
     * A copy rather than the live array because [PasscodeRepository.lock] zeroes the key the moment
     * the app leaves the foreground, which can land in the middle of an encrypt or decrypt. A
     * zeroed 32-byte array is still a valid AES key, so sharing the array would let a background
     * lock silently turn an in-flight write into ciphertext the real key cannot open.
     */
    fun dataKeyOrNull(): ByteArray?

    /**
     * True when encrypted keyshares exist that this process cannot currently read — either the
     * passcode has not been entered this session, or the credentials that unwrap them are gone.
     * Either way the storage layer must refuse to write a share in the clear beside them.
     */
    fun isLocked(): Boolean
}

@Singleton
internal class PasscodeRepositoryImpl(
    private val cipher: PasscodeCipher,
    private val store: PasscodeStore,
    private val keyShareProtection: VaultKeyShareProtection,
    private val dispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long,
) : PasscodeRepository, PasscodeDataKeySource {

    @Inject
    constructor(
        cipher: PasscodeCipher,
        store: PasscodeStore,
        keyShareProtection: VaultKeyShareProtection,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(cipher, store, keyShareProtection, dispatcher, System::currentTimeMillis)

    private val _state = MutableStateFlow<PasscodeState>(PasscodeState.Unknown)
    override val state: StateFlow<PasscodeState> = _state.asStateFlow()

    /**
     * Guards read-modify-write sequences over the persisted credentials and lockout counters, so
     * two concurrent unlock attempts cannot both read "4 failures" and each write "5".
     */
    private val mutex = Mutex()

    /**
     * Read from arbitrary threads by the storage layer while written under [mutex]; volatile so a
     * reader never observes a half-published reference.
     */
    @Volatile private var dataKey: ByteArray? = null

    override fun dataKeyOrNull(): ByteArray? = dataKey?.copyOf()

    override fun isLocked(): Boolean =
        _state.value == PasscodeState.Locked || _state.value == PasscodeState.KeyUnavailable

    override suspend fun initialize() {
        mutex.withLock {
            if (_state.value != PasscodeState.Unknown) return
            val hasPasscode = withContext(dispatcher) { store.readCredentials() != null }
            _state.value =
                when {
                    hasPasscode -> PasscodeState.Locked
                    // No credentials, but ciphertext that needed them. Reporting Disabled here is
                    // what turns a keystore wipe into silent, permanent data loss.
                    keyShareProtection.hasEncryptedKeyShares() -> PasscodeState.KeyUnavailable
                    else -> PasscodeState.Disabled
                }
        }
    }

    override suspend fun setPasscode(passcode: String) {
        requireValidPasscode(passcode)
        mutex.withLock {
            // Overwriting an existing wrap would throw away the data key that already-encrypted
            // keyshares were sealed under, and protectAll skips those rows, so every one of them
            // would become permanently unreadable. Callers changing a passcode want changePasscode.
            check(withContext(dispatcher) { store.readCredentials() } == null) {
                "A passcode is already configured; use changePasscode instead"
            }
            // Credentials gone but ciphertext left behind — see PasscodeState.KeyUnavailable. A
            // new passcode cannot open those rows, and protectAll skips them because they are
            // already encrypted, so this would quietly leave them orphaned forever.
            check(!keyShareProtection.hasEncryptedKeyShares()) {
                "Encrypted keyshares exist with no credentials; a new passcode cannot open them"
            }
            val key = withContext(dispatcher) { cipher.newDataKey() }
            // Persist the wrapped key before encrypting anything with it. The reverse order would
            // let a crash mid-encryption leave ciphertext whose key was never written down.
            persistWrappedKey(key, passcode)
            // The field gets its own array. lock() zeroes whatever the field holds and takes no
            // mutex, so handing the same array to protectAll would let backgrounding mid-run seal
            // the remaining rows under 32 zero bytes that no passcode can ever reopen.
            swapDataKey(key.copyOf())
            withContext(dispatcher) { store.writeLockout(PasscodeLockout.cleared()) }
            try {
                keyShareProtection.protectAll(key)
            } finally {
                key.fill(0)
            }
            publishUnlockedUnlessLocked()
        }
    }

    /**
     * Publishes [PasscodeState.Unlocked], unless a concurrent [lock] already dropped the key.
     *
     * [lock] can land at any point during a long bulk encrypt, and writing `Unlocked` on top of it
     * would claim a key this process no longer holds: reads would drop every encrypted share and
     * writes would store new ones in the clear.
     */
    private fun publishUnlockedUnlessLocked() {
        _state.value = if (dataKey != null) PasscodeState.Unlocked else PasscodeState.Locked
    }

    override suspend fun changePasscode(
        currentPasscode: String,
        newPasscode: String,
    ): PasscodeUnlockResult {
        requireValidPasscode(newPasscode)
        return mutex.withLock {
            verifyLocked(currentPasscode) { key ->
                // The data key is unchanged, so stored keyshares stay valid: only the wrap is
                // rewritten. This is why changing the passcode is instant on a large vault set.
                persistWrappedKey(key, newPasscode)
                swapDataKey(key)
                publishUnlockedUnlessLocked()
                PasscodeUnlockResult.Success
            }
        }
    }

    override suspend fun disablePasscode(passcode: String): PasscodeUnlockResult =
        mutex.withLock {
            verifyLocked(passcode) { key ->
                // Decrypt first, drop the credentials second. A crash between the two leaves the
                // passcode in place over a partly decrypted table, which still reads correctly.
                //
                // A share that will not decrypt aborts the whole operation with the passcode still
                // in place. That is reported, not thrown: letting it escape would crash the app
                // from a bare launch and leave the user unable to ever turn the passcode off.
                val unprotected = runCatching { keyShareProtection.unprotectAll(key) }
                unprotected.exceptionOrNull()?.let { cause ->
                    if (cause is CancellationException) throw cause
                    Timber.e(cause, "Refusing to disable the passcode: a keyshare did not decrypt")
                    key.fill(0)
                    return@verifyLocked PasscodeUnlockResult.Failed
                }
                withContext(dispatcher) { store.clearCredentials() }
                swapDataKey(null)
                key.fill(0)
                _state.value = PasscodeState.Disabled
                PasscodeUnlockResult.Success
            }
        }

    override suspend fun unlock(passcode: String): PasscodeUnlockResult =
        mutex.withLock {
            verifyLocked(passcode) { key ->
                swapDataKey(key)
                publishUnlockedUnlessLocked()
                PasscodeUnlockResult.Success
            }
        }

    override fun lock() {
        // Deliberately not suspending: the guard has to be able to lock the moment the app leaves
        // the foreground, without waiting on a coroutine that may never be scheduled. Zeroing is
        // safe against a concurrent reader because dataKeyOrNull hands out copies.
        swapDataKey(null)
        _state.compareAndSet(PasscodeState.Unlocked, PasscodeState.Locked)
    }

    /** Installs [newKey] as the in-memory data key, zeroing whatever it displaced. */
    private fun swapDataKey(newKey: ByteArray?) {
        val previous = dataKey
        dataKey = newKey
        if (previous !== newKey) previous?.fill(0)
    }

    /**
     * Verifies [passcode] against the stored wrap, applying and updating the lockout counters, and
     * runs [onVerified] with the recovered data key when it matches. Callers must hold [mutex].
     */
    private suspend fun verifyLocked(
        passcode: String,
        onVerified: suspend (ByteArray) -> PasscodeUnlockResult,
    ): PasscodeUnlockResult {
        val credentials =
            withContext(dispatcher) { store.readCredentials() }
                ?: return PasscodeUnlockResult.Wrong(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT)

        val stored = withContext(dispatcher) { store.readLockout() }
        // Persist the re-anchor, otherwise a clock wound into the past keeps reporting a full
        // penalty on every attempt and the lockout never expires.
        val lockout = PasscodeLockout.reanchoredForClockChange(stored, nowMillis())
        if (lockout !== stored) {
            withContext(dispatcher) { store.writeLockout(lockout) }
        }
        val remainingLockout = PasscodeLockout.remainingLockoutMillis(lockout, nowMillis())
        if (remainingLockout > 0L) {
            return PasscodeUnlockResult.LockedOut(remainingLockout)
        }

        // Charge the attempt before deriving, and durably. Derivation is 210k PBKDF2 iterations —
        // hundreds of milliseconds of wall clock in which a scripted attacker can force-stop the
        // process, and anything only written afterwards (or written with apply()) is simply lost.
        // The counter would never reach the threshold and the escalating delay would never engage.
        val attempted = PasscodeLockout.onFailedAttempt(lockout, nowMillis())
        withContext(dispatcher) { store.writeLockout(attempted) }

        val key =
            withContext(dispatcher) {
                cipher.unwrap(credentials.wrappedDataKey, passcode, credentials.salt)
            }

        if (key == null) {
            val penalty = PasscodeLockout.remainingLockoutMillis(attempted, nowMillis())
            return if (penalty > 0L) PasscodeUnlockResult.LockedOut(penalty)
            else PasscodeUnlockResult.Wrong(PasscodeLockout.remainingAttempts(attempted))
        }

        // The passcode was right, so refund the attempt charged above before running the operation.
        // The throttle exists to slow down wrong guesses; whether the operation then succeeds says
        // nothing about whether the user knows their passcode.
        withContext(dispatcher) { store.writeLockout(PasscodeLockout.cleared()) }
        return onVerified(key)
    }

    /**
     * Wraps [key] under [passcode] with a fresh salt and persists both. Callers must hold [mutex].
     */
    private suspend fun persistWrappedKey(key: ByteArray, passcode: String) {
        val credentials =
            withContext(dispatcher) {
                val salt = cipher.newSalt()
                PasscodeCredentials(salt = salt, wrappedDataKey = cipher.wrap(key, passcode, salt))
            }
        withContext(dispatcher) { store.writeCredentials(credentials) }
    }

    private fun requireValidPasscode(passcode: String) {
        require(passcode.length == PASSCODE_LENGTH) {
            "Passcode must be $PASSCODE_LENGTH digits, was ${passcode.length}"
        }
        require(passcode.all(Char::isDigit)) { "Passcode must contain digits only" }
    }
}
