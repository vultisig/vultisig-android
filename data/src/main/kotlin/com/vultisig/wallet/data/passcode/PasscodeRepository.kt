package com.vultisig.wallet.data.passcode

import android.os.SystemClock
import com.vultisig.wallet.data.DefaultDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    /**
     * Encrypted keyshares exist and the credential store could not be opened *this launch* — see
     * [PasscodeStore.isPersistent].
     *
     * Indistinguishable from [KeyUnavailable] by what is readable right now, and worlds apart in
     * what it means: the wrap is almost certainly still on disk, waiting for a keystore that comes
     * back. Collapsing the two would tell a user whose vaults are intact to reinstall the app,
     * which is the one action that would actually destroy them.
     */
    data object StoreUnavailable : PasscodeState
}

/** True once a passcode exists, whether or not it has been entered in this session. */
val PasscodeState.isConfigured: Boolean
    get() =
        when (this) {
            PasscodeState.Locked,
            PasscodeState.Unlocked -> true
            PasscodeState.KeyUnavailable,
            PasscodeState.StoreUnavailable,
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

    /**
     * Reads persisted state off the main thread. Safe to call more than once.
     *
     * Does not throw. A store that cannot be read resolves to [PasscodeState.StoreUnavailable]
     * rather than escaping: every caller is a coroutine started from a UI event or a screen's
     * `init`, and leaving the state [PasscodeState.Unknown] is as bad as crashing — the guard's
     * blank cover would sit over the whole app with nothing left to move it.
     */
    suspend fun initialize()

    /**
     * Configures [passcode] for the first time and leaves the app unlocked.
     *
     * Returns [PasscodeUnlockResult.Success] or [PasscodeUnlockResult.Failed]; the verification
     * outcomes cannot arise because there is nothing yet to verify against. Reported rather than
     * thrown for the same reason as the other three: every caller is a `launch` from a UI event,
     * and an escape there takes the process down.
     */
    suspend fun setPasscode(passcode: String): PasscodeUnlockResult

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
     */
    fun isLocked(): Boolean

    /**
     * Suspends while the app is locked, returning as soon as keyshares are readable — immediately
     * when no passcode is configured.
     *
     * Returns without waiting when the credentials are unreachable ([PasscodeState.KeyUnavailable],
     * [PasscodeState.StoreUnavailable]): neither resolves without a relaunch, so waiting would be a
     * hang rather than a delay.
     */
    suspend fun awaitUnlocked()
}

