package com.vultisig.wallet.data.utils

// Simple Cache Implementation
// Up to the caller to implement mutex or thread safety when required
class SimpleCache<K, V>(
    private val defaultExpirationMs: Long = 5 * 60 * 1000 // 5 minutes default
) {
    private data class CacheEntry<V>(
        val value: V,
        val expiresAt: Long
    )

    private val cache = mutableMapOf<K, CacheEntry<V>>()

    fun get(key: K): V? {
        val entry = cache[key] ?: return null

        return if (System.currentTimeMillis() < entry.expiresAt) {
            entry.value
        } else {
            cache.remove(key)
            null
        }
    }

    fun put(key: K, value: V, customExpirationMs: Long? = null) {
        val expiration = customExpirationMs ?: defaultExpirationMs
        cache[key] = CacheEntry(
            value = value,
            expiresAt = System.currentTimeMillis() + expiration
        )
    }

    suspend fun getOrPut(key: K, compute: suspend () -> V): V {
        get(key)?.let { return it }
        
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
        val currentTime = System.currentTimeMillis()
        cache.entries.removeIf { it.value.expiresAt <= currentTime }
    }

    fun size(): Int = cache.size

    fun containsKey(key: K): Boolean = cache.containsKey(key)

    fun hasValidEntry(key: K): Boolean {
        val entry = cache[key] ?: return false
        return System.currentTimeMillis() < entry.expiresAt
    }
}