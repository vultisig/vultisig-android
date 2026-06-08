package com.vultisig.wallet.data.repositories

import androidx.datastore.preferences.core.stringPreferencesKey
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.sources.AppDataStore
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
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
 * The mirror is hydrated from disk lazily on the first [urlFor] call (the repo is a [Singleton], so
 * this happens once per process and survives relaunch) and kept in sync with every [setOverride] /
 * [clearOverride]. Hydrating on first use — rather than fire-and-forget at construction —
 * guarantees the first networking lookup after process start sees persisted overrides instead of
 * racing an async job. [urlFor] is only reached from the off-main networking path, so the one-time
 * blocking disk read it performs never touches the main thread.
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

    // Serializes read-modify-write of the persisted map so concurrent set/clear can't clobber each
    // other (DataStore.set is last-writer-wins on its own).
    private val writeLock = Mutex()

    // Guards the one-time lazy hydration so the disk read happens at most once across threads.
    @Volatile private var hydrated = false
    private val hydrationLock = Any()

    override val overrides: Flow<Map<Chain, String>> =
        dataStore.readData(KEY_OVERRIDES, "").map(::decode)

    override fun urlFor(chain: Chain): String? {
        ensureHydrated()
        return mirror[chain.id]
    }

    // Populate the mirror from disk on first lookup so the very first networking call after process
    // start sees persisted overrides. Runs on the calling (off-main networking) thread; the double-
    // checked flag keeps it to a single blocking read regardless of concurrent callers.
    private fun ensureHydrated() {
        if (hydrated) return
        synchronized(hydrationLock) {
            if (hydrated) return
            runBlocking(Dispatchers.IO) { replaceMirror(readPersisted()) }
            hydrated = true
        }
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
