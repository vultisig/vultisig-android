package com.vultisig.wallet.data.passcode

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
            nowMillis = { now },
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
        repository().setPasscode("12345")

        val reopened = repository()
        reopened.initialize()

        assertEquals(PasscodeState.Locked, reopened.state.value)
    }

    @Test
    fun `initialize does not clobber an already resolved state`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")

        repository.initialize()

        assertEquals(PasscodeState.Unlocked, repository.state.value)
    }

    @Test
    fun `setPasscode unlocks and persists a wrapped key`() = runTest {
        val repository = repository()

        repository.setPasscode("12345")

        assertEquals(PasscodeState.Unlocked, repository.state.value)
        assertNotNull(store.readCredentials())
        assertNotNull(repository.dataKeyOrNull())
    }

    @Test
    fun `setPasscode rejects anything that is not five digits`() = runTest {
        val repository = repository()

        assertFailsWith<IllegalArgumentException> { repository.setPasscode("1234") }
        assertFailsWith<IllegalArgumentException> { repository.setPasscode("123456") }
        assertFailsWith<IllegalArgumentException> { repository.setPasscode("1234a") }
    }

    @Test
    fun `unlock succeeds with the right passcode and exposes the same data key`() = runTest {
        val first = repository()
        first.setPasscode("12345")
        val originalKey = first.dataKeyOrNull()!!.copyOf()

        val reopened = repository()
        reopened.initialize()

        assertEquals(PasscodeUnlockResult.Success, reopened.unlock("12345"))
        assertEquals(PasscodeState.Unlocked, reopened.state.value)
        assertContentEquals(originalKey, reopened.dataKeyOrNull())
    }

    @Test
    fun `unlock reports wrong passcode and counts down the remaining attempts`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")
        repository.lock()

        assertEquals(PasscodeUnlockResult.Wrong(4), repository.unlock("00000"))
        assertEquals(PasscodeUnlockResult.Wrong(3), repository.unlock("00000"))
        assertEquals(PasscodeState.Locked, repository.state.value)
    }

    @Test
    fun `unlock locks the user out after too many wrong attempts and recovers after the delay`() =
        runTest {
            val repository = repository()
            repository.setPasscode("12345")
            repository.lock()

            repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT - 1) { repository.unlock("00000") }
            val lockedOut = repository.unlock("00000")
            assertTrue(lockedOut is PasscodeUnlockResult.LockedOut, "expected a lockout")

            // Even the correct passcode is refused while the penalty is active.
            assertTrue(repository.unlock("12345") is PasscodeUnlockResult.LockedOut)

            now += lockedOut.retryAfterMillis
            assertEquals(PasscodeUnlockResult.Success, repository.unlock("12345"))
        }

    @Test
    fun `a successful unlock clears the failed attempt counter`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")
        repository.lock()

        repository.unlock("00000")
        repository.unlock("12345")
        repository.lock()

        assertEquals(PasscodeUnlockResult.Wrong(4), repository.unlock("00000"))
    }

    @Test
    fun `lockout survives a relaunch`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")
        repository.lock()
        repeat(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT) { repository.unlock("00000") }

        val reopened = repository()
        reopened.initialize()

        assertTrue(reopened.unlock("12345") is PasscodeUnlockResult.LockedOut)
    }

    @Test
    fun `lock drops the in-memory key`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")

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
        repository.setPasscode("12345")
        val keyBefore = repository.dataKeyOrNull()!!.copyOf()

        assertEquals(PasscodeUnlockResult.Success, repository.changePasscode("12345", "54321"))

        assertContentEquals(keyBefore, repository.dataKeyOrNull())
        repository.lock()
        assertEquals(PasscodeUnlockResult.Wrong(4), repository.unlock("12345"))
        assertEquals(PasscodeUnlockResult.Success, repository.unlock("54321"))
    }

    @Test
    fun `changePasscode with the wrong current passcode leaves the old one working`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")

        assertEquals(PasscodeUnlockResult.Wrong(4), repository.changePasscode("00000", "54321"))

        repository.lock()
        assertEquals(PasscodeUnlockResult.Success, repository.unlock("12345"))
    }

    @Test
    fun `changePasscode validates the new passcode before touching anything`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")

        assertFailsWith<IllegalArgumentException> { repository.changePasscode("12345", "1") }

        repository.lock()
        assertEquals(PasscodeUnlockResult.Success, repository.unlock("12345"))
    }

    @Test
    fun `disablePasscode clears the credentials`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")

        assertEquals(PasscodeUnlockResult.Success, repository.disablePasscode("12345"))

        assertEquals(PasscodeState.Disabled, repository.state.value)
        assertNull(store.readCredentials())
        assertNull(repository.dataKeyOrNull())
    }

    @Test
    fun `disablePasscode refuses a wrong passcode`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")

        assertEquals(PasscodeUnlockResult.Wrong(4), repository.disablePasscode("00000"))

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
                repository.unlock("12345"),
            )
        }

    @Test
    fun `setPasscode encrypts stored keyshares with the new data key`() = runTest {
        val repository = repository()

        repository.setPasscode("12345")

        assertEquals(listOf("protect"), protection.calls)
        assertContentEquals(repository.dataKeyOrNull(), protection.protectedWith)
    }

    @Test
    fun `setPasscode persists the wrapped key before encrypting anything with it`() = runTest {
        // Reverse that order and a crash mid-encryption strands ciphertext with no recorded key.
        val repository = repository()
        protection.calls.clear()

        repository.setPasscode("12345")

        assertNotNull(store.readCredentials(), "credentials must exist by the time we encrypt")
        assertEquals(listOf("protect"), protection.calls)
    }

    @Test
    fun `disablePasscode decrypts keyshares before dropping the credentials`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")
        protection.calls.clear()

        repository.disablePasscode("12345")

        assertEquals(listOf("unprotect"), protection.calls)
        assertNull(store.readCredentials())
    }

    @Test
    fun `a failed decrypt leaves the passcode in place`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")
        protection.unprotectFailure = IllegalStateException("keyshare failed to decrypt")

        assertFailsWith<IllegalStateException> { repository.disablePasscode("12345") }

        assertNotNull(store.readCredentials(), "credentials must survive a failed decrypt")
        assertEquals(PasscodeState.Unlocked, repository.state.value)
        assertNotNull(repository.dataKeyOrNull())
    }

    @Test
    fun `changePasscode does not re-encrypt keyshares`() = runTest {
        val repository = repository()
        repository.setPasscode("12345")
        protection.calls.clear()

        repository.changePasscode("12345", "54321")

        assertEquals(emptyList(), protection.calls, "the data key is unchanged")
    }

    @Test
    fun `isLocked is true only while a passcode is configured and not entered`() = runTest {
        val repository = repository()
        repository.initialize()
        assertEquals(false, repository.isLocked())

        repository.setPasscode("12345")
        assertEquals(false, repository.isLocked())

        repository.lock()
        assertEquals(true, repository.isLocked())

        repository.unlock("12345")
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
                nowMillis = { now },
            )
        repository.setPasscode("12345")
        repository.lock()

        withContext(Dispatchers.Default) {
            List(PasscodeLockout.ATTEMPTS_BEFORE_LOCKOUT - 1) {
                    async { repository.unlock("00000") }
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

    override suspend fun protectAll(dataKey: ByteArray) {
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

    @Synchronized override fun readCredentials(): PasscodeCredentials? = credentials

    @Synchronized
    override fun writeCredentials(credentials: PasscodeCredentials) {
        this.credentials = credentials
    }

    @Synchronized
    override fun clearCredentials() {
        credentials = null
    }

    @Synchronized override fun readLockout(): PasscodeLockoutState = lockout

    @Synchronized
    override fun writeLockout(state: PasscodeLockoutState) {
        lockout = state
    }
}
