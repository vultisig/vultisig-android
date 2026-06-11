package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.sources.AppDataStore
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * App-wide, per-chain custom RPC endpoint overrides (#4787).
 *
 * The persisted source of truth is a single DataStore entry holding a JSON `{chainId: url}` map. On
 * top of it the repository keeps a **thread-safe in-memory mirror** ([ConcurrentHashMap]) so the
 * networking layer can resolve an override with a *synchronous, non-suspending* read ([urlFor]) —
 * the API factories build their RPC client off the main thread and are not suspend functions, so
 * they cannot await DataStore. `null` from [urlFor] means "no override" and the caller keeps its
 * hardcoded default, guaranteeing byte-identical default behaviour when nothing is set.
 *
 * The mirror is hydrated from disk once at construction (the repo is a [Singleton], so this happens
 * once per process and survives relaunch) and kept in sync with every [setOverride] /
 * [clearOverride]. Hydration runs fire-and-forget on [Dispatchers.IO] so [urlFor] stays a pure,
 * non-blocking map read: it is reached both from the off-main networking factories and from
 * main-thread `viewModelScope.launch` callers (e.g. `GasSettingsViewModel`), so it must never
 * block. If the startup read fails the mirror stays empty and callers fall back to their hardcoded
 * default, preserving byte-identical default behaviour. The brief window before hydration completes
 * likewise resolves to defaults; the eager IO read closes it well before the user reaches any
 * networking.
 */
interface CustomRpcRepository {
    /** Reactive view of all overrides, keyed by chain. Empty when none are set. */
    val overrides: Flow<Map<Chain, String>>

    /**
     * Synchronous, thread-safe lookup of the override URL for [chain], or `null` when unset. Safe
     * to call from any thread, including the off-main networking path. Never touches DataStore.
     */
    fun urlFor(chain: Chain): String?

    suspend fun setOverride(chain: Chain, url: String)

    suspend fun clearOverride(chain: Chain)
}

@Singleton
internal class CustomRpcRepositoryImpl
@Inject
constructor(private val dataStore: AppDataStore, private val json: Json) : CustomRpcRepository {

    private val mirror = ConcurrentHashMap<ChainId, String>()

    // Serializes read-modify-write of the persisted map so concurrent set/clear (and startup
    // hydration) can't clobber each other. DataStore.set is last-writer-wins on its own.
    private val writeLock = Mutex()

    // True once the mirror reflects disk — set by either startup hydration or a set/clear write.
    // Lets a late-running hydration skip rather than clobber a newer override.
    @Volatile private var hydrated = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Eagerly hydrate the mirror off the main thread so urlFor() never has to block on disk.
        scope.launch { hydrate() }
    }

    override val overrides: Flow<Map<Chain, String>> =
        dataStore.readData(KEY_OVERRIDES, "").map(::decode)

    override fun urlFor(chain: Chain): String? = mirror[chain.id]

    // Populate the mirror from disk once. A read failure leaves the mirror empty, so urlFor()
    // returns null and callers keep their default endpoint (byte-identical-default guarantee).
    private suspend fun hydrate() =
        writeLock.withLock {
            if (hydrated) return@withLock
            runCatching { replaceMirror(readPersisted()) }
                .onFailure { Timber.w(it, "Failed to hydrate custom RPC overrides") }
            hydrated = true
        }

    override suspend fun setOverride(chain: Chain, url: String) = mutate {
        it + (chain.id to url.trim())
    }

    override suspend fun clearOverride(chain: Chain) = mutate { it - chain.id }

    // Read-modify-write against the persisted store (the source of truth), then replace the mirror
    // with the authoritative result. Basing the new map on the store rather than the mirror avoids
    // losing entries written by another instance before this one finished hydrating.
    private suspend fun mutate(transform: (Map<ChainId, String>) -> Map<ChainId, String>) =
        writeLock.withLock {
            val next = transform(readPersisted())
            dataStore.set(KEY_OVERRIDES, json.encodeToString(next))
            replaceMirror(next)
            // The mirror now reflects disk; skip the lazy first-read so it can't clobber this
            // write.
            hydrated = true
        }

    private suspend fun readPersisted(): Map<ChainId, String> =
        decodeRaw(dataStore.readData(KEY_OVERRIDES, "").first())

    private fun replaceMirror(map: Map<ChainId, String>) {
        mirror.keys.retainAll(map.keys)
        mirror.putAll(map)
    }

    private fun decode(raw: String): Map<Chain, String> =
        decodeRaw(raw)
            .mapNotNull { (id, url) ->
                runCatching { Chain.fromRaw(id) }.getOrNull()?.let { it to url }
            }
            .toMap()

    private fun decodeRaw(raw: String): Map<ChainId, String> {
        if (raw.isBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, String>>(raw)
        } catch (e: Exception) {
            Timber.w(e, "Failed to decode custom RPC overrides")
            emptyMap()
        }
    }

    private companion object {
        val KEY_OVERRIDES = stringPreferencesKey("custom_rpc_overrides")
    }
}
