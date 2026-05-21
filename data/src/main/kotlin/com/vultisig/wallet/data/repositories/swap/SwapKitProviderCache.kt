package com.vultisig.wallet.data.repositories.swap

import androidx.annotation.VisibleForTesting
import com.vultisig.wallet.data.api.models.quotes.SwapKitProvidersResponseJson
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 24h in-memory cache for SwapKit `/providers` enablement data. Phase 1 source chains (EVM +
 * Solana) are derived by unioning every provider's `enabledChainIds` and mapping back to Vultisig's
 * [Chain] enum.
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
    @VisibleForTesting internal var clock: Clock = Clock { java.lang.System.currentTimeMillis() }

    private val mutex = Mutex()
    @Volatile private var enabledChains: Set<Chain> = emptySet()
    @Volatile private var fetchedAtMillis: Long = 0

    override suspend fun isEnabled(chain: Chain): Boolean {
        val cached = ensureFresh() ?: return false
        return chain in cached
    }

    override suspend fun invalidate() =
        mutex.withLock {
            fetchedAtMillis = 0
            enabledChains = emptySet()
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
            runCatching { api.providers() }
                .map { response ->
                    val chains = response.toEnabledChains()
                    enabledChains = chains
                    fetchedAtMillis = nowInner
                    chains
                }
                .getOrNull()
        }
    }

    private fun SwapKitProvidersResponseJson.toEnabledChains(): Set<Chain> =
        providers.flatMap { it.enabledChainIds }.mapNotNull { swapKitChainToVultisig(it) }.toSet()

    companion object {
        /** Cache TTL — 24h, matching the iOS SwapKit Phase 1 implementation. */
        private const val TTL_MILLIS: Long = 24L * 60L * 60L * 1000L

        /**
         * Maps a SwapKit V3 chain identifier (e.g. `"ETH"`, `"BSC"`) to Vultisig's [Chain] enum.
         * Returns `null` for chains not yet supported in Phase 1 — those are filtered out before
         * `isEnabled` is checked anyway via [SwapProviderTable], so a `null` here just keeps the
         * cache focused on the chains we actually quote against.
         */
        internal fun swapKitChainToVultisig(swapKitChain: String): Chain? =
            when (swapKitChain.uppercase()) {
                "ETH",
                "ETHEREUM" -> Chain.Ethereum
                "BSC",
                "BNB" -> Chain.BscChain
                "AVAX",
                "AVALANCHE" -> Chain.Avalanche
                "ARB",
                "ARBITRUM" -> Chain.Arbitrum
                "OP",
                "OPTIMISM" -> Chain.Optimism
                "BASE" -> Chain.Base
                "MATIC",
                "POL",
                "POLYGON" -> Chain.Polygon
                "SOL",
                "SOLANA" -> Chain.Solana
                else -> null
            }
    }
}
