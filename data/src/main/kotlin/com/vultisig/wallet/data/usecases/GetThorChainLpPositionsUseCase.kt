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
    suspend operator fun invoke(
        runeAddress: String,
        assetAddress: String? = null,
        period: String? = null,
    ): List<ThorChainLpPosition>
}

internal class GetThorChainLpPositionsUseCaseImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : GetThorChainLpPositionsUseCase {

    override suspend fun invoke(
        runeAddress: String,
        assetAddress: String?,
        period: String?,
    ): List<ThorChainLpPosition> {
        val pools = thorChainApi.getPoolStats(period).filter { it.status == POOL_STATUS_AVAILABLE }
        return coroutineScope {
            pools
                .map { pool -> async { fetchPosition(pool, runeAddress, assetAddress) } }
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
