package com.vultisig.wallet.data.passcode

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PasscodeRepositoryImplTest {

    private lateinit var store: FakePasscodeStore
    private lateinit var protection: RecordingKeyShareProtection
    private var now = 1_000_000L

    @BeforeEach
    fun setUp() {
        store = FakePasscodeStore()
        protection = RecordingKeyShareProtection()
        now = 1_000_000L
    }

    /** All dispatchers share the test scheduler, otherwise `withContext` deadlocks the test. */
    private fun TestScope.repository() =
        PasscodeRepositoryImpl(
            cipher = PasscodeCipher(),
            store = store,
            keyShareProtection = protection,
            dispatcher = StandardTestDispatcher(testScheduler),
            elapsedRealtimeMillis = { now },
        )

    @Test
    fun `state is Disabled after initialize when no passcode is stored`() = runTest {
        val repository = repository()
        assertEquals(PasscodeState.Unknown, repository.state.value)

        repository.initialize()

        assertEquals(PasscodeState.Disabled, repository.state.value)
    }

    @Test
    fun `state is Locked after initialize when a passcode is stored`() = runTest {
        repository().setPasscode("123456")

        val reopened = repository()
        reopened.initialize()

        assertEquals(PasscodeState.Locked, reopened.state.value)
    }

    @Test
    fun `initialize does not clobber an already resolved state`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")

        repository.initialize()

        assertEquals(PasscodeState.Unlocked, repository.state.value)
    }

    @Test
    fun `setPasscode unlocks and persists a wrapped key`() = runTest {
        val repository = repository()

        repository.setPasscode("123456")

        assertEquals(PasscodeState.Unlocked, repository.state.value)
        assertNotNull(store.readCredentials())
        assertNotNull(repository.dataKeyOrNull())
    }

    @Test
    fun `setPasscode rejects anything that is not six digits`() = runTest {
        val repository = repository()

        assertFailsWith<IllegalArgumentException> { repository.setPasscode("12345") }
        assertFailsWith<IllegalArgumentException> { repository.setPasscode("1234567") }
        assertFailsWith<IllegalArgumentException> { repository.setPasscode("12345a") }
    }

    @Test
    fun `unlock succeeds with the right passcode and exposes the same data key`() = runTest {
        val first = repository()
        first.setPasscode("123456")
        val originalKey = first.dataKeyOrNull()!!.copyOf()

        val reopened = repository()
        reopened.initialize()

        assertEquals(PasscodeUnlockResult.Success, reopened.unlock("123456"))
        assertEquals(PasscodeState.Unlocked, reopened.state.value)
        assertContentEquals(originalKey, reopened.dataKeyOrNull())
    }

    @Test
    fun `unlock reports wrong passcode and counts down the remaining attempts`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")
        repository.lock()

        assertEquals(PasscodeUnlockResult.Wrong(4), repository.unlock("000000"))
        assertEquals(PasscodeUnlockResult.Wrong(3), repository.unlock("000000"))
        assertEquals(PasscodeState.Locked, repository.state.value)
    }

    @Test
    fun `unlock locks the user out after too many wrong attempts and recovers after the delay`() =
        runTest {
            val repository = repository()
            repository.setPasscode("123456")
            repository.lock()

            repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT - 1) { repository.unlock("000000") }
            val lockedOut = repository.unlock("000000")
            assertTrue(lockedOut is PasscodeUnlockResult.LockedOut, "expected a lockout")

            // Even the correct passcode is refused while the penalty is active.
            assertTrue(repository.unlock("123456") is PasscodeUnlockResult.LockedOut)

            now += lockedOut.retryAfterMillis
            assertEquals(PasscodeUnlockResult.Success, repository.unlock("123456"))
        }

    @Test
    fun `a successful unlock clears the failed attempt counter`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")
        repository.lock()

        repository.unlock("000000")
        repository.unlock("123456")
        repository.lock()

        assertEquals(PasscodeUnlockResult.Wrong(4), repository.unlock("000000"))
    }

    @Test
    fun `lockout survives a relaunch`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")
        repository.lock()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT) { repository.unlock("000000") }

        val reopened = repository()
        reopened.initialize()

        assertTrue(reopened.unlock("123456") is PasscodeUnlockResult.LockedOut)
    }

    @Test
    fun `lock drops the in-memory key`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")

        repository.lock()

        assertNull(repository.dataKeyOrNull())
        assertEquals(PasscodeState.Locked, repository.state.value)
    }

    @Test
    fun `lock is a no-op when no passcode is configured`() = runTest {
        val repository = repository()
        repository.initialize()

        repository.lock()

        assertEquals(PasscodeState.Disabled, repository.state.value)
    }

    @Test
    fun `changePasscode keeps the data key and accepts only the new passcode`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")
        val keyBefore = repository.dataKeyOrNull()!!.copyOf()

        assertEquals(PasscodeUnlockResult.Success, repository.changePasscode("123456", "654321"))

        assertContentEquals(keyBefore, repository.dataKeyOrNull())
        repository.lock()
        assertEquals(PasscodeUnlockResult.Wrong(4), repository.unlock("123456"))
        assertEquals(PasscodeUnlockResult.Success, repository.unlock("654321"))
    }

    @Test
    fun `changePasscode with the wrong current passcode leaves the old one working`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")

        assertEquals(PasscodeUnlockResult.Wrong(4), repository.changePasscode("000000", "654321"))

        repository.lock()
        assertEquals(PasscodeUnlockResult.Success, repository.unlock("123456"))
    }

    @Test
    fun `changePasscode validates the new passcode before touching anything`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")

        assertFailsWith<IllegalArgumentException> { repository.changePasscode("123456", "1") }

        repository.lock()
        assertEquals(PasscodeUnlockResult.Success, repository.unlock("123456"))
    }

    @Test
    fun `disablePasscode clears the credentials`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")

        assertEquals(PasscodeUnlockResult.Success, repository.disablePasscode("123456"))

        assertEquals(PasscodeState.Disabled, repository.state.value)
        assertNull(store.readCredentials())
        assertNull(repository.dataKeyOrNull())
    }

    @Test
    fun `disablePasscode refuses a wrong passcode`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")

        assertEquals(PasscodeUnlockResult.Wrong(4), repository.disablePasscode("000000"))

        assertNotNull(store.readCredentials())
        assertEquals(PasscodeState.Unlocked, repository.state.value)
    }

    @Test
    fun `unlock on a store with no credentials reports a wrong passcode rather than crashing`() =
        runTest {
            val repository = repository()
            repository.initialize()

            assertEquals(
                PasscodeUnlockResult.Wrong(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT),
                repository.unlock("123456"),
            )
        }

    @Test
    fun `setPasscode encrypts stored keyshares with the new data key`() = runTest {
        val repository = repository()

        repository.setPasscode("123456")

        assertEquals(listOf("protect"), protection.calls)
        assertContentEquals(repository.dataKeyOrNull(), protection.protectedWith)
    }

    @Test
    fun `setPasscode persists the wrapped key before encrypting anything with it`() = runTest {
        // Reverse that order and a crash mid-encryption strands ciphertext with no recorded key.
        // Observed from inside protectAll, not after setPasscode returns: by then both writes have
        // happened and the assertion would hold whatever order they ran in.
        val repository = repository()
        protection.calls.clear()
        var credentialsWhenProtectionBegan: PasscodeCredentials? = null
        protection.onProtectAll = { credentialsWhenProtectionBegan = store.readCredentials() }

        repository.setPasscode("123456")

        assertNotNull(
            credentialsWhenProtectionBegan,
            "credentials must already be stored when encryption starts",
        )
        assertEquals(listOf("protect"), protection.calls)
    }

    @Test
    fun `setPasscode refuses to replace a passcode that is already configured`() = runTest {
        // Overwriting the wrap would orphan every keyshare already encrypted under the old data
        // key, because protectAll skips rows that are encrypted already. Reported rather than
        // thrown: every caller is a launch from a UI event, where an escape kills the process.
        val repository = repository()
        repository.setPasscode("123456")
        val credentials = store.readCredentials()
        protection.calls.clear()

        assertEquals(PasscodeUnlockResult.Failed, repository.setPasscode("654321"))

        assertEquals(credentials, store.readCredentials(), "the original wrap must survive")
        assertEquals(emptyList(), protection.calls)
        assertEquals(PasscodeUnlockResult.Success, repository.unlock("123456"))
    }

    @Test
    fun `setPasscode refuses a store that will not survive the process`() = runTest {
        // The credentials would go to an in-memory fallback and vanish with the process, while
        // protectAll's ciphertext would not: every keyshare sealed under a key nothing recorded.
        store.persistent = false
        val repository = repository()

        assertEquals(PasscodeUnlockResult.Failed, repository.setPasscode("123456"))

        assertEquals(emptyList(), protection.calls, "nothing may be encrypted")
        assertNull(store.readCredentials())
        assertNull(repository.dataKeyOrNull())
    }

    @Test
    fun `setPasscode encrypts nothing when the wrap does not reach the disk`() = runTest {
        val repository = repository()
        store.writeFailure = IllegalStateException("credentials were not written to disk")

        assertEquals(PasscodeUnlockResult.Failed, repository.setPasscode("123456"))

        assertEquals(emptyList(), protection.calls, "nothing may be encrypted")
        assertNull(store.readCredentials())
    }

    @Test
    fun `changePasscode leaves the old passcode in force when the new wrap is not stored`() =
        runTest {
            val repository = repository()
            repository.setPasscode("123456")
            val credentials = store.readCredentials()
            store.writeFailure = IllegalStateException("credentials were not written to disk")

            assertEquals(PasscodeUnlockResult.Failed, repository.changePasscode("123456", "654321"))

            store.writeFailure = null
            assertEquals(credentials, store.readCredentials())
            repository.lock()
            assertEquals(PasscodeUnlockResult.Success, repository.unlock("123456"))
        }

    @Test
    fun `disablePasscode reseals the keyshares when the credentials are not removed`() = runTest {
        // The shares are decrypted before the credentials are dropped, so a passcode that stays in
        // force must not be left guarding a table that is already in the clear.
        val repository = repository()
        repository.setPasscode("123456")
        val credentials = store.readCredentials()
        store.writeFailure = IllegalStateException("credentials were not removed from disk")

        assertEquals(PasscodeUnlockResult.Failed, repository.disablePasscode("123456"))

        assertEquals(credentials, store.readCredentials())
        assertEquals(PasscodeState.Unlocked, repository.state.value)
        assertEquals(listOf("protect", "unprotect", "protect"), protection.calls)
    }

    @Test
    fun `setPasscode survives an encryption failure with a working passcode`() = runTest {
        // The wrap is already stored and the app is unlocked by the time protectAll runs, so a
        // failed row is not a reason to fail the operation around it — the next unlock sweeps it.
        val repository = repository()
        protection.protectFailure = IllegalStateException("database is locked")

        assertEquals(PasscodeUnlockResult.Success, repository.setPasscode("123456"))

        assertEquals(PasscodeState.Unlocked, repository.state.value)
        assertNotNull(store.readCredentials())
    }

    @Test
    fun `unlock finishes an encryption that setPasscode did not`() = runTest {
        // setPasscode is the only other caller of protectAll and cannot be resumed once it is
        // interrupted, so rows left in the clear would stay that way for good.
        val repository = repository()
        protection.protectFailure = IllegalStateException("killed mid-run")
        repository.setPasscode("123456")
        repository.lock()
        protection.protectFailure = null
        protection.calls.clear()

        assertEquals(PasscodeUnlockResult.Success, repository.unlock("123456"))

        assertEquals(listOf("protect"), protection.calls)
        assertContentEquals(repository.dataKeyOrNull(), protection.protectedWith)
    }

    @Test
    fun `the key handed to the unlock sweep is not the one lock zeroes`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")
        repository.lock()
        protection.protectedWith = null

        repository.unlock("123456")

        val used = protection.protectedWith
        assertNotNull(used)
        assertFalse(used.all { it == 0.toByte() }, "the sweep was handed a live key")
        assertContentEquals(used, repository.dataKeyOrNull())
    }

    @Test
    fun `initialize separates an unreachable store from lost credentials`() = runTest {
        // Same symptom — ciphertext with nothing to open it — and opposite advice. Telling a user
        // whose wrap is intact on disk to reinstall is what would actually destroy their vaults.
        protection.encryptedSharesPresent = true
        store.persistent = false
        val repository = repository()

        repository.initialize()

        assertEquals(PasscodeState.StoreUnavailable, repository.state.value)
        assertEquals(true, repository.isLocked(), "keyshare writes must still be refused")
    }

    @Test
    fun `a store that cannot be read resolves rather than throwing or standing still`() = runTest {
        // Both outcomes this replaces are fatal in their own way: an escape kills whichever
        // coroutine asked first, and staying Unknown leaves the guard's blank cover over the whole
        // app with nothing left to move it. Disabled is the one answer that would lose data.
        store.readFailure = IllegalStateException("keystore is gone")
        val repository = repository()

        repository.initialize()

        assertEquals(PasscodeState.StoreUnavailable, repository.state.value)
        assertEquals(true, repository.isLocked(), "keyshare writes must still be refused")
    }

    @Test
    fun `awaiting readable keyshares survives a store that cannot be read`() = runTest {
        // Reached from screens that await it inside a bare launch, so it has to resolve on its own
        // rather than hand them an exception.
        store.readFailure = IllegalStateException("keystore is gone")
        val repository = repository()

        repository.awaitUnlocked()

        assertEquals(PasscodeState.StoreUnavailable, repository.state.value)
    }

    @Test
    fun `awaitUnlocked waits for the passcode and returns at once without one`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")
        repository.lock()

        val waiting = async { repository.awaitUnlocked() }
        runCurrent()
        assertEquals(false, waiting.isCompleted, "a locked app must not report readable keyshares")

        repository.unlock("123456")
        waiting.await()

        // Nothing to wait for once the passcode is gone.
        repository.disablePasscode("123456")
        repository.awaitUnlocked()
    }

    @Test
    fun `awaitUnlocked does not wait on a state no unlock can resolve`() = runTest {
        // KeyUnavailable and StoreUnavailable never become Unlocked without a relaunch, so waiting
        // on them is a hang rather than a delay.
        protection.encryptedSharesPresent = true
        val repository = repository()
        repository.initialize()

        repository.awaitUnlocked()
    }

    @Test
    fun `disablePasscode decrypts keyshares before dropping the credentials`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")
        protection.calls.clear()

        repository.disablePasscode("123456")

        assertEquals(listOf("unprotect"), protection.calls)
        assertNull(store.readCredentials())
    }

    @Test
    fun `a failed decrypt leaves the passcode in place`() = runTest {
        // Reported, not thrown. Escaping here would reach a bare viewModelScope.launch and take
        // the process down, leaving the user holding a passcode they could never remove.
        val repository = repository()
        repository.setPasscode("123456")
        protection.unprotectFailure = IllegalStateException("keyshare failed to decrypt")

        assertEquals(PasscodeUnlockResult.Failed, repository.disablePasscode("123456"))

        assertNotNull(store.readCredentials(), "credentials must survive a failed decrypt")
        assertEquals(PasscodeState.Unlocked, repository.state.value)
        assertNotNull(repository.dataKeyOrNull())
    }

    @Test
    fun `a failed disable does not charge the user an attempt`() = runTest {
        // The passcode was right; only the operation failed. Counting it would march the user
        // towards a lockout for repeatedly trying something that cannot work.
        val repository = repository()
        repository.setPasscode("123456")
        protection.unprotectFailure = IllegalStateException("keyshare failed to decrypt")

        repository.disablePasscode("123456")

        assertEquals(
            PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT,
            PasscodeLockout.remainingAttempts(store.readLockout()),
        )
    }

    @Test
    fun `initialize reports KeyUnavailable when ciphertext outlives its credentials`() = runTest {
        // The keystore-backed prefs were wiped underneath us. Reporting Disabled here is what
        // turns that into silent, permanent data loss: shares dropped on read, plaintext on write.
        protection.encryptedSharesPresent = true
        val repository = repository()

        repository.initialize()

        assertEquals(PasscodeState.KeyUnavailable, repository.state.value)
    }

    @Test
    fun `initialize reports Disabled when there is no ciphertext to worry about`() = runTest {
        protection.encryptedSharesPresent = false
        val repository = repository()

        repository.initialize()

        assertEquals(PasscodeState.Disabled, repository.state.value)
    }

    @Test
    fun `the key handed to protectAll is not the one lock zeroes`() = runTest {
        // lock() takes no mutex and zeroes whatever the field holds. Sharing that array with the
        // bulk encrypt would let backgrounding mid-run seal rows under 32 zero bytes.
        val repository = repository()

        repository.setPasscode("123456")

        val used = protection.protectedWith
        assertNotNull(used)
        assertFalse(used.all { it == 0.toByte() }, "protectAll was handed a live key")
        assertContentEquals(used, repository.dataKeyOrNull())
    }

    @Test
    fun `a wrong passcode is charged before the derivation runs`() = runTest {
        // Force-stopping during the 210k-iteration unwrap must not discard the attempt, or a
        // scripted walk of the keyspace never trips the escalating delay.
        val repository = repository()
        repository.setPasscode("123456")
        repository.lock()
        var chargedDuringUnwrap: Int? = null
        store.onReadCredentials = { chargedDuringUnwrap = store.readLockout().failedAttempts }

        repository.unlock("000000")

        assertEquals(1, store.readLockout().failedAttempts)
        assertEquals(0, chargedDuringUnwrap, "the charge lands after the credentials are read")
    }

    @Test
    fun `changePasscode does not re-encrypt keyshares`() = runTest {
        val repository = repository()
        repository.setPasscode("123456")
        protection.calls.clear()

        repository.changePasscode("123456", "654321")

        assertEquals(emptyList(), protection.calls, "the data key is unchanged")
    }

    @Test
    fun `isLocked is true only while a passcode is configured and not entered`() = runTest {
        val repository = repository()
        repository.initialize()
        assertEquals(false, repository.isLocked())

        repository.setPasscode("123456")
        assertEquals(false, repository.isLocked())

        repository.lock()
        assertEquals(true, repository.isLocked())

        repository.unlock("123456")
        assertEquals(false, repository.isLocked())
    }

    @Test
    fun `concurrent wrong attempts are counted individually`() = runTest {
        // Runs on real threads: the mutex, not the test scheduler, has to serialise the
        // read-modify-write of the attempt counter.
        val repository =
            PasscodeRepositoryImpl(
                cipher = PasscodeCipher(),
                store = store,
                keyShareProtection = protection,
                dispatcher = Dispatchers.Default,
                elapsedRealtimeMillis = { now },
            )
        repository.setPasscode("123456")
        repository.lock()

        withContext(Dispatchers.Default) {
            List(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT - 1) {
                    async { repository.unlock("000000") }
                }
                .awaitAll()
        }

        assertEquals(
            PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT - 1,
            store.readLockout().failedAttempts,
        )
    }
}

