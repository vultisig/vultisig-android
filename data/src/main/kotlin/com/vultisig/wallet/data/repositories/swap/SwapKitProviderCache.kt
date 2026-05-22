package com.vultisig.wallet.data.repositories.swap

import androidx.annotation.VisibleForTesting
import com.vultisig.wallet.data.api.models.quotes.SwapKitProvidersResponseJson
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * 24h in-memory cache for SwapKit `/providers` enablement data. Phase 1 source chains (EVM +
 * Solana) are derived by unioning every provider's `supportedChainIds` and mapping back to
 * Vultisig's [Chain] enum.
 *
 * The cache is intentionally process-scoped (no disk persistence) — a cold launch every 24h is
 * cheap, and stale enablement is the failure mode we want to avoid most.
 */
interface SwapKitProviderCache {
    /**
     * Returns `true` when SwapKit currently routes on [chain]. Lazily refreshes the underlying
     * `/providers` response on first call or when the cache TTL has elapsed; any error while
     * refreshing surfaces `false` (fail-closed: better to skip SwapKit than offer a bad quote).
     */
    suspend fun isEnabled(chain: Chain): Boolean

    /** Force-invalidates the cached response. Mainly for tests and developer tooling. */
    suspend fun invalidate()
}

/**
 * In-memory [SwapKitProviderCache] with a 24h TTL, mutex-guarded refresh, and fail-closed reads.
 */
@Singleton
internal class SwapKitProviderCacheImpl @Inject constructor(private val api: SwapKitApi) :
    SwapKitProviderCache {

    /** Minimal clock seam so tests can advance time without sleeping. */
    fun interface Clock {
        fun nowMillis(): Long
    }

    /**
     * Overridable clock so tests can advance time deterministically; production uses
     * `System.currentTimeMillis`. Not [Inject]ed to keep Hilt wiring trivial.
     */
    @VisibleForTesting internal var clock: Clock = Clock { System.currentTimeMillis() }

    private val mutex = Mutex()
    @Volatile private var enabledChains: Set<Chain> = emptySet()
    @Volatile private var fetchedAtMillis: Long = 0

    override suspend fun isEnabled(chain: Chain): Boolean {
        val cached = ensureFresh() ?: return false
        return chain in cached
    }

    override suspend fun invalidate() =
        mutex.withLock {
            // Publish through `fetchedAtMillis` last to match the refresh-path ordering
            // (chains-then-timestamp). A reader on the fast path that sees the reset timestamp is
            // then guaranteed to see the cleared chains too — without this swap the two volatile
            // writes have no joint happens-before and a brief stale read is observable.
            enabledChains = emptySet()
            fetchedAtMillis = 0
        }

    private suspend fun ensureFresh(): Set<Chain>? {
        val now = clock.nowMillis()
        if (fetchedAtMillis != 0L && (now - fetchedAtMillis) < TTL_MILLIS) {
            return enabledChains
        }
        return mutex.withLock {
            val nowInner = clock.nowMillis()
            if (fetchedAtMillis != 0L && (nowInner - fetchedAtMillis) < TTL_MILLIS) {
                return@withLock enabledChains
            }
            try {
                val response = api.providers()
                val chains = response.toEnabledChains()
                enabledChains = chains
                fetchedAtMillis = nowInner
                chains
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun SwapKitProvidersResponseJson.toEnabledChains(): Set<Chain> =
        flatMap { it.supportedChainIds }
            .mapNotNull { id ->
                swapKitChainToVultisig(id).also {
                    if (it == null) Timber.w("Unknown SwapKit chain id: %s", id)
                }
            }
            .toSet()

    companion object {
        /** Cache TTL — 24h. */
        private const val TTL_MILLIS: Long = 24L * 60L * 60L * 1000L

        /**
         * Maps a SwapKit V3 `supportedChainIds` entry to Vultisig's [Chain] enum. Per the V3 docs
         * these are EVM chain ids as decimal strings (`"1"`, `"56"`, `"137"`, `"42161"`, ...) and
         * lowercase named ids for non-EVM (`"solana"`, `"bitcoin"`, ...). Returns `null` for chains
         * not yet supported in Phase 1.
         */
        internal fun swapKitChainToVultisig(swapKitChain: String): Chain? =
            when (swapKitChain.lowercase()) {
                "1",
                "ethereum" -> Chain.Ethereum
                "56",
                "bsc",
                "bnb" -> Chain.BscChain
                "43114",
                "avalanche" -> Chain.Avalanche
                "42161",
                "arbitrum" -> Chain.Arbitrum
                "10",
                "optimism" -> Chain.Optimism
                "8453",
                "base" -> Chain.Base
                "137",
                "polygon",
                "matic" -> Chain.Polygon
                "solana" -> Chain.Solana
                else -> null
            }
    }
}
