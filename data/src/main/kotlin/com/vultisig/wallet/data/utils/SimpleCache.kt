package com.vultisig.wallet.data.utils

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// Simple Cache Implementation
// Up to the caller to implement mutex or thread safety when required
class SimpleCache<K, V>(
    private val defaultExpiration: Duration = 5.minutes,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private data class CacheEntry<V>(val value: V, val expiresAt: TimeMark)

    private val cache = mutableMapOf<K, CacheEntry<V>>()

    fun get(key: K): V? {
        val entry = cache[key] ?: return null

        return if (entry.expiresAt.hasNotPassedNow()) {
            entry.value
        } else {
            cache.remove(key)
            null
        }
    }

    fun put(key: K, value: V, customExpiration: Duration? = null) {
        val expiration = customExpiration ?: defaultExpiration
        cache[key] = CacheEntry(value = value, expiresAt = timeSource.markNow() + expiration)
    }

    suspend fun getOrPut(key: K, compute: suspend () -> V): V {
        get(key)?.let {
            return it
        }

        val value = compute()
        put(key, value)
        return value
    }

    fun remove(key: K) {
        cache.remove(key)
    }

    fun clear() {
        cache.clear()
    }

    fun cleanUp() {
        cache.entries.removeIf { it.value.expiresAt.hasPassedNow() }
    }

    fun size(): Int = cache.size

    fun containsKey(key: K): Boolean = cache.containsKey(key)

    fun hasValidEntry(key: K): Boolean {
        val entry = cache[key] ?: return false
        return entry.expiresAt.hasNotPassedNow()
    }
}
