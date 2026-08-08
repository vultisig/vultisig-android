package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolStatsJson
import com.vultisig.wallet.data.models.ThorChainLpPosition
import com.vultisig.wallet.data.utils.NetworkException
import java.io.IOException
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

private const val POOL_STATUS_AVAILABLE = "available"

/**
 * The outcome of an LP lookup across several pools.
 *
 * [failedPools] exists because a pool that errored and a pool the user simply has no liquidity in
 * both end up absent from [positions]. A caller that sums positions into a total has to tell those
 * apart: treating a failed pool as zero silently understates the total with nothing on screen to
 * say a value is missing.
 */
data class ThorChainLpPositions(
    val positions: List<ThorChainLpPosition> = emptyList(),
    val failedPools: Set<String> = emptySet(),
)

interface GetThorChainLpPositionsUseCase {
    /**
     * Fetches the list of "available" THORChain pools. Exposed so callers that already need the
     * pool list (e.g. to populate a selection dialog) can pass it back into [invoke] and avoid the
     * second API call that would otherwise happen on cold start.
     */
    suspend fun fetchAvailablePools(period: String? = null): List<ThorChainPoolStatsJson>

    /**
     * Fetches the user's THORChain LP positions across all available pools.
     *
     * @param runeAddress the user's RUNE address — tried first for every pool.
     * @param assetAddressesByPool optional fallback addresses keyed by pool id (e.g. `"BTC.BTC" ->
     *   "bc1q..."`). Used when an LP record is keyed by the pool's non-RUNE side rather than RUNE.
     *   Without this, asset-side LPs degrade into placeholders with Remove disabled.
     * @param availablePools optional pre-fetched pool list; pass it when the caller already has the
     *   list to avoid a duplicate `getPoolStats` API call. When `null`, the use case fetches it
     *   itself (using [period] to choose the APR window).
     */
    suspend operator fun invoke(
        runeAddress: String,
        assetAddressesByPool: Map<String, String> = emptyMap(),
        period: String? = null,
        availablePools: List<ThorChainPoolStatsJson>? = null,
    ): ThorChainLpPositions
}

internal class GetThorChainLpPositionsUseCaseImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : GetThorChainLpPositionsUseCase {

    override suspend fun fetchAvailablePools(period: String?): List<ThorChainPoolStatsJson> =
        thorChainApi.getPoolStats(period).filterAvailable()

    private fun List<ThorChainPoolStatsJson>.filterAvailable(): List<ThorChainPoolStatsJson> =
        filter {
            it.status.equals(POOL_STATUS_AVAILABLE, ignoreCase = true)
        }

    override suspend fun invoke(
        runeAddress: String,
        assetAddressesByPool: Map<String, String>,
        period: String?,
        availablePools: List<ThorChainPoolStatsJson>?,
    ): ThorChainLpPositions {
        val pools = availablePools?.filterAvailable() ?: fetchAvailablePools(period)
        val results = coroutineScope {
            pools
                .map { pool ->
                    async {
                        pool to fetchPosition(pool, runeAddress, assetAddressesByPool[pool.asset])
                    }
                }
                .awaitAll()
        }

        return ThorChainLpPositions(
            positions =
                results.mapNotNull { (_, result) -> (result as? PoolFetch.Loaded)?.position },
            failedPools =
                results.mapNotNullTo(mutableSetOf()) { (pool, result) ->
                    pool.asset.takeIf { result is PoolFetch.Failed }
                },
        )
    }

    /**
     * A pool's lookup either completed — with or without a position — or errored. Kept distinct so
     * the caller isn't left reading an absent position as an empty one.
     */
    private sealed interface PoolFetch {
        data class Loaded(val position: ThorChainLpPosition?) : PoolFetch

        data object Failed : PoolFetch
    }

    private suspend fun fetchPosition(
        pool: ThorChainPoolStatsJson,
        runeAddress: String,
        assetAddress: String?,
    ): PoolFetch {
        val lp =
            try {
                thorChainApi.getLiquidityProvider(pool.asset, runeAddress)
                    ?: assetAddress?.let { thorChainApi.getLiquidityProvider(pool.asset, it) }
            } catch (e: IOException) {
                Timber.w(e, "Failed to fetch LP position for pool %s", pool.asset)
                return PoolFetch.Failed
            } catch (e: NetworkException) {
                Timber.w(e, "Failed to fetch LP position for pool %s", pool.asset)
                return PoolFetch.Failed
            } ?: return PoolFetch.Loaded(null)

        val units = lp.units.toBigIntegerOrNull() ?: BigInteger.ZERO
        if (units.signum() == 0) return PoolFetch.Loaded(null)

        return PoolFetch.Loaded(
            ThorChainLpPosition(
                pool = pool.asset,
                units = units,
                runeRedeemValue = lp.runeRedeemValue.toBigIntegerOrNull() ?: BigInteger.ZERO,
                assetRedeemValue = lp.assetRedeemValue.toBigIntegerOrNull() ?: BigInteger.ZERO,
                annualPercentageRate = pool.annualPercentageRate?.toFiniteDoubleOrNull(),
            )
        )
    }

    // Midgard returns "NaN" for periods with insufficient history; treat as missing.
    private fun String.toFiniteDoubleOrNull(): Double? = toDoubleOrNull()?.takeIf { it.isFinite() }
}
