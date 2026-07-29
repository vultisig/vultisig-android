package com.vultisig.wallet.data.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Generic in-memory TTL cache with per-key in-flight request coalescing: concurrent callers for the
 * same key while a fetch is already running all await that same fetch instead of firing duplicate
 * loads (e.g. a double-tap on a chart range).
 */
class TtlCache<K : Any, V> {
    private class Entry<V>(val value: V, val expiresAt: Long)

    /**
     * A coalesced follower must never see the initiating caller's own [CancellationException] as if
     * it were its own cancellation (that would make the follower's coroutine silently complete as
     * Cancelled per structured-concurrency rules, even though it was never itself cancelled).
     * Wrapping it in a plain [Exception] lets a follower's [CompletableDeferred.await] fail open
     * instead.
     */
    private class CoalescedFetchCancelledException(cause: Throwable) :
        Exception("A concurrent fetch for this key was cancelled before completing", cause)

    private val mutex = Mutex()
    private val entries = mutableMapOf<K, Entry<V>>()
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    private sealed interface Lookup<out V> {
        data class Hit<V>(val value: V) : Lookup<V>

        data class Await<V>(val deferred: CompletableDeferred<V>) : Lookup<V>

        data class Start<V>(val deferred: CompletableDeferred<V>) : Lookup<V>
    }

    /**
     * Returns the cached value for [key] if it hasn't expired, otherwise runs [loader] (coalescing
     * concurrent callers for the same key) and caches the result for [ttlMillis].
     *
     * [now] defaults to a monotonic clock (immune to wall-clock/NTP adjustments) rather than
     * [System.currentTimeMillis], so TTL expiry tracks elapsed time, not wall-clock time.
     */
    suspend fun getOrPut(
        key: K,
        ttlMillis: Long,
        now: Long = System.nanoTime() / 1_000_000,
        loader: suspend () -> V,
    ): V {
        val lookup =
            mutex.withLock {
                val fresh = entries[key]?.takeIf { it.expiresAt > now }
                when {
                    fresh != null -> Lookup.Hit(fresh.value)
                    inFlight[key] != null -> Lookup.Await(inFlight.getValue(key))
                    else -> {
                        val deferred = CompletableDeferred<V>()
                        inFlight[key] = deferred
                        Lookup.Start(deferred)
                    }
                }
            }

        return when (lookup) {
            is Lookup.Hit -> lookup.value
            is Lookup.Await -> lookup.deferred.await()
            is Lookup.Start -> {
                try {
                    val value = loader()
                    mutex.withLock {
                        entries[key] = Entry(value, now + ttlMillis)
                        inFlight.remove(key)
                    }
                    lookup.deferred.complete(value)
                    value
                } catch (e: CancellationException) {
                    // Cleanup must run even if this coroutine is already cancelling, otherwise the
                    // key is orphaned in inFlight forever and every future caller awaits a deferred
                    // that will never complete.
                    withContext(NonCancellable) { mutex.withLock { inFlight.remove(key) } }
                    lookup.deferred.completeExceptionally(CoalescedFetchCancelledException(e))
                    throw e
                } catch (e: Throwable) {
                    withContext(NonCancellable) { mutex.withLock { inFlight.remove(key) } }
                    lookup.deferred.completeExceptionally(e)
                    throw e
                }
            }
        }
    }

    /** Returns the last cached value for [key] regardless of expiry, or null if never cached. */
    suspend fun peekStale(key: K): V? = mutex.withLock { entries[key]?.value }
}
