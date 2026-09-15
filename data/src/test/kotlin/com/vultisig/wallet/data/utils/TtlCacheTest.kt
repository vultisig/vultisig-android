@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.utils

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class TtlCacheTest {

    @Test
    fun `caches a fresh value without re-invoking the loader`() = runTest {
        val timeSource = TestTimeSource()
        val cache = TtlCache<String, Int>(timeSource)
        val loadCount = AtomicInteger(0)

        val first =
            cache.getOrPut("k", ttl = 1.seconds) {
                loadCount.incrementAndGet()
                42
            }
        timeSource += 500.milliseconds
        val second =
            cache.getOrPut("k", ttl = 1.seconds) {
                loadCount.incrementAndGet()
                99
            }

        assertEquals(42, first)
        assertEquals(42, second)
        assertEquals(1, loadCount.get())
    }

    @Test
    fun `re-invokes the loader once the entry has expired`() = runTest {
        val timeSource = TestTimeSource()
        val cache = TtlCache<String, Int>(timeSource)
        val loadCount = AtomicInteger(0)

        cache.getOrPut("k", ttl = 1.seconds) {
            loadCount.incrementAndGet()
            42
        }
        timeSource += 1_001.milliseconds
        val afterExpiry =
            cache.getOrPut("k", ttl = 1.seconds) {
                loadCount.incrementAndGet()
                99
            }

        assertEquals(99, afterExpiry)
        assertEquals(2, loadCount.get())
    }

    @Test
    fun `coalesces concurrent callers for the same key into a single load`() = runTest {
        val cache = TtlCache<String, Int>()
        val loadCount = AtomicInteger(0)

        val result = coroutineScope {
            val a = async {
                cache.getOrPut("k", ttl = 1.seconds) {
                    loadCount.incrementAndGet()
                    delay(100)
                    7
                }
            }
            val b = async {
                cache.getOrPut("k", ttl = 1.seconds) {
                    loadCount.incrementAndGet()
                    delay(100)
                    8
                }
            }
            listOf(a.await(), b.await())
        }

        assertEquals(listOf(7, 7), result)
        assertEquals(1, loadCount.get())
    }

    @Test
    fun `peekStale returns the last cached value even after expiry`() = runTest {
        val cache = TtlCache<String, Int>()

        cache.getOrPut("k", ttl = 1.seconds) { 42 }

        assertEquals(42, cache.peekStale("k"))
    }

    @Test
    fun `peekStale returns null when the key was never cached`() = runTest {
        val cache = TtlCache<String, Int>()

        assertEquals(null, cache.peekStale("missing"))
    }

    @Test
    fun `a failing loader propagates to the caller and does not poison the cache`() = runTest {
        val cache = TtlCache<String, Int>()

        assertFailsWith<IllegalStateException> {
            cache.getOrPut("k", ttl = 1.seconds) { error("boom") }
        }

        // A subsequent call retries the loader rather than replaying the failure.
        val value = cache.getOrPut("k", ttl = 1.seconds) { 42 }
        assertEquals(42, value)
    }

    @Test
    fun `treats now equal to expiresAt as already expired`() = runTest {
        val timeSource = TestTimeSource()
        val cache = TtlCache<String, Int>(timeSource)
        val loadCount = AtomicInteger(0)

        cache.getOrPut("k", ttl = 1.seconds) {
            loadCount.incrementAndGet()
            42
        }
        timeSource += 1.seconds
        val atExactExpiry =
            cache.getOrPut("k", ttl = 1.seconds) {
                loadCount.incrementAndGet()
                99
            }

        assertEquals(99, atExactExpiry)
        assertEquals(2, loadCount.get())
    }

    @Test
    fun `bases the entry's expiry on a clock reading taken after the loader completes, not before`() =
        runTest {
            val timeSource = TestTimeSource()
            val cache = TtlCache<String, Int>(timeSource)

            cache.getOrPut("k", ttl = 1.seconds) {
                timeSource += 500.milliseconds // the loader itself takes 500ms to resolve
                42
            }

            // A call-start mark would have set expiresAt to 0 + 1_000 = 1_000, making this lookup
            // at now=1_200 see the entry as already expired. Marking again after the loader
            // completes sets expiresAt to 500 + 1_000 = 1_500, so it's still fresh.
            timeSource += 700.milliseconds
            val loadCount = AtomicInteger(0)
            val cachedValue =
                cache.getOrPut("k", ttl = 1.seconds) {
                    loadCount.incrementAndGet()
                    99
                }

            assertEquals(42, cachedValue)
            assertEquals(0, loadCount.get())
        }

    @Test
    fun `a follower retries its own fetch instead of failing when the coalesced leader is cancelled`() =
        runTest {
            val cache = TtlCache<String, Int>()
            val leaderStarted = CompletableDeferred<Unit>()

            val leaderJob = launch {
                cache.getOrPut("k", ttl = 1.seconds) {
                    leaderStarted.complete(Unit)
                    delay(10_000)
                    1
                }
            }

            leaderStarted.await()

            val followerResult = async { cache.getOrPut("k", ttl = 1.seconds) { 2 } }
            runCurrent() // let the follower reach Lookup.Await before the leader is cancelled

            leaderJob.cancel()
            leaderJob.join()

            // The leader's cancellation isn't a real outcome for the follower to fail open on —
            // it retries its own fetch rather than silently defeating the fail-open contract.
            assertEquals(2, followerResult.await())
        }
}
