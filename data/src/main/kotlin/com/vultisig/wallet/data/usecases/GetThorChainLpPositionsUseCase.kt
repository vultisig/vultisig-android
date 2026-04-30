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
    ): List<ThorChainLpPosition>
}

internal class GetThorChainLpPositionsUseCaseImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : GetThorChainLpPositionsUseCase {

    override suspend fun fetchAvailablePools(period: String?): List<ThorChainPoolStatsJson> =
        thorChainApi.getPoolStats(period).filter {
            it.status.equals(POOL_STATUS_AVAILABLE, ignoreCase = true)
        }

    override suspend fun invoke(
        runeAddress: String,
        assetAddressesByPool: Map<String, String>,
        period: String?,
        availablePools: List<ThorChainPoolStatsJson>?,
    ): List<ThorChainLpPosition> {
        val pools = availablePools ?: fetchAvailablePools(period)
        return coroutineScope {
            pools
                .map { pool ->
                    async { fetchPosition(pool, runeAddress, assetAddressesByPool[pool.asset]) }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    private suspend fun fetchPosition(
        pool: ThorChainPoolStatsJson,
        runeAddress: String,
        assetAddress: String?,
    ): ThorChainLpPosition? {
        val lp =
            try {
                thorChainApi.getLiquidityProvider(pool.asset, runeAddress)
                    ?: assetAddress?.let { thorChainApi.getLiquidityProvider(pool.asset, it) }
            } catch (e: IOException) {
                Timber.w(e, "Failed to fetch LP position for pool %s", pool.asset)
                null
            } catch (e: NetworkException) {
                Timber.w(e, "Failed to fetch LP position for pool %s", pool.asset)
                null
            } ?: return null

        val units = lp.units.toBigIntegerOrNull() ?: BigInteger.ZERO
        if (units.signum() == 0) return null

        return ThorChainLpPosition(
            pool = pool.asset,
            units = units,
            runeRedeemValue = lp.runeRedeemValue.toBigIntegerOrNull() ?: BigInteger.ZERO,
            assetRedeemValue = lp.assetRedeemValue.toBigIntegerOrNull() ?: BigInteger.ZERO,
            annualPercentageRate = pool.annualPercentageRate?.toFiniteDoubleOrNull(),
        )
    }

    // Midgard returns "NaN" for periods with insufficient history; treat as missing.
    private fun String.toFiniteDoubleOrNull(): Double? = toDoubleOrNull()?.takeIf { it.isFinite() }
}
