package com.vultisig.wallet.data.utils

import io.mockk.mockk
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import timber.log.Timber

/**
 * Regression suite for issue #4403: `buildSecurePrefsKey` must never throw [IllegalStateException]
 * (the type produced by Kotlin's `error(...)`) on lock-timeout, lock-interrupt, or wrong-entry-type
 * paths. The provider catch in `MainDataModule.provideEncryptedSharedPrefs` and the prewarm catch
 * in `SharedPrefsMasterKeyInitializer.prewarm()` both handle only [GeneralSecurityException] and
 * [IOException]; anything else escapes as a Hilt binding failure → process crash on cold launch.
 *
 * Tests target the internal seam overload `buildSecurePrefsKey(lock, lockTimeoutMillis, loadEntry,
 * generateKey)` so every exit path (success-existing, success-generated, lock-timeout,
 * lock-interrupt, unexpected-entry-type, loadEntry-throws, generateKey-throws) is exercisable on
 * the JVM without AndroidKeyStore.
 */
internal class BuildSecurePrefsKeyTest {

    private data class LogEntry(
        val priority: Int,
        val tag: String?,
        val message: String,
        val t: Throwable?,
    )

    private val logEntries = mutableListOf<LogEntry>()

    private val capturingTree =
        object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                logEntries.add(LogEntry(priority, tag, message, t))
            }
        }

    @BeforeEach
    fun setUp() {
        logEntries.clear()
        Timber.plant(capturingTree)
    }

    @AfterEach
    fun tearDown() {
        Timber.uproot(capturingTree)
        // Defensive: clear any interrupt flag a test set on this thread so subsequent
        // tests (and the JUnit runner) run on a clean thread.
        Thread.interrupted()
    }

    private fun fakeKey(): SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    /** android.util.Log.WARN == 5; the literal avoids the Android SDK on the JVM test classpath. */
    private val warnPriority = 5

    private fun warnLogs(): List<LogEntry> = logEntries.filter { it.priority == warnPriority }

    // ── (a) Existing entry success ───────────────────────────────────────────────

    @Test
    fun `existing entry is returned without calling generateKey`() {
        val expected = fakeKey()
        val secretEntry = KeyStore.SecretKeyEntry(expected)
        var generateCalled = false
        val lock = ReentrantLock()

        val result =
            buildSecurePrefsKey(
                lock = lock,
                lockTimeoutMillis = 3_000L,
                loadEntry = { secretEntry },
                generateKey = {
                    generateCalled = true
                    fakeKey()
                },
            )

        assertFalse(generateCalled, "generateKey must NOT be called when an entry exists")
        assertSame(expected, result, "must return the key from the existing entry")
        assertFalse(lock.isLocked, "lock must be released after success")
        assertEquals(0, warnLogs().size, "zero WARN logs expected on happy path")
    }

    // ── (b) Generated key success ────────────────────────────────────────────────

    @Test
    fun `null entry causes generateKey to be called and its result returned`() {
        val generated = fakeKey()
        var generateCalled = false
        val lock = ReentrantLock()

        val result =
            buildSecurePrefsKey(
                lock = lock,
                lockTimeoutMillis = 3_000L,
                loadEntry = { null },
                generateKey = {
                    generateCalled = true
                    generated
                },
            )

        assertTrue(generateCalled, "generateKey must be called when loadEntry returns null")
        assertSame(generated, result, "must return the key from generateKey")
        assertFalse(lock.isLocked, "lock must be released after success")
        assertEquals(0, warnLogs().size, "zero WARN logs expected on happy path")
    }

    // ── (c) Lock-timeout ─────────────────────────────────────────────────────────

    @Test
    fun `lock timeout throws KeyStoreException with Timed out message`() {
        val lock = ReentrantLock()
        var loadEntryCalled = false
        var generateCalled = false

        withLockHeldByOtherThread(lock) {
            val ex =
                assertThrows<KeyStoreException> {
                    buildSecurePrefsKey(
                        lock = lock,
                        lockTimeoutMillis = 0L,
                        loadEntry = {
                            loadEntryCalled = true
                            null
                        },
                        generateKey = {
                            generateCalled = true
                            fakeKey()
                        },
                    )
                }

            assertIsNot<IllegalStateException>(
                ex,
                "must NOT be IllegalStateException — old error() contract",
            )
            assertIs<GeneralSecurityException>(ex, "must be catchable as GeneralSecurityException")
            assertTrue(
                ex.message?.contains("Timed out") == true,
                "message must contain 'Timed out', was: ${ex.message}",
            )
            assertTrue(
                ex.message?.contains("0 ms") == true,
                "message must mention the timeout value with units, was: ${ex.message}",
            )

            assertFalse(loadEntryCalled, "loadEntry must NOT be called after timeout")
            assertFalse(generateCalled, "generateKey must NOT be called after timeout")

            val warns = warnLogs()
            assertEquals(1, warns.size, "exactly one WARN log expected on timeout")
            assertTrue(
                warns[0].message.contains("Timed out"),
                "WARN message must contain 'Timed out', was: ${warns[0].message}",
            )

            assertFalse(
                lock.isHeldByCurrentThread,
                "calling thread must NOT hold the lock after timeout",
            )
        }

        assertFalse(lock.isLocked, "lock must be fully free after holder releases it")
    }

    // ── (d) Unexpected-entry-type ────────────────────────────────────────────────

    @Test
    fun `non-SecretKeyEntry throws KeyStoreException mentioning unexpected entry type`() {
        // KeyStore.PrivateKeyEntry requires real key material to construct; mockk avoids that.
        val wrongEntry: KeyStore.Entry = mockk<KeyStore.PrivateKeyEntry>()
        var generateCalled = false
        val lock = ReentrantLock()

        val ex =
            assertThrows<KeyStoreException> {
                buildSecurePrefsKey(
                    lock = lock,
                    lockTimeoutMillis = 3_000L,
                    loadEntry = { wrongEntry },
                    generateKey = {
                        generateCalled = true
                        fakeKey()
                    },
                )
            }

        assertIsNot<IllegalStateException>(
            ex,
            "must NOT be IllegalStateException — old error() contract",
        )
        assertIs<GeneralSecurityException>(ex, "must be catchable as GeneralSecurityException")
        assertIs<UnexpectedKeyEntryException>(
            ex,
            "persistent wrong-entry state must surface as UnexpectedKeyEntryException so the " +
                "provider routes it to destructive recovery, not an in-memory fallback",
        )
        assertTrue(
            ex.message?.contains("unexpected entry type") == true,
            "message must contain 'unexpected entry type', was: ${ex.message}",
        )
        assertTrue(
            ex.message?.contains("PrivateKeyEntry") == true,
            "message must mention the actual entry class name, was: ${ex.message}",
        )

        assertFalse(generateCalled, "generateKey must NOT be called when entry type is wrong")

        val warns = warnLogs()
        assertEquals(1, warns.size, "exactly one WARN log expected on wrong entry type")
        assertTrue(
            warns[0].message.contains("unexpected entry type") &&
                warns[0].message.contains(SECURE_PREFS_KEY_ALIAS),
            "WARN message must mention both the alias and the unexpected entry type, was: ${warns[0].message}",
        )

        assertFalse(lock.isLocked, "lock must be released after unexpected-entry-type failure")
    }

    // ── (e) Lock-interrupted ─────────────────────────────────────────────────────

    @Test
    fun `interrupt before tryLock throws KeyStoreException with Interrupted message and restored flag`() {
        var loadEntryCalled = false
        var generateCalled = false
        val lock = ReentrantLock()

        Thread.currentThread().interrupt()

        val ex =
            assertThrows<KeyStoreException> {
                buildSecurePrefsKey(
                    lock = lock,
                    lockTimeoutMillis = 3_000L,
                    loadEntry = {
                        loadEntryCalled = true
                        null
                    },
                    generateKey = {
                        generateCalled = true
                        fakeKey()
                    },
                )
            }

        assertIsNot<IllegalStateException>(
            ex,
            "must NOT be IllegalStateException — old error() contract",
        )
        assertIs<GeneralSecurityException>(ex, "must be catchable as GeneralSecurityException")
        assertTrue(
            ex.message?.contains("Interrupted") == true,
            "message must contain 'Interrupted', was: ${ex.message}",
        )
        val cause = ex.cause
        assertNotNull(cause, "cause must be set")
        assertIs<InterruptedException>(cause, "cause must be the InterruptedException")

        assertTrue(
            Thread.currentThread().isInterrupted,
            "interrupt flag must be restored after the call",
        )
        // Clear so subsequent tests run on a clean thread.
        Thread.interrupted()

        assertFalse(loadEntryCalled, "loadEntry must NOT be called after interrupt")
        assertFalse(generateCalled, "generateKey must NOT be called after interrupt")

        val warns = warnLogs()
        assertEquals(1, warns.size, "exactly one WARN log expected on interrupt")
        assertTrue(
            warns[0].message.contains("Interrupted") || warns[0].message.contains("interrupted"),
            "WARN message must mention interrupted, was: ${warns[0].message}",
        )

        assertFalse(lock.isLocked, "lock must NOT be held — it was never successfully acquired")
    }

    // ── (f') loadEntry itself throws — must propagate verbatim and release the lock ─

    @Test
    fun `loadEntry throwing GeneralSecurityException propagates and releases the lock`() {
        val original = KeyStoreException("simulated keystore daemon failure")
        var generateCalled = false
        val lock = ReentrantLock()

        val ex =
            assertThrows<KeyStoreException> {
                buildSecurePrefsKey(
                    lock = lock,
                    lockTimeoutMillis = 3_000L,
                    loadEntry = { throw original },
                    generateKey = {
                        generateCalled = true
                        fakeKey()
                    },
                )
            }

        assertSame(original, ex, "loadEntry's throwable must propagate verbatim, not be wrapped")
        assertFalse(generateCalled, "generateKey must NOT be called after loadEntry fails")
        assertFalse(lock.isLocked, "lock must be released even when loadEntry throws")
    }

    // ── (f) Parameterized invariant — no IllegalStateException propagates ────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("failureScenarios")
    fun `thrown exception is never IllegalStateException and is always catchable as GeneralSecurityException or IOException`(
        label: String,
        scenario: Runnable,
    ) {
        val thrown =
            try {
                scenario.run()
                null
            } catch (e: Throwable) {
                e
            }

        assertNotNull(thrown, "[$label] must throw for a failure scenario")
        assertIsNot<IllegalStateException>(
            thrown,
            "[$label] must NOT throw IllegalStateException (old error() contract)",
        )
        assertTrue(
            thrown is GeneralSecurityException || thrown is IOException,
            "[$label] must be catchable as GeneralSecurityException or IOException, got ${thrown::class.simpleName}",
        )
    }

    // ── (g) Parameterized invariant — lock released on every path ────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("allScenarios")
    fun `lock is always released after the call regardless of outcome`(
        label: String,
        scenario: LockScenario,
    ) {
        try {
            scenario.run()
        } catch (_: Exception) {
            // Lock-state-only check: failure-mode exceptions thrown by buildSecurePrefsKey are
            // expected here. Per-scenario semantics (type, message, log count) are asserted in
            // dedicated @Test methods above. Errors (including AssertionError) intentionally
            // propagate so a broken scenario does not silently green this test.
        } finally {
            scenario.cleanup()
        }
        assertFalse(
            scenario.lock.isHeldByCurrentThread,
            "[$label] calling thread must NOT hold the lock after the call",
        )
        if (label != "timeout") {
            assertFalse(scenario.lock.isLocked, "[$label] lock must be fully free after the call")
        }
    }

    // ── (h) Bit-for-bit success preservation ─────────────────────────────────────

    @Test
    fun `returned key encodes to exactly the same bytes as the wrapped SecretKeySpec`() {
        val bytes = ByteArray(32) { (it * 7 + 13).toByte() }
        val spec = SecretKeySpec(bytes, "AES")
        val secretEntry = KeyStore.SecretKeyEntry(spec)
        val lock = ReentrantLock()

        val result =
            buildSecurePrefsKey(
                lock = lock,
                lockTimeoutMillis = 3_000L,
                loadEntry = { secretEntry },
                generateKey = { fakeKey() },
            )

        assertContentEquals(
            bytes,
            result.encoded,
            "encoded bytes must be identical to the original SecretKeySpec bytes",
        )
    }

    /**
     * Runs [block] with [lock] held by a separate thread. The holder is released and joined inside
     * `finally`, so [block]-level assertion failures cannot leak the holder thread or the lock.
     */
    private fun withLockHeldByOtherThread(lock: ReentrantLock, block: () -> Unit) {
        val holderStarted = CountDownLatch(1)
        val releaseHolder = CountDownLatch(1)
        val holderThread = Thread {
            lock.lock()
            try {
                holderStarted.countDown()
                releaseHolder.await(10, TimeUnit.SECONDS)
            } finally {
                lock.unlock()
            }
        }
        holderThread.isDaemon = true
        holderThread.start()
        try {
            assertTrue(
                holderStarted.await(5, TimeUnit.SECONDS),
                "holder thread did not acquire the lock within 5s",
            )
            assertTrue(lock.isLocked, "pre-condition: holder thread must own the lock")
            block()
        } finally {
            releaseHolder.countDown()
            holderThread.join(5_000)
            assertFalse(holderThread.isAlive, "holder thread did not terminate after release")
        }
    }

    /**
     * Pairs a runnable scenario with its injected lock and a teardown hook for any holder threads.
     */
    internal class LockScenario(
        val label: String,
        val lock: ReentrantLock,
        private val action: () -> Unit,
        private val onCleanup: () -> Unit = {},
    ) : Runnable {
        override fun toString(): String = label

        override fun run() {
            action()
        }

        fun cleanup() {
            onCleanup()
        }
    }

    companion object {

        private fun zeroKey(): SecretKey = SecretKeySpec(ByteArray(32), "AES")

        /** Lazily creates a fresh holder thread inside the lambda — nothing runs until invoked. */
        private fun timeoutScenario(): LockScenario {
            val lock = ReentrantLock()
            // Holder-thread plumbing lives inside the lambda so it is allocated and torn down per
            // test invocation. JUnit5 evaluates @MethodSource at discovery; we don't want a real
            // thread spawned at that time.
            var holderThread: Thread? = null
            var releaseHolder: CountDownLatch? = null
            return LockScenario(
                label = "timeout",
                lock = lock,
                action = {
                    val started = CountDownLatch(1)
                    val release = CountDownLatch(1).also { releaseHolder = it }
                    val thread = Thread {
                        lock.lock()
                        try {
                            started.countDown()
                            release.await(10, TimeUnit.SECONDS)
                        } finally {
                            lock.unlock()
                        }
                    }
                    thread.isDaemon = true
                    thread.start()
                    holderThread = thread
                    if (!started.await(5, TimeUnit.SECONDS)) {
                        throw AssertionError("holder thread did not acquire the lock within 5s")
                    }
                    buildSecurePrefsKey(
                        lock = lock,
                        lockTimeoutMillis = 0L,
                        loadEntry = { null },
                        generateKey = ::zeroKey,
                    )
                },
                onCleanup = {
                    releaseHolder?.countDown()
                    holderThread?.join(5_000)
                },
            )
        }

        private fun interruptedScenario(): LockScenario {
            val lock = ReentrantLock()
            return LockScenario(
                label = "interrupted",
                lock = lock,
                action = {
                    Thread.currentThread().interrupt()
                    try {
                        buildSecurePrefsKey(
                            lock = lock,
                            lockTimeoutMillis = 3_000L,
                            loadEntry = { null },
                            generateKey = ::zeroKey,
                        )
                    } finally {
                        Thread.interrupted()
                    }
                },
            )
        }

        private fun wrongEntryScenario(): LockScenario {
            val lock = ReentrantLock()
            return LockScenario(
                label = "wrong-entry-type",
                lock = lock,
                action = {
                    val wrong: KeyStore.Entry = mockk<KeyStore.PrivateKeyEntry>()
                    buildSecurePrefsKey(
                        lock = lock,
                        lockTimeoutMillis = 3_000L,
                        loadEntry = { wrong },
                        generateKey = ::zeroKey,
                    )
                },
            )
        }

        private fun loadEntryThrowsScenario(): LockScenario {
            val lock = ReentrantLock()
            return LockScenario(
                label = "loadEntry-throws-KeyStoreException",
                lock = lock,
                action = {
                    buildSecurePrefsKey(
                        lock = lock,
                        lockTimeoutMillis = 3_000L,
                        loadEntry = { throw KeyStoreException("simulated keystore failure") },
                        generateKey = ::zeroKey,
                    )
                },
            )
        }

        private fun generateThrowsScenario(): LockScenario {
            val lock = ReentrantLock()
            return LockScenario(
                label = "generateKey-throws-IOException",
                lock = lock,
                action = {
                    buildSecurePrefsKey(
                        lock = lock,
                        lockTimeoutMillis = 3_000L,
                        loadEntry = { null },
                        generateKey = { throw IOException("keystore daemon died") },
                    )
                },
            )
        }

        private fun successExistingEntryScenario(): LockScenario {
            val lock = ReentrantLock()
            return LockScenario(
                label = "success-existing-entry",
                lock = lock,
                action = {
                    val entry = KeyStore.SecretKeyEntry(zeroKey())
                    buildSecurePrefsKey(
                        lock = lock,
                        lockTimeoutMillis = 3_000L,
                        loadEntry = { entry },
                        generateKey = ::zeroKey,
                    )
                },
            )
        }

        private fun successGenerateScenario(): LockScenario {
            val lock = ReentrantLock()
            return LockScenario(
                label = "success-generate",
                lock = lock,
                action = {
                    buildSecurePrefsKey(
                        lock = lock,
                        lockTimeoutMillis = 3_000L,
                        loadEntry = { null },
                        generateKey = ::zeroKey,
                    )
                },
            )
        }

        @JvmStatic
        fun failureScenarios(): List<Arguments> =
            listOf(
                    timeoutScenario(),
                    interruptedScenario(),
                    wrongEntryScenario(),
                    loadEntryThrowsScenario(),
                    generateThrowsScenario(),
                )
                .map { scenario ->
                    val wrapped = Runnable {
                        try {
                            scenario.run()
                        } finally {
                            scenario.cleanup()
                        }
                    }
                    Arguments.of(scenario.label, wrapped)
                }

        @JvmStatic
        fun allScenarios(): List<Arguments> =
            listOf(
                    successExistingEntryScenario(),
                    successGenerateScenario(),
                    timeoutScenario(),
                    interruptedScenario(),
                    wrongEntryScenario(),
                    loadEntryThrowsScenario(),
                    generateThrowsScenario(),
                )
                .map { Arguments.of(it.label, it) }
    }
}
