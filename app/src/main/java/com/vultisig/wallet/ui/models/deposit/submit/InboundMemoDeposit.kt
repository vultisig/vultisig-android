package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getDustThreshold
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.nativeTokenTicker
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.models.toValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

/**
 * Fee, chain-specific data and UTXOs for a memo-carrying transfer into a protocol inbound vault.
 *
 * [gasFee] is the total the transaction pays — on a planned UTXO chain the planner's fee, not the
 * per-byte rate the chain-specific data carries for signing.
 */
internal data class InboundMemoTransfer(
    val gasFee: TokenValue,
    val estimatedGasFee: EstimatedGasFee,
    val specific: BlockChainSpecificAndUtxo,
)

/**
 * Prices and plans a transfer of [amount] of [token] to the inbound vault [dstAddress] carrying
 * [memo] — the shape every protocol inbound deposit takes (a secured-asset mint, the asset side of
 * a Maya LP add).
 *
 * UTXO chains (Cardano aside) are planned with [getBitcoinTransactionPlan] and spend only the UTXOs
 * the plan selected; the amount must clear the chain dust threshold and the plan must succeed.
 */
internal suspend fun planInboundMemoTransfer(
    vaultId: String,
    vault: Vault,
    token: Coin,
    amount: BigInteger,
    dstAddress: String,
    memo: String,
    feeServiceComposite: FeeServiceComposite,
    tokenRepository: TokenRepository,
    blockChainSpecificRepository: BlockChainSpecificRepository,
    gasFeeToEstimate: suspend (GasFeeParams) -> EstimatedGasFee,
    getBitcoinTransactionPlan: BitcoinTransactionPlanBuilder,
): InboundMemoTransfer {
    val chain = token.chain
    val transfer =
        Transfer(
            coin = token,
            vault =
                VaultData(
                    vaultHexChainCode = vault.hexChainCode,
                    vaultHexPublicKey = vault.getPubKeyByChain(chain),
                ),
            amount = amount,
            to = dstAddress,
            memo = memo,
            isMax = false,
        )

    val fees = withContext(Dispatchers.IO) { feeServiceComposite.calculateFees(transfer) }
    val nativeCoin = withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }
    // On a UTXO chain this is the fee service's rate — sats per byte, or Zcash's flat 1,000-zat
    // seed — which the specific carries into signing; it is not what the transaction pays.
    val feeRate = TokenValue(value = fees.amount, token = nativeCoin)

    val specific =
        blockChainSpecificRepository.getSpecific(
            chain,
            token.address,
            token,
            feeRate,
            memo = memo,
            isSwap = false,
            dstAddress = dstAddress,
            isMaxAmountEnabled = false,
            isDeposit = true,
            tokenAmountValue = amount,
        )

    val plan =
        if (chain.isPlannedUtxoChain) {
            getBitcoinTransactionPlan(vaultId, token, dstAddress, amount, specific, memo).also {
                validateBtcLikeAmount(amount, chain, it)
            }
        } else {
            null
        }
    val plannedSpecific = if (plan != null) selectPlannedUtxos(specific, plan) else specific
    // The planner's fee is what the signer pays: on Zcash it already includes the ZIP-317 charge a
    // memo output adds, which the flat seed above knows nothing about.
    val gasFee =
        plan?.fee?.takeIf { it > 0 }?.let { TokenValue(value = BigInteger.valueOf(it), token = nativeCoin) }
            ?: feeRate

    val estimatedGasFee =
        gasFeeToEstimate(
            GasFeeParams(gasLimit = BigInteger.ONE, gasFee = gasFee, selectedToken = token)
        )

    return InboundMemoTransfer(
        gasFee = gasFee,
        estimatedGasFee = estimatedGasFee,
        specific = plannedSpecific,
    )
}

/** UTXO chains whose spend is planned by WalletCore's Bitcoin planner (Cardano is not). */
internal val Chain.isPlannedUtxoChain: Boolean
    get() = standard == TokenStandard.UTXO && this != Chain.Cardano

/**
 * Replaces the UTXOs in [specific] with those selected by [plan], leaving non-UTXO specifics and a
 * missing plan untouched.
 */
internal fun selectPlannedUtxos(
    specific: BlockChainSpecificAndUtxo,
    plan: Bitcoin.TransactionPlan?,
): BlockChainSpecificAndUtxo {
    specific.blockChainSpecific as? BlockChainSpecific.UTXO ?: return specific

    val updatedUtxo =
        plan?.utxosOrBuilderList?.map { planUtxo ->
            UtxoInfo(
                hash = planUtxo.outPoint.hash.toByteArray().reversedArray().toHexString(),
                index = planUtxo.outPoint.index.toUInt(),
                amount = planUtxo.amount,
            )
        } ?: return specific

    return specific.copy(utxos = updatedUtxo)
}

/**
 * Validates that [amount] is above the chain dust threshold and that [plan] resolved successfully,
 * throwing [InvalidTransactionDataException] otherwise.
 */
internal fun validateBtcLikeAmount(amount: BigInteger, chain: Chain, plan: Bitcoin.TransactionPlan?) {
    val minAmount = chain.getDustThreshold
    if (amount < minAmount) {
        throw InvalidTransactionDataException(
            UiText.FormattedText(
                R.string.send_form_minimum_send_amount_is_requires_this,
                listOf(chain.toValue(minAmount).toString(), chain.nativeTokenTicker, chain.raw),
            )
        )
    }
    if (plan?.error != SigningError.OK) {
        throw InvalidTransactionDataException(R.string.insufficient_utxos_error.asUiText())
    }
}
