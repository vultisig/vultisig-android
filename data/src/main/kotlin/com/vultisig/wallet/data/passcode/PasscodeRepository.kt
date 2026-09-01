package com.vultisig.wallet.data.passcode

import android.os.SystemClock
import com.vultisig.wallet.data.DefaultDispatcher
import javax.crypto.Cipher
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
     * The passcode was right but the operation could not be completed, and the passcode itself is
     * unchanged — still in force if it was set, still absent if it was not.
     *
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
     * Reads the persisted state again after a launch that could not read it.
     *
     * Only [PasscodeState.KeyUnavailable] and [PasscodeState.StoreUnavailable] retry. Both are
     * decided by what a keystore returned once, and a keystore that stalled can come back, so the
     * alternative is telling the user to relaunch to repeat a read the app can just do again. Every
     * other state is settled by something this cannot re-derive.
     */
    suspend fun retry()

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

    /**
     * Whether a biometric copy of the data key is stored on this device — the optional shortcut
     * described on [BiometricUnlockStore], never a replacement for the passcode.
     *
     * A flow rather than a getter because both the lock screen and the settings switch render from
     * it, and either can turn it off: a copy the hardware has invalidated is dropped on the unlock
     * attempt that discovers it, and the switch must follow.
     */
    val isBiometricUnlockEnabled: StateFlow<Boolean>

    /**
     * A cipher to hand to the biometric prompt before [enableBiometricUnlock], or null when this
     * device cannot hold the copy. The prompt authenticates it; nothing here can.
     */
    suspend fun biometricEnableCipher(): Cipher?

    /**
     * A cipher to hand to the biometric prompt before [unlockWithBiometrics], or null when there is
     * no usable copy to read.
     */
    suspend fun biometricUnlockCipher(): Cipher?

    /**
     * Stores a biometric copy of the data key using a [cipher] the prompt has authenticated.
     *
     * Requires the app to be unlocked, since only then is there a data key to copy. Returns false
     * when the copy did not reach the disk — the passcode is unaffected either way.
     */
    suspend fun enableBiometricUnlock(cipher: Cipher): Boolean

    /** Removes the biometric copy. The passcode continues to work unchanged. */
    suspend fun disableBiometricUnlock()

    /**
     * Unlocks the app with a [cipher] the prompt has authenticated, recovering the data key from
     * the biometric copy instead of deriving it from the passcode.
     */
    suspend fun unlockWithBiometrics(cipher: Cipher): PasscodeUnlockResult
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
     * [PasscodeState.StoreUnavailable]): neither resolves without [PasscodeRepository.retry] or a
     * relaunch, so waiting would be a hang rather than a delay.
     */
    suspend fun awaitUnlocked()
}

