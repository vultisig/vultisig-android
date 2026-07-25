package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject

/**
 * Reduce a live THORChain inbound list to the chains a limit order can currently be placed from or
 * to.
 *
 * THORChain itself is always included — RUNE settles via `MsgDeposit` and so has no inbound vault
 * to advertise. Everything else must have a live inbound that is neither halted nor trading-paused,
 * mirroring the market swap's halt gate; absent pause flags read as "not paused".
 *
 * When the inbound list yields nothing usable we fall back to the static routable set rather than
 * returning just THORChain: an empty picker reads as "this pair is unsupported" when the real
 * problem is a failed fetch. The placement path re-checks halts at sign time, so a stale entry here
 * cannot get a halted-chain order signed.
 */
fun getLimitSwapSupportedChains(inbounds: List<THORChainInboundAddress>): List<Chain> {
    val chains = linkedSetOf(Chain.ThorChain)

    inbounds.forEach { inbound ->
        if (inbound.halted || inbound.globalTradingPaused || inbound.chainTradingPaused) {
            return@forEach
        }
        thorchainAssetPrefixToChain[inbound.chain.trim().uppercase()]?.let(chains::add)
    }

    return if (chains.size > 1) chains.toList() else staticLimitSwapSupportedChains
}

/** Chains a limit order can currently be placed from or to, given live network state. */
interface LimitSwapSupportedChainsRepository {
    suspend fun getSupportedChains(): List<Chain>
}

internal class LimitSwapSupportedChainsRepositoryImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : LimitSwapSupportedChainsRepository {

    override suspend fun getSupportedChains(): List<Chain> {
        val inbounds =
            runCatching { thorChainApi.getTHORChainInboundAddresses() }.getOrDefault(emptyList())
        return getLimitSwapSupportedChains(inbounds)
    }
}
