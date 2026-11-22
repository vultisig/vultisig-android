package com.vultisig.wallet.data.utils

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Process-only handoff. Expiry tasks retain keys and markers, never the values. */
class TransientStore<K, V>(private val capacity: Int, private val lifetimeMillis: Long) {
    private class Entry<V>(val value: V, val marker: Any, val expiration: ScheduledFuture<*>)

    private val entries = LinkedHashMap<K, Entry<V>>()

    @Synchronized
    fun put(key: K, value: V) {
        remove(key)
        if (entries.size == capacity) remove(entries.keys.first())
        val marker = Any()
        val expiration = scheduler.schedule({ expire(key, marker) }, lifetimeMillis, TimeUnit.MILLISECONDS)
        entries[key] = Entry(value, marker, expiration)
    }

    @Synchronized
    fun get(key: K): V? = entries[key]?.value

    @Synchronized
    fun remove(key: K): V? =
        entries.remove(key)?.let { entry ->
            entry.expiration.cancel(false)
            entry.value
        }

    @Synchronized
    private fun expire(key: K, marker: Any) {
        if (entries[key]?.marker === marker) entries.remove(key)
    }

    companion object {
        private val scheduler =
            ScheduledThreadPoolExecutor(1) { runnable ->
                    Thread(runnable, "vault-handoff-expiry").apply { isDaemon = true }
                }
                .apply { removeOnCancelPolicy = true }
    }
}
