package com.vultisig.wallet.data.mediator

internal data class MediatorCacheKey(
    val kind: String,
    val id: String = "",
    val participant: String = "",
    val messageId: String? = null,
)

internal class MediatorCache(
    private val maxBytes: Long = 8 * 1024 * 1024L,
    private val maxEntries: Int = 2_048,
) {
    private data class Entry(val value: String, val weight: Long)

    private val entries = LinkedHashMap<MediatorCacheKey, Entry>()
    private var retainedBytes = 0L

    operator fun get(key: MediatorCacheKey): String? = entries[key]?.value

    /** Check the whole fanout before writing any part of it. */
    fun add(keys: Collection<MediatorCacheKey>, value: String): Boolean {
        val unique = keys.distinct()
        val newCount = unique.count { it !in entries }
        if (entries.size + newCount > maxEntries) return false
        val valueWeight = maxOf(value.length * 2L, value.encodeToByteArray().size.toLong())
        var bytesAfterWrite = retainedBytes
        val weights = unique.map { key ->
            val weight =
                valueWeight +
                    key.toString().encodeToByteArray().size + ENTRY_OVERHEAD_BYTES
            bytesAfterWrite += weight - (entries[key]?.weight ?: 0L)
            weight
        }
        if (bytesAfterWrite > maxBytes) return false

        for ((index, key) in unique.withIndex()) entries[key] = Entry(value, weights[index])
        retainedBytes = bytesAfterWrite
        return true
    }

    fun matching(predicate: (MediatorCacheKey) -> Boolean): List<String> =
        entries.filterKeys(predicate).values.map { it.value }

    fun remove(key: MediatorCacheKey) {
        retainedBytes -= entries.remove(key)?.weight ?: 0L
    }

    fun clear() {
        entries.clear()
        retainedBytes = 0L
    }

    private companion object {
        const val ENTRY_OVERHEAD_BYTES = 128L
    }
}
