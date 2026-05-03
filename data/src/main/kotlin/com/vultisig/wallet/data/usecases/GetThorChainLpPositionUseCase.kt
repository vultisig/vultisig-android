package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainLiquidityProviderJson
import com.vultisig.wallet.data.models.ThorChainLpPosition
import com.vultisig.wallet.data.utils.NetworkException
import java.io.IOException
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
     * Returns `null` for any of the following — callers cannot distinguish them:
     * - the user has no LP record on either side of the pool;
     * - the rune-side and (optional) asset-side records both exist but report zero units;
     * - thornode/midgard returned an unparseable amount field;
     * - the request failed with a swallowed [IOException] or [NetworkException].
     *
     * @param poolId the pool to query, e.g. `"BTC.BTC"`.
     * @param runeAddress the user's RUNE address — tried first.
     * @param assetAddress optional non-RUNE side address used as a fallback when an LP record is
     *   keyed by the pool's asset side rather than RUNE. Blank addresses are ignored.
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
        val runeSide = fetchSide(poolId, runeAddress)
        // A stale rune-side record with units == 0 must not shadow a real asset-side position.
        val lp =
            runeSide?.takeIf { it.hasUnits() }
                ?: assetAddress?.takeIf { it.isNotBlank() }?.let { fetchSide(poolId, it) }
                ?: return null

        val units = lp.units.toBigIntegerOrNull() ?: return null
        if (units.signum() == 0) return null
        val runeRedeemValue = lp.runeRedeemValue.toBigIntegerOrNull() ?: return null
        val assetRedeemValue = lp.assetRedeemValue.toBigIntegerOrNull() ?: return null

        return ThorChainLpPosition(
            pool = poolId,
            units = units,
            runeRedeemValue = runeRedeemValue,
            assetRedeemValue = assetRedeemValue,
            annualPercentageRate = null,
        )
    }

    private suspend fun fetchSide(
        poolId: String,
        address: String,
    ): ThorChainLiquidityProviderJson? =
        try {
            thorChainApi.getLiquidityProvider(poolId, address)
        } catch (e: IOException) {
            Timber.w(e, "Failed to fetch LP position for pool %s", poolId)
            null
        } catch (e: NetworkException) {
            Timber.w(e, "Failed to fetch LP position for pool %s", poolId)
            null
        }

    private fun ThorChainLiquidityProviderJson.hasUnits(): Boolean =
        (units.toBigIntegerOrNull()?.signum() ?: 0) > 0
}
