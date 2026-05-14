package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.ThorChainApi
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Typed view over THORChain's `/thorchain/mimir` flags. Mimir keys are dynamic on-chain governance
 * values used (among other things) to pause or halt specific actions. THORChain treats any positive
 * integer value for these keys as "on"; absent keys are off.
 *
 * Results are cached for a short TTL so several validators in one screen (e.g. the LP preflight
 * checking global + per-asset + chain LP halt) hit the network only once.
 */
interface ThorMimirRepository {
    /**
     * True when add-liquidity is paused either globally (`PAUSELP`) or for the specific [pool]
     * (`PAUSELP-<CHAIN>-<TICKER>`). [pool] is the canonical THORChain pool identifier (e.g.
     * `ETH.USDT-0xdac17f958d2ee523a2206206994597c13d831ec7`).
     */
    suspend fun isLpPaused(pool: String): Boolean

    /**
     * True when LP activity for the given chain (THORChain inbound-chain identifier, e.g. `ETH`,
     * `BTC`, `BSC`) is halted via either `HALT<CHAIN>LP` or `HALT<CHAIN>CHAIN`.
     */
    suspend fun isLpHalted(chainPrefix: String): Boolean
}

internal class ThorMimirRepositoryImpl @Inject constructor(private val thorChainApi: ThorChainApi) :
    ThorMimirRepository {

    private val mutex = Mutex()
    private var cached: Map<String, Long>? = null
    private var cachedAtMillis: Long = 0L

    override suspend fun isLpPaused(pool: String): Boolean {
        val mimir = mimir()
        if (mimir.isOn(KEY_GLOBAL_LP_PAUSE)) return true
        val key = "$KEY_GLOBAL_LP_PAUSE-${pool.toMimirAssetSuffix()}"
        return mimir.isOn(key)
    }

    override suspend fun isLpHalted(chainPrefix: String): Boolean {
        val mimir = mimir()
        val upper = chainPrefix.uppercase()
        return mimir.isOn("HALT${upper}LP") || mimir.isOn("HALT${upper}CHAIN")
    }

    private suspend fun mimir(): Map<String, Long> =
        mutex.withLock {
            val now = nowMillis()
            val current = cached
            if (current != null && now - cachedAtMillis < TTL_MILLIS) {
                current
            } else {
                val fresh = thorChainApi.getMimir().mapKeys { it.key.uppercase() }
                cached = fresh
                cachedAtMillis = now
                fresh
            }
        }

    private fun nowMillis(): Long = System.currentTimeMillis()

    private fun Map<String, Long>.isOn(key: String): Boolean = (this[key.uppercase()] ?: 0L) > 0L

    /**
     * Mimir per-asset pause keys are formatted as `PAUSELP-<CHAIN>-<TICKER>` (no contract suffix,
     * dashes instead of dots, all uppercase). Convert a canonical pool id (`ETH.USDT-0xdac...`) to
     * that form.
     */
    private fun String.toMimirAssetSuffix(): String {
        val withoutContract = substringBefore('-')
        return withoutContract.replace('.', '-').uppercase()
    }

    private companion object {
        const val TTL_MILLIS = 30_000L
        const val KEY_GLOBAL_LP_PAUSE = "PAUSELP"
    }
}
