package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapTransaction.RegularSwapTransaction
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.swap.convertToTokenValue
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Assembles the provider-specific [RegularSwapTransaction] for an already-validated swap.
 *
 * Extracted from `SwapFormViewModel.swap()` so the transaction-construction logic — the
 * `when(quote)` block that fetches the block-chain-specific/UTXO plan, resolves allowances, and
 * builds the payload for ThorChain / MayaChain / SwapKit / 1inch — lives in a single unit that can
 * be unit-tested in isolation. The ViewModel keeps pre-flight validation, persistence, and
 * navigation; this builder is pure transaction construction over resolved inputs.
 */
internal class SwapTransactionBuilder
@Inject
constructor(
    private val swapGasCalculator: SwapGasCalculator,
    private val allowanceRepository: AllowanceRepository,
) {

    suspend fun build(
        vaultId: String,
        srcToken: Coin,
        dstToken: Coin,
        srcAddress: String,
        srcTokenValue: TokenValue,
        quote: SwapQuote,
        gasFee: TokenValue,
        gasFeeFiatValue: FiatValue,
        estimatedNetworkFeeTokenValue: TokenValue?,
        estimatedNetworkFeeFiatValue: FiatValue?,
        gasLimitOverride: Long? = null,
        externalRecipient: String? = null,
    ): RegularSwapTransaction {
        val dstTokenValue = quote.expectedDstValue

        return when (quote) {
            is SwapQuote.ThorChain -> {
                val dstAddress = quote.data.router ?: quote.data.inboundAddress ?: srcAddress
                val isRouterDeposit =
                    !srcToken.isNativeToken &&
                        srcToken.chain.standard == TokenStandard.EVM &&
                        !quote.data.router.isNullOrEmpty()
                val specificAndUtxo =
                    swapGasCalculator.getSpecificAndUtxo(
                        srcToken = srcToken,
                        srcAddress = srcAddress,
                        gasFee = gasFee,
                        isThorchainRouterDeposit = isRouterDeposit,
                        dstAddress = if (isRouterDeposit) dstAddress else null,
                        memo = if (isRouterDeposit) quote.data.memo else null,
                        tokenAmountValue = if (isRouterDeposit) srcTokenValue.value else null,
                    )
                val allowance =
                    allowanceRepository.getAllowance(
                        chain = srcToken.chain,
                        contractAddress = srcToken.contractAddress,
                        srcAddress = srcAddress,
                        dstAddress = dstAddress,
                    )
                val isApprovalRequired = allowance != null && allowance < srcTokenValue.value

                val isAffiliate = true

                RegularSwapTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = vaultId,
                    srcToken = srcToken,
                    srcTokenValue = srcTokenValue,
                    dstToken = dstToken,
                    dstAddress = dstAddress,
                    expectedDstTokenValue = dstTokenValue,
                    blockChainSpecific = specificAndUtxo,
                    estimatedFees = quote.fees,
                    swapFee = dstToken.convertToTokenValue(quote.data.fees.affiliate),
                    outboundFee = dstToken.convertToTokenValue(quote.data.fees.outbound),
                    gasFees = estimatedNetworkFeeTokenValue ?: gasFee,
                    isApprovalRequired = isApprovalRequired,
                    memo = quote.data.memo,
                    gasFeeFiatValue = estimatedNetworkFeeFiatValue ?: gasFeeFiatValue,
                    externalRecipient = externalRecipient,
                    payload =
                        SwapPayload.ThorChain(
                            THORChainSwapPayload(
                                fromAddress = srcAddress,
                                fromCoin = srcToken,
                                toCoin = dstToken,
                                vaultAddress = quote.data.inboundAddress ?: srcAddress,
                                routerAddress = quote.data.router,
                                fromAmount = srcTokenValue.value,
                                toAmountDecimal = dstTokenValue.decimal,
                                // The on-chain min-output floor is enforced by the `:LIM` field
                                // the node bakes into `quote.data.memo` (signed verbatim) when the
                                // quote request carries `tolerance_bps`; this proto field is not
                                // read at sign time, so it stays "0".
                                toAmountLimit = "0",
                                streamingInterval = "1",
                                streamingQuantity = "0",
                                expirationTime =
                                    (System.currentTimeMillis().milliseconds + 15.minutes)
                                        .inWholeSeconds
                                        .toULong(),
                                isAffiliate = isAffiliate,
                            )
                        ),
                )
            }

            is SwapQuote.MayaChain -> {
                val isRouterDeposit =
                    !srcToken.isNativeToken &&
                        srcToken.chain.standard == TokenStandard.EVM &&
                        !quote.data.router.isNullOrEmpty()
                val dstAddress =
                    if (!srcToken.isNativeToken && srcToken.chain.standard == TokenStandard.EVM) {
                        quote.data.router ?: quote.data.inboundAddress ?: srcAddress
                    } else {
                        quote.data.inboundAddress ?: srcAddress
                    }
                val specificAndUtxo =
                    swapGasCalculator.getSpecificAndUtxo(
                        srcToken = srcToken,
                        srcAddress = srcAddress,
                        gasFee = gasFee,
                        isThorchainRouterDeposit = isRouterDeposit,
                        dstAddress = if (isRouterDeposit) dstAddress else null,
                        memo = if (isRouterDeposit) quote.data.memo else null,
                        tokenAmountValue = if (isRouterDeposit) srcTokenValue.value else null,
                    )

                val allowance =
                    allowanceRepository.getAllowance(
                        chain = srcToken.chain,
                        contractAddress = srcToken.contractAddress,
                        srcAddress = srcAddress,
                        dstAddress = dstAddress,
                    )
                val isApprovalRequired = allowance != null && allowance < srcTokenValue.value

                val isAffiliate = true

                RegularSwapTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = vaultId,
                    srcToken = srcToken,
                    srcTokenValue = srcTokenValue,
                    dstToken = dstToken,
                    dstAddress = dstAddress,
                    expectedDstTokenValue = dstTokenValue,
                    blockChainSpecific = specificAndUtxo,
                    estimatedFees = quote.fees,
                    swapFee = dstToken.convertToTokenValue(quote.data.fees.affiliate),
                    outboundFee = dstToken.convertToTokenValue(quote.data.fees.outbound),
                    gasFees = estimatedNetworkFeeTokenValue ?: gasFee,
                    memo = quote.data.memo,
                    isApprovalRequired = isApprovalRequired,
                    gasFeeFiatValue = estimatedNetworkFeeFiatValue ?: gasFeeFiatValue,
                    externalRecipient = externalRecipient,
                    payload =
                        SwapPayload.MayaChain(
                            THORChainSwapPayload(
                                fromAddress = srcAddress,
                                fromCoin = srcToken,
                                toCoin = dstToken,
                                vaultAddress = quote.data.inboundAddress ?: srcAddress,
                                routerAddress = quote.data.router,
                                fromAmount = srcTokenValue.value,
                                toAmountDecimal = dstTokenValue.decimal,
                                // See ThorChain branch: the real floor is the memo `:LIM` the node
                                // adds from `tolerance_bps`; this proto field is unused at sign
                                // time.
                                toAmountLimit = "0",
                                streamingInterval = "3",
                                streamingQuantity = "0",
                                expirationTime =
                                    (System.currentTimeMillis().milliseconds + 15.minutes)
                                        .inWholeSeconds
                                        .toULong(),
                                isAffiliate = isAffiliate,
                            )
                        ),
                )
            }

            is SwapQuote.SwapKit -> {
                // Pre-flight gate: refuse a route whose txType has no wired signing
                // path before staging keysign. Sourced from
                // SwapKitSwapPayloadJson.SIGNABLE_TX_TYPES — the same list
                // SigningHelper dispatches on — so this guard can't drift from what
                // the dispatcher actually accepts.
                require(SwapKitSwapPayloadJson.isSignableTxType(quote.data.txType)) {
                    "Unsupported SwapKit txType for swap: ${quote.data.txType}"
                }
                val specificAndUtxo =
                    swapGasCalculator.getSpecificAndUtxo(
                        srcToken = srcToken,
                        srcAddress = srcAddress,
                        gasFee = gasFee,
                    )
                RegularSwapTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = vaultId,
                    srcToken = srcToken,
                    srcTokenValue = srcTokenValue,
                    dstToken = dstToken,
                    // SwapKit's source-chain deposit address. Signing is driven
                    // entirely by the payload bytes (PSBT / TronWeb object), not by
                    // this blockChainSpecific.
                    dstAddress = quote.data.targetAddress,
                    expectedDstTokenValue = dstTokenValue,
                    blockChainSpecific = specificAndUtxo,
                    estimatedFees = quote.fees,
                    gasFees = estimatedNetworkFeeTokenValue ?: gasFee,
                    memo = quote.data.memo,
                    isApprovalRequired = false,
                    gasFeeFiatValue = estimatedNetworkFeeFiatValue ?: gasFeeFiatValue,
                    externalRecipient = externalRecipient,
                    payload = SwapPayload.SwapKit(quote.data),
                )
            }

            is SwapQuote.OneInch -> {
                val dstAddress = quote.data.tx.to
                // The ERC20 allowance must be granted to the provider's token-
                // transfer proxy, which for SwapKit differs from the swap `to`.
                // Derivation is factored into approveSpenderFor (pinned by test) so
                // a regression collapsing it to `to` can't pass CI silently.
                val approveSpender = approveSpenderFor(quote.data.tx)
                val specificAndUtxo =
                    swapGasCalculator.getSpecificAndUtxo(srcToken, srcAddress, gasFee)

                val allowance =
                    allowanceRepository.getAllowance(
                        chain = srcToken.chain,
                        contractAddress = srcToken.contractAddress,
                        srcAddress = srcAddress,
                        dstAddress = approveSpender,
                    )
                val isApprovalRequired = allowance != null && allowance < srcTokenValue.value

                val specific = specificAndUtxo.blockChainSpecific
                // Aggregators can return tx.gas == 0; fall back to the standard EVM swap unit
                // so the signed payload never carries a zero gas limit (matches
                // SwapQuoteManager's fee path).
                //
                // A user gas-limit override (#4858) replaces the aggregator estimate. OneInchSwap
                // signs with maxOf(tx.gas, ethSpecific.gasLimit), so set BOTH to the override —
                // maxOf(x, x) = x — making it effective whether the user raises or lowers the
                // limit. Auto (null/non-positive) keeps the estimate and the current behavior.
                val gasLimit =
                    gasLimitOverride?.takeIf { it > 0L }
                        ?: (quote.data.tx.gas.takeIf { it != 0L }
                            ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT)
                val hasGasOverride = gasLimitOverride != null && gasLimitOverride > 0L
                val effectiveSpecificAndUtxo =
                    if (specific is BlockChainSpecific.Ethereum && hasGasOverride) {
                        specificAndUtxo.copy(
                            blockChainSpecific = specific.copy(gasLimit = gasLimit.toBigInteger())
                        )
                    } else {
                        specificAndUtxo
                    }

                // The passed-in network fee was estimated for the aggregator's gas limit, so a user
                // override (#4858) must also be reflected in the displayed/verify fee — else a
                // lowered limit could show a fee that can't match what executes (review #4969).
                // Recompute the max-fee from the override (gasLimit × maxFeePerGas) and re-value it
                // in fiat via the native price implied by the displayed fee pair. For aggregator
                // swaps that pair is re-based onto the route gas (#5056), so reference
                // estimatedNetworkFeeTokenValue with its own estimatedNetworkFeeFiatValue — the
                // flat-600k gasFee/gasFeeFiatValue baseline would skew the price, and pairing the
                // token with its matching fiat avoids a mismatch when only one estimate is present.
                // Auto (no override) keeps the estimate.
                val (priceReferenceFee, priceReferenceFiat) =
                    if (estimatedNetworkFeeTokenValue != null) {
                        estimatedNetworkFeeTokenValue to
                            (estimatedNetworkFeeFiatValue ?: gasFeeFiatValue)
                    } else {
                        gasFee to gasFeeFiatValue
                    }
                val (displayGasFees, displayGasFeeFiat) =
                    if (
                        specific is BlockChainSpecific.Ethereum &&
                            hasGasOverride &&
                            priceReferenceFee.value.signum() > 0
                    ) {
                        val overriddenFeeWei = gasLimit.toBigInteger() * specific.maxFeePerGasWei
                        val overriddenFiat =
                            priceReferenceFiat.value
                                .multiply(overriddenFeeWei.toBigDecimal())
                                .divide(
                                    priceReferenceFee.value.toBigDecimal(),
                                    10,
                                    RoundingMode.HALF_UP,
                                )
                        gasFee.copy(value = overriddenFeeWei) to
                            FiatValue(overriddenFiat, priceReferenceFiat.currency)
                    } else {
                        priceReferenceFee to priceReferenceFiat
                    }
                val quoteData =
                    if (specific is BlockChainSpecific.Ethereum) {
                        quote.data.copy(
                            tx =
                                quote.data.tx.copy(
                                    gasPrice = specific.maxFeePerGasWei.toString(),
                                    gas = gasLimit,
                                )
                        )
                    } else {
                        quote.data
                    }

                RegularSwapTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = vaultId,
                    srcToken = srcToken,
                    srcTokenValue = srcTokenValue,
                    dstToken = dstToken,
                    dstAddress = dstAddress,
                    approveSpender = approveSpender,
                    expectedDstTokenValue = dstTokenValue,
                    blockChainSpecific = effectiveSpecificAndUtxo,
                    estimatedFees = quote.fees,
                    gasFees = displayGasFees,
                    memo = null,
                    isApprovalRequired = isApprovalRequired,
                    gasFeeFiatValue = displayGasFeeFiat,
                    externalRecipient = externalRecipient,
                    payload =
                        SwapPayload.EVM(
                            EVMSwapPayloadJson(
                                fromCoin = srcToken,
                                toCoin = dstToken,
                                fromAmount = srcTokenValue.value,
                                toAmountDecimal = dstTokenValue.decimal,
                                quote = quoteData,
                                provider = quote.provider,
                                swapId = quote.swapId,
                                subProvider = quote.subProvider,
                            )
                        ),
                )
            }
        }
    }
}