@Singleton
internal class PasscodeRepositoryImpl(
    private val cipher: PasscodeCipher,
    private val store: PasscodeStore,
    private val biometrics: BiometricUnlockStore,
    private val keyShareProtection: VaultKeyShareProtection,
    private val dispatcher: CoroutineDispatcher,
    private val elapsedRealtimeMillis: () -> Long,
) : PasscodeRepository, PasscodeDataKeySource {

    @Inject
    constructor(
        cipher: PasscodeCipher,
        store: PasscodeStore,
        biometrics: BiometricUnlockStore,
        keyShareProtection: VaultKeyShareProtection,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        cipher,
        store,
        biometrics,
        keyShareProtection,
        dispatcher,
        SystemClock::elapsedRealtime,
    )

    private val _state = MutableStateFlow<PasscodeState>(PasscodeState.Unknown)
    override val state: StateFlow<PasscodeState> = _state.asStateFlow()

    private val _isBiometricUnlockEnabled = MutableStateFlow(false)
    override val isBiometricUnlockEnabled: StateFlow<Boolean> =
        _isBiometricUnlockEnabled.asStateFlow()

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
            _state.value = resolveOrReport()
            refreshBiometricUnlockEnabled()
        }
    }

    override suspend fun retry() {
        mutex.withLock {
            when (_state.value) {
                PasscodeState.KeyUnavailable,
                PasscodeState.StoreUnavailable -> {
                    _state.value = resolveOrReport()
                    refreshBiometricUnlockEnabled()
                }
                else -> Unit
            }
        }
    }

    /**
     * Publishes whether the biometric shortcut is on. Callers must hold [mutex].
     *
     * A copy only means anything while a passcode is configured, since it is a shortcut past that
     * passcode and nothing else. Reporting false for every other state keeps a copy that outlived
     * its passcode — which the invariant says cannot happen, but which nothing here can prove —
     * from advertising itself as a way in.
     */
    private suspend fun refreshBiometricUnlockEnabled() {
        _isBiometricUnlockEnabled.value =
            _state.value.isConfigured && withContext(dispatcher) { biometrics.isEnabled() }
    }

    /**
     * Resolves the state, reporting a read that failed as one rather than letting it escape.
     *
     * Neither leaving the state Unknown nor throwing is survivable: the first leaves the guard's
     * blank cover over the whole app with nothing to move it, and the second kills whichever
     * coroutine happened to ask first. Disabled is the one answer that would lose data.
     *
     * Callers must hold [mutex].
     */
    private suspend fun resolveOrReport(): PasscodeState =
        try {
            resolveInitialState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Could not read the passcode state")
            PasscodeState.StoreUnavailable
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
            // The data key about to be minted is a new one, so any biometric copy still on the
            // device holds a key nothing wraps. The invariant says disablePasscode already removed
            // it; this is what makes that true even if a crash landed between the two.
            withContext(dispatcher) { biometrics.clear() }
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
            refreshBiometricUnlockEnabled()
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
                // rewritten. This is why changing the passcode is instant on a large vault set,
                // and why the biometric copy needs nothing done to it — it holds the data key
                // itself, not anything derived from the passcode.
                //
                // Nothing else has been touched yet, and the store puts back what it had, so a
                // wrap that did not land leaves the old passcode in force.
                try {
                    persistWrappedKey(key, newPasscode)
                } catch (e: CancellationException) {
                    key.fill(0)
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to store the new passcode")
                    key.fill(0)
                    return@verifyLocked PasscodeUnlockResult.Failed
                }
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
                try {
                    withContext(NonCancellable) {
                        // The biometric copy goes before the credentials it shadows. It holds this
                        // data key, and the moment the credentials are gone it is the only thing
                        // that does — so a crash anywhere after this point leaves no copy of a
                        // retired key behind, which is the invariant BiometricUnlockStore relies on
                        // instead of iOS's binding blob. No earlier than this, or the abort above
                        // would turn the shortcut off over an operation that changed nothing.
                        withContext(dispatcher) { biometrics.clear() }
                        _isBiometricUnlockEnabled.value = false
                        withContext(dispatcher) { store.clearCredentials() }
                        swapDataKey(null)
                        _state.value = PasscodeState.Disabled
                        key.fill(0)
                    }
                } catch (e: CancellationException) {
                    key.fill(0)
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Refusing to disable the passcode: the credentials are still there")
                    // unprotectAll has already put every share back in the clear, and the passcode
                    // that guards them is still in force. Uncancellable because they stay exposed
                    // until this finishes. The biometric copy is gone by now and cannot be remade
                    // without another prompt, so the shortcut stays off — what Failed promises is
                    // unchanged is the passcode it was a shortcut past, and that is still there.
                    withContext(NonCancellable) {
                        protectAllOrLog(key)
                        key.fill(0)
                    }
                    return@verifyLocked PasscodeUnlockResult.Failed
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

    override suspend fun biometricEnableCipher(): Cipher? =
        withContext(dispatcher) { biometrics.encryptCipherOrNull() }

    override suspend fun biometricUnlockCipher(): Cipher? {
        val cipher = withContext(dispatcher) { biometrics.decryptCipherOrNull() }
        // Null can mean the store has just dropped a copy the hardware invalidated, so the link on
        // the lock screen and the switch in settings both have to stop offering it.
        if (cipher == null) {
            mutex.withLock { refreshBiometricUnlockEnabled() }
        }
        return cipher
    }

    override suspend fun enableBiometricUnlock(cipher: Cipher): Boolean =
        mutex.withLock {
            // Only an unlocked app holds the key this copies. Recovering it any other way would
            // mean deriving it from the passcode, which is the passcode path's job, not this one's.
            val key =
                dataKeyOrNull()
                    ?: run {
                        Timber.e("Refusing to enable biometric unlock: the app is locked")
                        return@withLock false
                    }
            val stored =
                try {
                    withContext(dispatcher) { biometrics.store(key, cipher) }
                } finally {
                    key.fill(0)
                }
            refreshBiometricUnlockEnabled()
            stored
        }

    override suspend fun disableBiometricUnlock() {
        mutex.withLock {
            withContext(dispatcher) { biometrics.clear() }
            refreshBiometricUnlockEnabled()
        }
    }

    override suspend fun unlockWithBiometrics(cipher: Cipher): PasscodeUnlockResult =
        mutex.withLock {
            // Anything else is an app with nothing for this to open: already unlocked, no passcode
            // at all, or one of the states where no key of any kind reaches the keyshares.
            if (_state.value != PasscodeState.Locked) {
                return@withLock PasscodeUnlockResult.Failed
            }

            val key =
                withContext(dispatcher) { biometrics.readDataKeyOrNull(cipher) }
                    ?: run {
                        refreshBiometricUnlockEnabled()
                        return@withLock PasscodeUnlockResult.Failed
                    }

            // The field gets a copy so the array below stays ours to zero — see swapDataKey.
            swapDataKey(key.copyOf())
            try {
                // The same sweep the passcode path runs: an interrupted setPasscode is finished by
                // whichever unlock comes next, and this is now one of them.
                protectAllOrLog(key)
            } finally {
                key.fill(0)
            }

            // A biometric match is an authentication that succeeded, so the wrong-passcode counter
            // standing against this device no longer describes anything. The throttle exists to
            // slow down guessing at six digits, and nothing was guessed here.
            withContext(dispatcher) { store.writeLockout(PasscodeLockout.cleared()) }

            publishUnlockedUnlessLocked()
            PasscodeUnlockResult.Success
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
