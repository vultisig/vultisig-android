package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Reduce a live THORChain inbound list to the chains a limit order can currently be placed from or
 * to.
 *
 * THORChain itself is always included — RUNE settles via `MsgDeposit` and so has no inbound vault
 * to advertise. Everything else must have a live inbound that is neither halted nor trading-paused,
 * mirroring the market swap's halt gate; absent pause flags read as "not paused".
 *
 * This reflects the live state faithfully: a successful response where every external inbound is
 * halted returns only THORChain, so paused chains aren't re-offered. The static-routable fallback
 * (for a *failed* fetch, where an empty picker would wrongly read as "unsupported") lives in the
 * repository, which is the only layer that can tell a failure from a genuinely empty result.
 */
fun getLimitSwapSupportedChains(inbounds: List<THORChainInboundAddress>): List<Chain> {
    val chains = linkedSetOf(Chain.ThorChain)

    inbounds.forEach { inbound ->
        if (inbound.halted || inbound.globalTradingPaused || inbound.chainTradingPaused) {
            return@forEach
        }
        thorchainAssetPrefixToChain[inbound.chain.trim().uppercase()]?.let(chains::add)
    }

    return chains.toList()
}

/** Chains a limit order can currently be placed from or to, given live network state. */
interface LimitSwapSupportedChainsRepository {
    suspend fun getSupportedChains(): List<Chain>
}

internal class LimitSwapSupportedChainsRepositoryImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : LimitSwapSupportedChainsRepository {

    override suspend fun getSupportedChains(): List<Chain> =
        try {
            getLimitSwapSupportedChains(thorChainApi.getTHORChainInboundAddresses())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only a *failed* fetch falls back to the static routable set — an empty picker would
            // otherwise read as "this pair is unsupported". A successful all-halted response is
            // handled above (returns just THORChain). A cancelled caller must propagate.
            Timber.w(e, "Failed to fetch THORChain inbounds for limit-swap supported chains")
            staticLimitSwapSupportedChains
        }
}
