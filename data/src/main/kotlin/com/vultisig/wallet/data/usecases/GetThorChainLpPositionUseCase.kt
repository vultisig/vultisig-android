package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.ThorChainLpPosition
import com.vultisig.wallet.data.utils.NetworkException
import java.io.IOException
import java.math.BigInteger
import javax.inject.Inject
import timber.log.Timber

interface GetThorChainLpPositionUseCase {
    /**
     * Fetches the user's THORChain LP position for a single pool.
     *
     * Use this when the caller already knows which pool to inspect (e.g. the Remove LP screen) — it
     * issues at most two requests (rune-side, then asset-side fallback) instead of fanning out
     * across every available pool.
     *
     * @param poolId the pool to query, e.g. `"BTC.BTC"`.
     * @param runeAddress the user's RUNE address — tried first.
     * @param assetAddress optional non-RUNE side address used as a fallback when an LP record is
     *   keyed by the pool's asset side rather than RUNE.
     * @return the position, or `null` if the user has no LP units in this pool.
     */
    suspend operator fun invoke(
        poolId: String,
        runeAddress: String,
        assetAddress: String? = null,
    ): ThorChainLpPosition?
}

internal class GetThorChainLpPositionUseCaseImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : GetThorChainLpPositionUseCase {

    override suspend fun invoke(
        poolId: String,
        runeAddress: String,
        assetAddress: String?,
    ): ThorChainLpPosition? {
        val lp =
            try {
                thorChainApi.getLiquidityProvider(poolId, runeAddress)
                    ?: assetAddress?.let { thorChainApi.getLiquidityProvider(poolId, it) }
            } catch (e: IOException) {
                Timber.w(e, "Failed to fetch LP position for pool %s", poolId)
                null
            } catch (e: NetworkException) {
                Timber.w(e, "Failed to fetch LP position for pool %s", poolId)
                null
            } ?: return null

        val units = lp.units.toBigIntegerOrNull() ?: BigInteger.ZERO
        if (units.signum() == 0) return null

        return ThorChainLpPosition(
            pool = poolId,
            units = units,
            runeRedeemValue = lp.runeRedeemValue.toBigIntegerOrNull() ?: BigInteger.ZERO,
            assetRedeemValue = lp.assetRedeemValue.toBigIntegerOrNull() ?: BigInteger.ZERO,
            annualPercentageRate = null,
        )
    }
}
