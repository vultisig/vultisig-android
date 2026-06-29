package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingHelper
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
 * Swap-only fee helper — only [BlockChainSpecific.Ethereum] and [BlockChainSpecific.THORChain]
 * reach the swap branch; everything else goes through `feeServiceComposite`.
 *
 * For an EVM aggregator route the caller passes [aggregatorDisplayGasLimit] (from
 * `evmSwapDisplayGasLimit`) so the joiner values the fee at the same limit as the initiator
 * (#5056); native-protocol deposits pass null and keep [EthereumFeeService.DEFAULT_SWAP_LIMIT]. The
 * [error] branch guards against a new subtype reaching this path with a silent zero fee.
 */
internal fun computeJoinKeysignSwapNetworkFee(
    blockChainSpecific: BlockChainSpecific,
    nativeCoin: Coin,
    aggregatorDisplayGasLimit: BigInteger? = null,
): TokenValue =
    when (blockChainSpecific) {
        is BlockChainSpecific.Ethereum -> {
            val limit = aggregatorDisplayGasLimit ?: EthereumFeeService.DEFAULT_SWAP_LIMIT
            TokenValue(value = blockChainSpecific.maxFeePerGasWei * limit, token = nativeCoin)
        }
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

/**
 * The Cosmos staking / distribution message type URLs whose presence in a signed body marks the
 * payload as a delegation deposit (delegate / undelegate / redelegate / withdraw-reward).
 */
private val COSMOS_STAKING_DEPOSIT_TYPE_URLS =
    setOf(
        CosmosStakingHelper.MSG_DELEGATE_TYPE_URL,
        CosmosStakingHelper.MSG_UNDELEGATE_TYPE_URL,
        CosmosStakingHelper.MSG_BEGIN_REDELEGATE_TYPE_URL,
        CosmosStakingHelper.MSG_WITHDRAW_DELEGATOR_REWARD_TYPE_URL,
    )

/**
 * True when this Cosmos payload's signed body ([signDirect]) carries a staking / distribution
 * message. The initiator classifies such transactions as deposits via its local
 * `DepositTransactionRepository`, which a joining device cannot see — so the joiner recovers the
 * same classification from the transmitted SignDoc, letting both devices render the
 * Transaction-complete screen identically (issue #4939). A `null`/unparseable body, or a plain
 * `MsgSend` / dApp transaction, yields `false`.
 */
internal fun KeysignPayload.isCosmosStakingDeposit(
    parseCosmosMessage: ParseCosmosMessageUseCase
): Boolean {
    val direct = signDirect ?: return false
    val message =
        try {
            parseCosmosMessage(direct)
        } catch (e: IllegalArgumentException) {
            Timber.w("Failed to parse cosmos message for deposit detection: %s", e.message)
            return false
        }
    return message.messages.any { it.typeUrl in COSMOS_STAKING_DEPOSIT_TYPE_URLS }
}

/** Sums the raw Cosmos fee amounts, or `null` if any entry is missing or not a valid integer. */
private fun List<String?>.sumDenomAmountsOrNull(): BigInteger? {
    var total = BigInteger.ZERO
    for (raw in this) {
        total += raw?.toBigIntegerOrNull() ?: return null
    }
    return total
}
