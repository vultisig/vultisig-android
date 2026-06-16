package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.cosmosNativeDenom
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.usecases.ParseCosmosMessageUseCase
import java.math.BigInteger
import timber.log.Timber

/**
 * Polymorphic fee helper shared by the join-keysign deposit and send branches — mirrors iOS's
 * `BlockChainSpecific.fee` getter. Ethereum returns `maxFeePerGasWei * gasLimit`, THORChain returns
 * `blockChainSpecific.fee`, and every other chain returns [fallbackFeeAmount].
 *
 * Swap callers must use [computeJoinKeysignSwapNetworkFee] instead — it bakes in the
 * initiator-aligned EVM swap gas limit and rejects subtypes the swap branch can't reach, so an
 * accidental zero-fee fallback is impossible.
 */
internal fun computeJoinKeysignNetworkFee(
    blockChainSpecific: BlockChainSpecific,
    nativeCoin: Coin,
    fallbackFeeAmount: BigInteger,
): TokenValue =
    when (blockChainSpecific) {
        is BlockChainSpecific.Ethereum ->
            TokenValue(
                value = blockChainSpecific.maxFeePerGasWei * blockChainSpecific.gasLimit,
                token = nativeCoin,
            )
        is BlockChainSpecific.THORChain ->
            TokenValue(value = blockChainSpecific.fee, token = nativeCoin)
        else -> TokenValue(value = fallbackFeeAmount, token = nativeCoin)
    }

/**
 * Swap-only fee helper. Only [BlockChainSpecific.Ethereum] and [BlockChainSpecific.THORChain] are
 * reachable in the swap branch — every other subtype goes through `feeServiceComposite`. The
 * Ethereum case uses [EthereumFeeService.DEFAULT_SWAP_LIMIT] so joiner output matches the
 * initiator's swap-fee display (see [EthereumFeeService.calculateDefaultFees] for Swap) instead of
 * being ~15× lower for native ETH/Arb transfers.
 *
 * The [error] branch is the safety net for [JoinKeysignViewModel.loadTransaction]'s swap path: if a
 * new [BlockChainSpecific] subtype is ever added to that branch's type check, this helper crashes
 * loudly instead of silently shipping a zero fee.
 */
internal fun computeJoinKeysignSwapNetworkFee(
    blockChainSpecific: BlockChainSpecific,
    nativeCoin: Coin,
): TokenValue =
    when (blockChainSpecific) {
        is BlockChainSpecific.Ethereum ->
            TokenValue(
                value = blockChainSpecific.maxFeePerGasWei * EthereumFeeService.DEFAULT_SWAP_LIMIT,
                token = nativeCoin,
            )
        is BlockChainSpecific.THORChain ->
            TokenValue(value = blockChainSpecific.fee, token = nativeCoin)
        else ->
            error(
                "computeJoinKeysignSwapNetworkFee does not support " +
                    "${blockChainSpecific::class.simpleName} — extend this helper when adding " +
                    "new swap-branch subtypes"
            )
    }

/**
 * The fee a dApp signed in [KeysignPayload.signAmino] or [KeysignPayload.signDirect], or `null` for
 * a wallet-built native tx (both absent) so callers fall back to the estimate. The cosmos signers
 * sign these bytes verbatim, so this is the fee actually broadcast — including the `0` Rujira uses
 * today (issue #4390).
 *
 * Only [Chain.cosmosNativeDenom] entries are summed; a fee in another denom, or an unparseable
 * amount, yields `null` rather than a misleading `0`. Mirrors the Windows resolver.
 */
internal fun KeysignPayload.dappSuppliedNativeFee(
    chain: Chain,
    parseCosmosMessage: ParseCosmosMessageUseCase,
): BigInteger? {
    val nativeDenom = chain.cosmosNativeDenom ?: return null

    val aminoMatched =
        signAmino?.fee?.amount?.filter { it?.denom?.lowercase() == nativeDenom }.orEmpty()
    if (aminoMatched.isNotEmpty()) {
        return aminoMatched.map { it?.amount }.sumDenomAmountsOrNull()
    }

    val directMatched =
        signDirect
            ?.let {
                try {
                    parseCosmosMessage(it)
                } catch (e: IllegalArgumentException) {
                    Timber.w("Failed to parse cosmos message: %s", e.message)
                    null
                }
            }
            ?.authInfoFee
            ?.amount
            ?.filter { it.denom.lowercase() == nativeDenom }
            .orEmpty()
    if (directMatched.isNotEmpty()) {
        return directMatched.map { it.amount }.sumDenomAmountsOrNull()
    }

    return null
}

/** Sums the raw Cosmos fee amounts, or `null` if any entry is missing or not a valid integer. */
private fun List<String?>.sumDenomAmountsOrNull(): BigInteger? {
    var total = BigInteger.ZERO
    for (raw in this) {
        total += raw?.toBigIntegerOrNull() ?: return null
    }
    return total
}