/** Records how the repository drives the bulk keyshare re-keying, including its ordering. */
internal class RecordingKeyShareProtection : VaultKeyShareProtection {
    val calls = mutableListOf<String>()
    var protectedWith: ByteArray? = null
    var unprotectFailure: Throwable? = null
    var protectFailure: Throwable? = null

    /** Runs when protection begins, so a test can observe the store mid-operation. */
    var onProtectAll: (() -> Unit)? = null

    var encryptedSharesPresent = false

    override suspend fun hasEncryptedKeyShares(): Boolean = encryptedSharesPresent

    override suspend fun protectAll(dataKey: ByteArray) {
        onProtectAll?.invoke()
        protectFailure?.let { throw it }
        calls += "protect"
        protectedWith = dataKey.copyOf()
    }

    override suspend fun unprotectAll(dataKey: ByteArray) {
        calls += "unprotect"
        unprotectFailure?.let { throw it }
    }
}

/** In-memory [PasscodeStore] standing in for the encrypted preferences. */
internal class FakePasscodeStore : PasscodeStore {
    private var credentials: PasscodeCredentials? = null
    private var lockout: PasscodeLockoutState = PasscodeLockoutState()

    /** Runs on each credential read, so a test can observe the store mid-verification. */
    var onReadCredentials: (() -> Unit)? = null

    /** Set false to stand in for the in-memory fallback a keystore failure installs. */
    var persistent = true

    /** Set to stand in for a keystore that throws rather than returning nothing. */
    var readFailure: Throwable? = null

    /** Set to stand in for a store whose write or removal never reaches the disk. */
    var writeFailure: Throwable? = null

    override fun isPersistent(): Boolean = persistent

    @Synchronized
    override fun readCredentials(): PasscodeCredentials? {
        onReadCredentials?.invoke()
        readFailure?.let { throw it }
        return credentials
    }

    @Synchronized
    override fun writeCredentials(credentials: PasscodeCredentials) {
        writeFailure?.let { throw it }
        this.credentials = credentials
    }

    @Synchronized
    override fun clearCredentials() {
        writeFailure?.let { throw it }
        credentials = null
    }

    @Synchronized override fun readLockout(): PasscodeLockoutState = lockout

    @Synchronized
    override fun writeLockout(state: PasscodeLockoutState) {
        lockout = state
    }
}
