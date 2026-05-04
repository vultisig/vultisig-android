package com.vultisig.wallet.data.repositories

import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-shot rendezvous between two coroutines that don't share scope: a producer publishes via
 * [respond] and a consumer awaits via [request], using a string id as the meeting key.
 *
 * Order between [request] and [respond] does not matter — the consumer resolves whether [respond]
 * runs before, after, or while it is suspended.
 *
 * Each request consumes the slot for its id, so a subsequent [request] for the same id starts fresh
 * and waits for a new [respond]; a previous exchange's value never leaks into the next one.
 *
 * Concurrent waiters on the same id share a single rendezvous — a single [respond] resolves all of
 * them with the same value. A [respond] published with no current waiter buffers the value for the
 * next [request]; a subsequent [respond] on the same id replaces the buffered value.
 *
 * A `null` return from [request] means [respond] was called with a `null` payload. JVM erasure
 * leaves the type parameter `T` unenforced inside this call, so a wrong-type payload propagates to
 * the call site where Kotlin's `CHECKCAST` raises `ClassCastException` — callers must match `T` to
 * the type their producer publishes via [respond].
 */
interface RequestResultRepository {

    suspend fun <T> request(requestId: String): T?

    suspend fun respond(requestId: String, result: Any?)
}

internal class RequestResultRepositoryImpl @Inject constructor() : RequestResultRepository {

    private val mutex = Mutex()
    private val pending = mutableMapOf<String, CompletableDeferred<Any?>>()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> request(requestId: String): T? {
        val deferred = mutex.withLock { pending.getOrPut(requestId) { CompletableDeferred() } }
        return try {
            deferred.await() as? T
        } finally {
            mutex.withLock {
                if (pending[requestId] === deferred && deferred.isCompleted) {
                    pending.remove(requestId)
                }
            }
        }
    }

    override suspend fun respond(requestId: String, result: Any?) {
        val deferred =
            mutex.withLock {
                val active = pending.remove(requestId)
                if (active != null && !active.isCompleted) {
                    active
                } else {
                    CompletableDeferred<Any?>().also { pending[requestId] = it }
                }
            }
        deferred.complete(result)
    }
}
