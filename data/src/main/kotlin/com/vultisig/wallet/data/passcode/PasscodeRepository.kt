package com.vultisig.wallet.data.passcode

import com.vultisig.wallet.data.DefaultDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
}

/** Outcome of any operation that requires the user to prove they know the current passcode. */
sealed interface PasscodeUnlockResult {
    data object Success : PasscodeUnlockResult

    /** Wrong passcode; [remainingAttempts] tries remain before a delay is imposed. */
    data class Wrong(val remainingAttempts: Int) : PasscodeUnlockResult

    /** Too many wrong attempts; no entry is accepted for another [retryAfterMillis]. */
    data class LockedOut(val retryAfterMillis: Long) : PasscodeUnlockResult
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
    /** The data-encryption key while unlocked, or null when locked or not configured. */
    fun dataKeyOrNull(): ByteArray?
}

@Singleton
internal class PasscodeRepositoryImpl(
    private val cipher: PasscodeCipher,
    private val store: PasscodeStore,
    private val dispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long,
) : PasscodeRepository, PasscodeDataKeySource {

    @Inject
    constructor(
        cipher: PasscodeCipher,
        store: PasscodeStore,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(cipher, store, dispatcher, System::currentTimeMillis)

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

    override fun dataKeyOrNull(): ByteArray? = dataKey

    override suspend fun initialize() {
        mutex.withLock {
            if (_state.value != PasscodeState.Unknown) return
            val hasPasscode = withContext(dispatcher) { store.readCredentials() != null }
            _state.value = if (hasPasscode) PasscodeState.Locked else PasscodeState.Disabled
        }
    }

    override suspend fun setPasscode(passcode: String) {
        requireValidPasscode(passcode)
        mutex.withLock {
            val key = withContext(dispatcher) { cipher.newDataKey() }
            persistWrappedKey(key, passcode)
            dataKey = key
            store.writeLockout(PasscodeLockout.cleared())
            _state.value = PasscodeState.Unlocked
        }
    }

    override suspend fun changePasscode(
        currentPasscode: String,
        newPasscode: String,
    ): PasscodeUnlockResult {
        requireValidPasscode(newPasscode)
        return mutex.withLock {
            verifyLocked(currentPasscode) { key ->
                persistWrappedKey(key, newPasscode)
                dataKey = key
                _state.value = PasscodeState.Unlocked
            }
        }
    }

    override suspend fun disablePasscode(passcode: String): PasscodeUnlockResult =
        mutex.withLock {
            verifyLocked(passcode) {
                withContext(dispatcher) { store.clearCredentials() }
                dataKey = null
                _state.value = PasscodeState.Disabled
            }
        }

    override suspend fun unlock(passcode: String): PasscodeUnlockResult =
        mutex.withLock {
            verifyLocked(passcode) { key ->
                dataKey = key
                _state.value = PasscodeState.Unlocked
            }
        }

    override fun lock() {
        // Deliberately not suspending: the guard has to be able to lock the moment the app leaves
        // the foreground, without waiting on a coroutine that may never be scheduled. Zeroing then
        // dropping the reference is safe against a concurrent reader, which either sees the old
        // array (already in use for a decrypt in flight) or null.
        val key = dataKey
        dataKey = null
        key?.fill(0)
        _state.compareAndSet(PasscodeState.Unlocked, PasscodeState.Locked)
    }

    /**
     * Verifies [passcode] against the stored wrap, applying and updating the lockout counters, and
     * runs [onVerified] with the recovered data key when it matches. Callers must hold [mutex].
     */
    private suspend fun verifyLocked(
        passcode: String,
        onVerified: suspend (ByteArray) -> Unit,
    ): PasscodeUnlockResult {
        val credentials =
            withContext(dispatcher) { store.readCredentials() }
                ?: return PasscodeUnlockResult.Wrong(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT)

        val lockout = withContext(dispatcher) { store.readLockout() }
        val remainingLockout = PasscodeLockout.remainingLockoutMillis(lockout, nowMillis())
        if (remainingLockout > 0L) {
            return PasscodeUnlockResult.LockedOut(remainingLockout)
        }

        val key =
            withContext(dispatcher) {
                cipher.unwrap(credentials.wrappedDataKey, passcode, credentials.salt)
            }

        if (key == null) {
            val failed = PasscodeLockout.onFailedAttempt(lockout, nowMillis())
            withContext(dispatcher) { store.writeLockout(failed) }
            val penalty = PasscodeLockout.remainingLockoutMillis(failed, nowMillis())
            return if (penalty > 0L) PasscodeUnlockResult.LockedOut(penalty)
            else PasscodeUnlockResult.Wrong(PasscodeLockout.remainingAttempts(failed))
        }

        onVerified(key)
        withContext(dispatcher) { store.writeLockout(PasscodeLockout.cleared()) }
        return PasscodeUnlockResult.Success
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
