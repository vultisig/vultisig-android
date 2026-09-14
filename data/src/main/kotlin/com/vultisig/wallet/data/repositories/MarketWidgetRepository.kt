package com.vultisig.wallet.data.repositories

import android.content.Context
import com.vultisig.wallet.data.api.MarketWidgetApi
import com.vultisig.wallet.data.models.MarketWidgetAsset
import com.vultisig.wallet.data.models.MarketWidgetAssetIdentity
import com.vultisig.wallet.data.models.MarketWidgetQuery
import com.vultisig.wallet.data.models.MarketWidgetResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Market data plus a bounded last-good disk cache for the home-screen widgets.
 *
 * Widgets render from [cached] only; [refresh] is what the background worker (and the widget
 * configuration screen) call to pull fresh data. A failed refresh keeps the previous snapshot,
 * flagged stale, so a widget never blanks out because one poll timed out.
 */
interface MarketWidgetRepository {

    /**
     * Bumps on every cache write. A widget whose Glance session is still alive is only recomposed
     * by `updateAll`, not re-provided, so it watches this to pick up the refresh worker's result.
     */
    val version: StateFlow<Long>

    suspend fun cached(query: MarketWidgetQuery, currency: String): MarketWidgetResult?

    suspend fun refresh(query: MarketWidgetQuery, currency: String): MarketWidgetResult

    suspend fun search(query: String): List<MarketWidgetAssetIdentity>

    /** Cached icon bytes for [asset], or null when none were downloaded (or it has no image). */
    suspend fun icon(asset: MarketWidgetAsset): ByteArray?
}

@Serializable
private data class CacheEntry(
    val assets: List<MarketWidgetAsset>,
    val updatedAt: Long,
    val isStale: Boolean = false,
)

@Singleton
internal class MarketWidgetRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val api: MarketWidgetApi,
    private val json: Json,
) : MarketWidgetRepository {

    private val mutex = Mutex()
    private val _version = MutableStateFlow(0L)
    override val version: StateFlow<Long> = _version.asStateFlow()

    private val cacheDir: File
        get() = File(context.filesDir, CACHE_DIR)

    private val cacheFile: File
        get() = File(cacheDir, CACHE_FILE)

    private val iconDir: File
        get() = File(cacheDir, ICON_DIR)

    override suspend fun cached(query: MarketWidgetQuery, currency: String): MarketWidgetResult? =
        withContext(Dispatchers.IO) {
            mutex
                .withLock { loadEntries()[cacheKey(query, currency)] }
                ?.let {
                    MarketWidgetResult(
                        assets = it.assets,
                        updatedAt = it.updatedAt,
                        isStale = it.isStale,
                    )
                }
        }

    override suspend fun refresh(query: MarketWidgetQuery, currency: String): MarketWidgetResult =
        withContext(Dispatchers.IO) {
            val key = cacheKey(query, currency)
            val previous = mutex.withLock { loadEntries()[key] }
            val assets =
                try {
                    api.markets(query, currency)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Market widget refresh failed for %s", key)
                    return@withContext fallback(key, previous, e)
                }

            // Outside the API catch on purpose: a cache write failure must surface to the worker
            // (so it retries) rather than be reported back as a stale-but-successful result.
            downloadMissingIcons(assets)
            val updatedAt = System.currentTimeMillis()
            store(key, CacheEntry(assets = assets, updatedAt = updatedAt))
            MarketWidgetResult(assets = assets, updatedAt = updatedAt, isStale = false)
        }

    /**
     * Last-good result for a failed refresh. Refreshes for one key can overlap (the worker and the
     * configure screen share this instance), so the entry captured before the request is only
     * marked stale if it is still what the cache holds; a newer snapshot written meanwhile wins.
     */
    private suspend fun fallback(
        key: String,
        previous: CacheEntry?,
        cause: Exception,
    ): MarketWidgetResult {
        val current = mutex.withLock { loadEntries()[key] } ?: previous ?: throw cause
        if (current.updatedAt == previous?.updatedAt && !current.isStale) {
            // Failing to flag staleness is not worth failing the caller over; the data is intact.
            runCatching { store(key, current.copy(isStale = true)) }
                .onFailure { Timber.w(it, "Could not mark market widget cache stale") }
        }
        return MarketWidgetResult(
            assets = current.assets,
            updatedAt = current.updatedAt,
            isStale = current.updatedAt == previous?.updatedAt || current.isStale,
        )
    }

    override suspend fun search(query: String): List<MarketWidgetAssetIdentity> = api.search(query)

    override suspend fun icon(asset: MarketWidgetAsset): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = iconFile(asset.imageUrl ?: return@withContext null)
            runCatching { file.takeIf { it.isFile }?.readBytes() }.getOrNull()
        }

    private suspend fun downloadMissingIcons(assets: List<MarketWidgetAsset>) = coroutineScope {
        assets
            .mapNotNull { asset -> asset.imageUrl?.takeUnless { iconFile(it).isFile } }
            .distinct()
            .map { url ->
                async {
                    val bytes =
                        try {
                            api.icon(url)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.d(e, "Market widget icon download failed")
                            null
                        }
                    if (bytes != null) {
                        runCatching {
                            iconDir.mkdirs()
                            iconFile(url).writeBytes(bytes)
                        }
                    }
                }
            }
            .forEach { it.await() }
    }

    private suspend fun store(key: String, entry: CacheEntry) =
        mutex.withLock {
            val entries = loadEntries().toMutableMap()
            entries[key] = entry
            // Bound the file: one entry per distinct (currency, query) that was ever shown, so
            // evict the oldest once a user has cycled through more than a handful.
            while (entries.size > MAX_ENTRIES) {
                val oldest = entries.minByOrNull { it.value.updatedAt }?.key ?: break
                entries.remove(oldest)
            }
            pruneIcons(entries.values)
            cacheDir.mkdirs()
            val tmp = File(cacheDir, "$CACHE_FILE.tmp")
            tmp.writeText(json.encodeToString(entries))
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.writeText(json.encodeToString(entries))
                tmp.delete()
            }
            // Only after the file is on disk: readers re-read the file on this signal, so bumping
            // it for a write that never landed would just have them re-render the old snapshot.
            _version.update { it + 1 }
        }

    /** Delete icons that no cached entry references any more. */
    private fun pruneIcons(entries: Collection<CacheEntry>) {
        val live =
            entries
                .asSequence()
                .flatMap { it.assets }
                .mapNotNull { it.imageUrl }
                .map { iconFile(it).name }
                .toSet()
        iconDir.listFiles()?.forEach { file -> if (file.name !in live) file.delete() }
    }

    private fun loadEntries(): Map<String, CacheEntry> =
        runCatching {
                val file = cacheFile
                if (!file.isFile) emptyMap()
                else json.decodeFromString<Map<String, CacheEntry>>(file.readText())
            }
            .getOrElse {
                Timber.w(it, "Market widget cache unreadable, discarding")
                emptyMap()
            }

    private fun iconFile(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }.take(32)
        return File(iconDir, name)
    }

    private fun cacheKey(query: MarketWidgetQuery, currency: String) =
        "${currency.lowercase()}-${query.cacheKey}"

    companion object {
        private const val CACHE_DIR = "market_widgets"
        private const val CACHE_FILE = "market-cache.json"
        private const val ICON_DIR = "icons"
        private const val MAX_ENTRIES = 6
    }
}
