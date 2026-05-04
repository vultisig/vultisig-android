package com.vultisig.wallet.data.utils

import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import timber.log.Timber

/**
 * Verifies the contract of [SharedPrefsMasterKeyInitializer.runPrewarmInto]:
 * 1. On success the target is completed with the supplied key.
 * 2. On every non-cancellation throwable the target is completed with null and exactly one WARN log
 *    entry is emitted for that throwable.
 * 3. [CancellationException] is rethrown WITHOUT completing the target.
 * 4. A second call on an already-completed target is a no-op (first-write semantics).
 *
 * Issue #4402: the original `prewarm()` catch-list was `GeneralSecurityException | IOException` —
 * it missed [IllegalStateException] (the lock-timeout `error(...)` at
 * SecureSharedPreferences.kt:39), [IllegalArgumentException], raw [RuntimeException],
 * [InterruptedException], and [NullPointerException]. Test (d) is the canonical regression guard:
 * it MUST fail on origin/main and pass after the fix.
 */
internal class SharedPrefsMasterKeyInitializerTest {

    // ── Log-capture infrastructure ────────────────────────────────────────────

    private data class LogEntry(val priority: Int, val throwable: Throwable?)

    private val capturedLogs = mutableListOf<LogEntry>()

    private val capturingTree =
        object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                capturedLogs += LogEntry(priority, t)
            }
        }

    // android.util.Log.WARN == 5; use the literal to avoid the Android SDK in JVM tests.
    private val WARN = 5

    @AfterEach
    fun tearDown() {
        Timber.uprootAll()
        capturedLogs.clear()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fakeKey(): SecretKey = SecretKeySpec(ByteArray(32), "AES")

    private fun freshTarget(): CompletableDeferred<SecretKey?> = CompletableDeferred()

    // ── (a) Happy path ────────────────────────────────────────────────────────

    @Test
    fun `runPrewarmInto completes target with supplied key on success`() {
        val key = fakeKey()
        val target = freshTarget()

        SharedPrefsMasterKeyInitializer.runPrewarmInto(target) { key }

        assertTrue(target.isCompleted)
        assertSame(key, target.getCompleted())
    }

    // ── (b) GeneralSecurityException ─────────────────────────────────────────

    @Test
    fun `runPrewarmInto completes target with null on GeneralSecurityException`() {
        val target = freshTarget()

        SharedPrefsMasterKeyInitializer.runPrewarmInto(target) {
            throw GeneralSecurityException("test")
        }

        assertTrue(target.isCompleted)
        assertNull(target.getCompleted())
    }

    // ── (c) IOException ───────────────────────────────────────────────────────

    @Test
    fun `runPrewarmInto completes target with null on IOException`() {
        val target = freshTarget()

        SharedPrefsMasterKeyInitializer.runPrewarmInto(target) { throw IOException("test") }

        assertTrue(target.isCompleted)
        assertNull(target.getCompleted())
    }

    // ── (d) THE BUG — IllegalStateException from lock timeout ─────────────────
    // This test MUST fail on origin/main (GSE+IOE-only catch) and pass after the fix.

    @Test
    fun `runPrewarmInto completes target with null on IllegalStateException from lock timeout`() {
        val target = freshTarget()

        SharedPrefsMasterKeyInitializer.runPrewarmInto(target) {
            throw IllegalStateException("Timed out waiting for secure prefs key lock after 3 s")
        }

        assertTrue(target.isCompleted)
        assertNull(target.getCompleted())
    }

    // ── (e) IllegalArgumentException ─────────────────────────────────────────

    @Test
    fun `runPrewarmInto completes target with null on IllegalArgumentException`() {
        val target = freshTarget()

        SharedPrefsMasterKeyInitializer.runPrewarmInto(target) {
            throw IllegalArgumentException("bad spec")
        }

        assertTrue(target.isCompleted)
        assertNull(target.getCompleted())
    }

    // ── (f) Raw RuntimeException ──────────────────────────────────────────────

    @Test
    fun `runPrewarmInto completes target with null on RuntimeException`() {
        val target = freshTarget()

        SharedPrefsMasterKeyInitializer.runPrewarmInto(target) {
            throw RuntimeException("oem keystore null")
        }

        assertTrue(target.isCompleted)
        assertNull(target.getCompleted())
    }

    // ── (g) InterruptedException ──────────────────────────────────────────────

    @Test
    fun `runPrewarmInto completes target with null on InterruptedException`() {
        val target = freshTarget()

        SharedPrefsMasterKeyInitializer.runPrewarmInto(target) { throw InterruptedException() }

        assertTrue(target.isCompleted)
        assertNull(target.getCompleted())
    }

    // ── (h) CancellationException — must NOT be swallowed ────────────────────

    @Test
    fun `runPrewarmInto rethrows CancellationException without completing target`() {
        val target = freshTarget()

        assertThrows<CancellationException> {
            SharedPrefsMasterKeyInitializer.runPrewarmInto(target) {
                throw CancellationException("cancelled")
            }
        }

        assertFalse(target.isCompleted)
        assertFalse(target.isCancelled)
    }

    // ── (i) First-write semantics ─────────────────────────────────────────────

    @Test
    fun `runPrewarmInto preserves first-write on already-completed target`() {
        val preStub = fakeKey()
        val target = freshTarget()
        target.complete(preStub)

        val later = SecretKeySpec(ByteArray(32), "AES")
        SharedPrefsMasterKeyInitializer.runPrewarmInto(target) { later }

        // CompletableDeferred.complete() returns false on the second call; first-write wins.
        assertSame(preStub, target.getCompleted())
    }

    // ── (j) Parameterized invariant: every non-cancellation throwable settles to null ───

    companion object {
        private class CustomUncheckedException : Exception("custom")

        @JvmStatic
        fun nonCancellationThrowables(): List<Throwable> =
            listOf(
                GeneralSecurityException("gse"),
                IOException("ioe"),
                IllegalStateException("ise"),
                IllegalArgumentException("iae"),
                RuntimeException("rte"),
                InterruptedException(),
                NullPointerException("npe"),
                CustomUncheckedException(),
            )
    }

    @ParameterizedTest
    @MethodSource("nonCancellationThrowables")
    fun `every non-cancellation throwable settles target with null`(t: Throwable) {
        val target = freshTarget()

        SharedPrefsMasterKeyInitializer.runPrewarmInto(target) { throw t }

        assertTrue(target.isCompleted, "target must be completed for ${t::class.simpleName}")
        assertNull(target.getCompleted(), "completed value must be null for ${t::class.simpleName}")
    }

    // ── (k) Every failure path logs exactly one WARN entry ────────────────────

    @ParameterizedTest
    @MethodSource("nonCancellationThrowables")
    fun `every failure path logs once via Timber at WARN level`(t: Throwable) {
        Timber.plant(capturingTree)
        val target = freshTarget()

        SharedPrefsMasterKeyInitializer.runPrewarmInto(target) { throw t }

        assertEquals(
            1,
            capturedLogs.size,
            "expected exactly 1 log entry for ${t::class.simpleName}, got ${capturedLogs.size}",
        )
        val entry = capturedLogs.single()
        assertEquals(
            WARN,
            entry.priority,
            "log priority must be WARN (5) for ${t::class.simpleName}",
        )
        assertNotNull(entry.throwable, "log entry must carry the throwable")
        assertSame(
            t,
            entry.throwable,
            "logged throwable must be the same instance for ${t::class.simpleName}",
        )

        // Reset for the next parameterized iteration.
        capturedLogs.clear()
        Timber.uprootAll()
    }
}