@Singleton
internal class PasscodeRepositoryImpl(
    private val cipher: PasscodeCipher,
    private val store: PasscodeStore,
    private val keyShareProtection: VaultKeyShareProtection,
    private val dispatcher: CoroutineDispatcher,
    private val elapsedRealtimeMillis: () -> Long,
) : PasscodeRepository, PasscodeDataKeySource {

    @Inject
    constructor(
        cipher: PasscodeCipher,
        store: PasscodeStore,
        keyShareProtection: VaultKeyShareProtection,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(cipher, store, keyShareProtection, dispatcher, SystemClock::elapsedRealtime)

    private val _state = MutableStateFlow<PasscodeState>(PasscodeState.Unknown)
    override val state: StateFlow<PasscodeState> = _state.asStateFlow()

    /**
     * Guards read-modify-write sequences over the persisted credentials and lockout counters, so
     * two concurrent unlock attempts cannot both read "4 failures" and each write "5".
     */
    private val mutex = Mutex()

    /**
     * Held across every read and every zeroing of [dataKey].
     *
     * [lock] can land on any thread at any moment, and zeroing the array a reader is halfway
     * through copying hands that reader a key that is part real and part zeroes — still 32 bytes,
     * still accepted by AES, and quietly wrong. Volatile publishes the reference, not the bytes
     * behind it, so the copy and the zeroing have to exclude each other outright.
     */
    private val keyLock = Any()

    /**
     * Read from arbitrary threads by the storage layer while written under [mutex]; volatile so a
     * reader never observes a half-published reference.
     */
    @Volatile private var dataKey: ByteArray? = null

    override fun dataKeyOrNull(): ByteArray? = synchronized(keyLock) { dataKey?.copyOf() }

    override fun isLocked(): Boolean =
        when (_state.value) {
            PasscodeState.Locked,
            PasscodeState.KeyUnavailable,
            PasscodeState.StoreUnavailable -> true
            PasscodeState.Unlocked,
            PasscodeState.Disabled,
            PasscodeState.Unknown -> false
        }

    override suspend fun awaitUnlocked() {
        // Resolves the state rather than assuming someone else has. Only the guard calls
        // initialize, so a caller reached from a service or a worker — with no UI in this process
        // — would otherwise wait on an Unknown that nothing is going to move.
        initialize()
        _state.first { it != PasscodeState.Unknown && it != PasscodeState.Locked }
    }

    override suspend fun initialize() {
        mutex.withLock {
            if (_state.value != PasscodeState.Unknown) return
            _state.value =
                try {
                    resolveInitialState()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Reading the credentials is itself a keystore operation, and the database is
                    // asked whether any ciphertext exists — either can fail. Neither leaving the
                    // state Unknown nor letting this escape is survivable: the first leaves the
                    // guard's blank cover over the whole app with nothing to move it, and the
                    // second kills whichever coroutine happened to ask first. Reporting Disabled
                    // is the one answer that would lose data, so it reports the truth — nothing
                    // readable this launch — which at least says so on screen and names the fix.
                    Timber.e(e, "Could not read the passcode state")
                    PasscodeState.StoreUnavailable
                }
        }
    }

    /** Callers must hold [mutex]. */
    private suspend fun resolveInitialState(): PasscodeState {
        val hasPasscode = withContext(dispatcher) { store.readCredentials() != null }
        return when {
            hasPasscode -> PasscodeState.Locked
            !keyShareProtection.hasEncryptedKeyShares() -> PasscodeState.Disabled
            // Ciphertext with nothing to open it. Reporting Disabled here is what turns a keystore
            // wipe into silent, permanent data loss — but which of the two unreadable states this
            // is decides what the user is told to do about it, and "reinstall" aimed at a store
            // that is merely unavailable this launch would destroy vaults whose wrap is sitting
            // intact on disk.
            withContext(dispatcher) { store.isPersistent() } -> PasscodeState.KeyUnavailable
            else -> PasscodeState.StoreUnavailable
        }
    }

    override suspend fun setPasscode(passcode: String): PasscodeUnlockResult {
        requireValidPasscode(passcode)
        return mutex.withLock {
            // Nothing written here would survive the launch, but protectAll's ciphertext would:
            // the next launch would find every keyshare encrypted under a key whose only record
            // went to a store that no longer exists. Refusing costs the user a retry after a
            // restart; going ahead costs them their vaults.
            if (!withContext(dispatcher) { store.isPersistent() }) {
                Timber.e("Refusing to set a passcode: the credential store is not persistent")
                return@withLock PasscodeUnlockResult.Failed
            }
            // Overwriting an existing wrap would throw away the data key that already-encrypted
            // keyshares were sealed under, and protectAll skips those rows, so every one of them
            // would become permanently unreadable. Callers changing a passcode want changePasscode.
            if (withContext(dispatcher) { store.readCredentials() } != null) {
                Timber.e("Refusing to set a passcode: one is already configured")
                return@withLock PasscodeUnlockResult.Failed
            }
            // Credentials gone but ciphertext left behind — see PasscodeState.KeyUnavailable. A
            // new passcode cannot open those rows, and protectAll skips them because they are
            // already encrypted, so this would quietly leave them orphaned forever.
            if (keyShareProtection.hasEncryptedKeyShares()) {
                Timber.e("Refusing to set a passcode: encrypted keyshares have no credentials")
                return@withLock PasscodeUnlockResult.Failed
            }
            val key = withContext(dispatcher) { cipher.newDataKey() }
            try {
                // Not cancellable as a unit. Cancellation between the wrap and the bulk encrypt
                // leaves a passcode this session still believes is off, over shares this session
                // keeps writing in the clear — and nothing re-runs protectAll to close the gap
                // until the user unlocks again, which they will not be prompted to do.
                withContext(NonCancellable) {
                    // Ahead of the wrap, so a store that cannot be written to fails before any of
                    // this becomes real rather than half-way through.
                    withContext(dispatcher) { store.writeLockout(PasscodeLockout.cleared()) }
                    // Persist the wrapped key before encrypting anything with it. The reverse
                    // order lets a crash mid-encryption leave ciphertext with no recorded key.
                    persistWrappedKey(key, passcode)
                    // The field gets its own array. lock() zeroes whatever the field holds, so
                    // handing the same array to protectAll would let backgrounding mid-run seal
                    // the remaining rows under 32 zero bytes that no passcode can ever reopen.
                    swapDataKey(key.copyOf())
                    protectAllOrLog(key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing above the wrap changes anything, and everything below it cannot throw,
                // so an escape here means the passcode was never stored.
                Timber.e(e, "Failed to store the passcode")
                return@withLock PasscodeUnlockResult.Failed
            } finally {
                key.fill(0)
            }
            publishUnlockedUnlessLocked()
            PasscodeUnlockResult.Success
        }
    }

    /**
     * Encrypts the plaintext keyshares under [key], reporting a failure rather than propagating it.
     *
     * The passcode is already in place and working by the time this runs, so an unwritable row is
     * not a reason to fail the operation around it — rows carry their own marker, and the sweep at
     * the next [unlock] picks up whatever was left behind.
     */
    private suspend fun protectAllOrLog(key: ByteArray) {
        try {
            keyShareProtection.protectAll(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt keyshares at rest; retrying on the next unlock")
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
                // One unit, uncancellable. Stopping between the two leaves the key live and the
                // state Unlocked with no wrap on disk: every keyshare written afterwards would be
                // sealed under a key the next launch has no way to recover.
                withContext(NonCancellable) {
                    withContext(dispatcher) { store.clearCredentials() }
                    swapDataKey(null)
                    key.fill(0)
                    _state.value = PasscodeState.Disabled
                }
                PasscodeUnlockResult.Success
            }
        }

    override suspend fun unlock(passcode: String): PasscodeUnlockResult =
        mutex.withLock {
            verifyLocked(passcode) { key ->
                // The field gets a copy so the array below stays ours to zero — see swapDataKey.
                swapDataKey(key.copyOf())
                try {
                    // The only other caller of protectAll is setPasscode, which cannot be resumed
                    // if it is interrupted: this is what finishes the job. Cheap when there is
                    // nothing to do — the query matches only rows still in the clear.
                    protectAllOrLog(key)
                } finally {
                    key.fill(0)
                }
                publishUnlockedUnlessLocked()
                PasscodeUnlockResult.Success
            }
        }

    override fun lock() {
        // Deliberately not suspending: the guard has to be able to lock the moment the app leaves
        // the foreground, without waiting on a coroutine that may never be scheduled. Racing a
        // reader is safe because both sides hold keyLock, so a copy is taken whole or not at all.
        swapDataKey(null)
        _state.compareAndSet(PasscodeState.Unlocked, PasscodeState.Locked)
    }

    /**
     * Installs [newKey] as the in-memory data key, zeroing whatever it displaced.
     *
     * Callers that keep using the array they pass in must hand over a copy: whatever goes in here
     * belongs to the field, and the next [lock] fills it with zeroes wherever else it is held.
     */
    private fun swapDataKey(newKey: ByteArray?) {
        synchronized(keyLock) {
            val previous = dataKey
            dataKey = newKey
            if (previous !== newKey) previous?.fill(0)
        }
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
        // Persist the re-anchor, otherwise a penalty that outlived a reboot reads as fully
        // outstanding on every attempt from then on and never expires.
        val lockout = PasscodeLockout.reanchoredAfterReboot(stored, elapsedRealtimeMillis())
        if (lockout !== stored) {
            withContext(dispatcher) { store.writeLockout(lockout) }
        }
        val remainingLockout =
            PasscodeLockout.remainingLockoutMillis(lockout, elapsedRealtimeMillis())
        if (remainingLockout > 0L) {
            return PasscodeUnlockResult.LockedOut(remainingLockout)
        }

        // Charge the attempt before deriving, and durably. Derivation is 210k PBKDF2 iterations —
        // hundreds of milliseconds of wall clock in which a scripted attacker can force-stop the
        // process, and anything only written afterwards (or written with apply()) is simply lost.
        // The counter would never reach the threshold and the escalating delay would never engage.
        val attempted = PasscodeLockout.onFailedAttempt(lockout, elapsedRealtimeMillis())
        withContext(dispatcher) { store.writeLockout(attempted) }

        val key =
            withContext(dispatcher) {
                cipher.unwrap(credentials.wrappedDataKey, passcode, credentials.salt)
            }

        if (key == null) {
            val penalty = PasscodeLockout.remainingLockoutMillis(attempted, elapsedRealtimeMillis())
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
