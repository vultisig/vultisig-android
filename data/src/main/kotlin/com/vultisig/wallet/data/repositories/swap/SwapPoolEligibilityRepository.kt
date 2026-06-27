package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.swapAssetName
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Live THORChain / MayaChain swap eligibility derived from each protocol's `Available` pools.
 *
 * Pools are fetched on-device (THORChain `/thorchain/pools`, MayaChain `/mayachain/pools`),
 * normalized to `CHAIN.TICKER` keys and cached in memory with stale-while-revalidate semantics:
 * synchronous reads always return the last-good snapshot (empty until the first successful fetch)
 * while a background refresh is triggered whenever the snapshot is older than the cache TTL. A
 * fetch can only ADD eligible routes on top of the static fallback in [SwapProviderTableImpl]; a
 * failed fetch keeps the previous snapshot.
 */
internal interface SwapPoolEligibilityRepository {
    /** True when [ticker] on [chain] has a live `Available` THORChain pool. */
    fun isThorEligible(chain: Chain, ticker: String): Boolean

    /** True when [ticker] on [chain] has a live `Available` MayaChain pool. */
    fun isMayaEligible(chain: Chain, ticker: String): Boolean

    /** Force-refresh both protocol pool snapshots, keeping the last-good set on failure. */
    suspend fun refresh()
}

/** No-op eligibility used as the cold-start default and in tests; never adds a route. */
internal object EmptySwapPoolEligibility : SwapPoolEligibilityRepository {
    override fun isThorEligible(chain: Chain, ticker: String): Boolean = false

    override fun isMayaEligible(chain: Chain, ticker: String): Boolean = false

    override suspend fun refresh() = Unit
}

@Singleton
internal class SwapPoolEligibilityRepositoryImpl
@Inject
constructor(private val thorChainApi: ThorChainApi, private val mayaChainApi: MayaChainApi) :
    SwapPoolEligibilityRepository {

    @Volatile private var thorPools: Set<String> = emptySet()
    @Volatile private var mayaPools: Set<String> = emptySet()
    @Volatile private var lastRefreshMs: Long = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRefreshing = AtomicBoolean(false)

    override fun isThorEligible(chain: Chain, ticker: String): Boolean {
        ensureFresh()
        return key(chain, ticker) in thorPools
    }

    override fun isMayaEligible(chain: Chain, ticker: String): Boolean {
        ensureFresh()
        return key(chain, ticker) in mayaPools
    }

    override suspend fun refresh() {
        if (!isRefreshing.compareAndSet(false, true)) return
        try {
            refreshInternal()
        } finally {
            isRefreshing.set(false)
        }
    }

    private fun ensureFresh() {
        if (System.currentTimeMillis() - lastRefreshMs < CACHE_TTL_MS) return
        if (!isRefreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                refreshInternal()
            } finally {
                isRefreshing.set(false)
            }
        }
    }

    private suspend fun refreshInternal() {
        var anySuccess = false
        runCatching { fetchThorPools() }
            .onSuccess {
                thorPools = it
                anySuccess = true
            }
            .onFailure { Timber.w(it, "THORChain pools refresh failed; keeping last-good") }
        runCatching { fetchMayaPools() }
            .onSuccess {
                mayaPools = it
                anySuccess = true
            }
            .onFailure { Timber.w(it, "MayaChain pools refresh failed; keeping last-good") }
        if (anySuccess) lastRefreshMs = System.currentTimeMillis()
    }

    private suspend fun fetchThorPools(): Set<String> =
        thorChainApi
            .getPools()
            .asSequence()
            .filter { it.status.equals(STATUS_AVAILABLE, ignoreCase = true) }
            .mapNotNull { normalize(it.asset) }
            .toSet()

    private suspend fun fetchMayaPools(): Set<String> =
        mayaChainApi
            .getMayaNodePools()
            .asSequence()
            .filter { it.status.equals(STATUS_AVAILABLE, ignoreCase = true) }
            .mapNotNull { normalize(it.asset) }
            .toSet()

    /**
     * Normalizes a pool asset id (`ETH.USDT-0xdAC17…`) to a `CHAIN.TICKER` key, or null if
     * malformed.
     */
    private fun normalize(assetId: String): String? {
        val dot = assetId.indexOf('.')
        if (dot <= 0 || dot >= assetId.length - 1) return null
        val chainPart = assetId.substring(0, dot).uppercase()
        val tickerPart = assetId.substring(dot + 1).substringBefore('-').uppercase()
        if (tickerPart.isBlank()) return null
        return "$chainPart.$tickerPart"
    }

    private fun key(chain: Chain, ticker: String): String =
        "${chain.swapAssetName().uppercase()}.${ticker.uppercase()}"

    private companion object {
        const val STATUS_AVAILABLE = "available"
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
