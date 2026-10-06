@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.api.swapAggregators.callsSoldToken
import com.vultisig.wallet.data.api.swapAggregators.swapKitErc20DepositRecipient
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService
import com.vultisig.wallet.data.chains.helpers.EthereumGasHelper.requireEthereumSpec
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapTransaction.RegularSwapTransaction
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.swapProviderFromWireId
import com.vultisig.wallet.data.repositories.AllowanceRepository
import com.vultisig.wallet.data.repositories.ApprovalRequirement
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.swap.convertToTokenValue
import com.vultisig.wallet.data.utils.toLongExact
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Display-only fee/discount context captured from the swap form at build time, carried onto the
 * [RegularSwapTransaction] so the verify screen renders the same Swap Fee percentage and VULT-tier
 * / referral discount rows the form shows (#5358). Defaults reproduce the pre-#5358 verify screen
 * (no percentage, no discount rows).
 */
internal data class SwapFeeDisplay(
    val swapFeePercent: String? = null,
    val swapFeeIncludedInRate: Boolean = false,
    val vultBpsDiscount: Int? = null,
    val vultBpsDiscountFiatValue: String? = null,
    val referralBpsDiscount: Int? = null,
    val referralBpsDiscountFiatValue: String? = null,
)

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
    private val clock: Clock,
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
        feeDisplay: SwapFeeDisplay = SwapFeeDisplay(),
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
                val approval =
                    allowanceRepository.getApprovalRequirement(
                        chain = srcToken.chain,
                        contractAddress = srcToken.contractAddress,
                        srcAddress = srcAddress,
                        dstAddress = dstAddress,
                        amount = srcTokenValue.value,
                    )

                val isAffiliate = true

                RegularSwapTransaction(
                    id = Uuid.random().toString(),
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
                    isApprovalRequired = approval != ApprovalRequirement.NotRequired,
                    resetAllowanceFirst = approval == ApprovalRequirement.ResetThenApprove,
                    memo = quote.data.memo,
                    gasFeeFiatValue = estimatedNetworkFeeFiatValue ?: gasFeeFiatValue,
                    externalRecipient = externalRecipient,
                    swapFeePercent = feeDisplay.swapFeePercent,
                    swapFeeIncludedInRate = feeDisplay.swapFeeIncludedInRate,
                    vultBpsDiscount = feeDisplay.vultBpsDiscount,
                    vultBpsDiscountFiatValue = feeDisplay.vultBpsDiscountFiatValue,
                    referralBpsDiscount = feeDisplay.referralBpsDiscount,
                    referralBpsDiscountFiatValue = feeDisplay.referralBpsDiscountFiatValue,
                    priceImpact = quote.priceImpact,
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
                                expirationTime = (clock.now() + 15.minutes).epochSeconds.toULong(),
                                isAffiliate = isAffiliate,
                                slippageBps = quote.data.fees.slippageBps,
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

                val approval =
                    allowanceRepository.getApprovalRequirement(
                        chain = srcToken.chain,
                        contractAddress = srcToken.contractAddress,
                        srcAddress = srcAddress,
                        dstAddress = dstAddress,
                        amount = srcTokenValue.value,
                    )

                val isAffiliate = true

                RegularSwapTransaction(
                    id = Uuid.random().toString(),
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
                    isApprovalRequired = approval != ApprovalRequirement.NotRequired,
                    resetAllowanceFirst = approval == ApprovalRequirement.ResetThenApprove,
                    gasFeeFiatValue = estimatedNetworkFeeFiatValue ?: gasFeeFiatValue,
                    externalRecipient = externalRecipient,
                    swapFeePercent = feeDisplay.swapFeePercent,
                    swapFeeIncludedInRate = feeDisplay.swapFeeIncludedInRate,
                    vultBpsDiscount = feeDisplay.vultBpsDiscount,
                    vultBpsDiscountFiatValue = feeDisplay.vultBpsDiscountFiatValue,
                    referralBpsDiscount = feeDisplay.referralBpsDiscount,
                    referralBpsDiscountFiatValue = feeDisplay.referralBpsDiscountFiatValue,
                    priceImpact = quote.priceImpact,
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
                                expirationTime = (clock.now() + 15.minutes).epochSeconds.toULong(),
                                isAffiliate = isAffiliate,
                                slippageBps = quote.data.fees.slippageBps,
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
                    id = Uuid.random().toString(),
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
                    swapFeePercent = feeDisplay.swapFeePercent,
                    swapFeeIncludedInRate = feeDisplay.swapFeeIncludedInRate,
                    vultBpsDiscount = feeDisplay.vultBpsDiscount,
                    vultBpsDiscountFiatValue = feeDisplay.vultBpsDiscountFiatValue,
                    referralBpsDiscount = feeDisplay.referralBpsDiscount,
                    referralBpsDiscountFiatValue = feeDisplay.referralBpsDiscountFiatValue,
                    priceImpact = quote.priceImpact,
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
                val (specificAndUtxo, approval, routeGas) =
                    if (quote.data.tx.callsSoldToken(srcToken)) {
                        erc20DepositSpend(
                            quote.data.tx,
                            srcToken,
                            srcAddress,
                            srcTokenValue,
                            gasFee,
                        )
                    } else {
                        EvmSpend(
                            specificAndUtxo =
                                swapGasCalculator.getSpecificAndUtxo(srcToken, srcAddress, gasFee),
                            approval =
                                allowanceRepository.getApprovalRequirement(
                                    chain = srcToken.chain,
                                    contractAddress = srcToken.contractAddress,
                                    srcAddress = srcAddress,
                                    dstAddress = approveSpender,
                                    amount = srcTokenValue.value,
                                ),
                            // Aggregators can return a non-positive tx.gas; fall back to the
                            // standard EVM swap unit so a malformed gas limit never reaches the
                            // shared signed payload (matches SwapQuoteManager's fee path).
                            routeGas =
                                quote.data.tx.gas.takeIf { it > 0L }
                                    ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT,
                        )
                    }

                val specific = specificAndUtxo.blockChainSpecific
                // A user gas-limit override (#4858) replaces the aggregator estimate. OneInchSwap
                // signs with maxOf(tx.gas, ethSpecific.gasLimit), so set BOTH to the override —
                // maxOf(x, x) = x — making it effective whether the user raises or lowers the
                // limit. Auto (null/non-positive) keeps the estimate and the current behavior.
                val gasLimit = gasLimitOverride?.takeIf { it > 0L } ?: routeGas
                val hasGasOverride = gasLimitOverride != null && gasLimitOverride > 0L
                val effectiveSpecificAndUtxo =
                    if (specific is BlockChainSpecific.Ethereum && hasGasOverride) {
                        specificAndUtxo.copy(
                            blockChainSpecific = specific.copy(gasLimit = gasLimit.toBigInteger())
                        )
                    } else {
                        specificAndUtxo
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
                val (displayGasFees, displayGasFeeFiat) =
                    displayedSwapGasFee(
                        specific = specific,
                        displayLimit =
                            (effectiveSpecificAndUtxo.blockChainSpecific
                                    as? BlockChainSpecific.Ethereum)
                                ?.let { evmSwapPayloadDisplayGasLimit(srcToken, quoteData.tx, it) },
                        gasFee = gasFee,
                        gasFeeFiatValue = gasFeeFiatValue,
                        estimatedNetworkFeeTokenValue = estimatedNetworkFeeTokenValue,
                        estimatedNetworkFeeFiatValue = estimatedNetworkFeeFiatValue,
                    )
                // A literal 1inch quote carries no affiliate fee, so `quote.fees` is 1inch's own
                // quoted `gasPrice × gas` shown as the "Swap Fee". The joiner re-derives that same
                // placeholder from the signed tx's `gasPrice × gas` (JoinSwapUiModelBuilder's
                // `else`
                // branch), which is the `maxFeePerGasWei`/`gasLimit` stamped just above — not the
                // original quote values. Value the initiator's Swap Fee off the same stamped params
                // so both co-signers show the same figure instead of diverging (#5329). Other
                // providers routed through this branch (LI.FI / Kyber / SwapKit) carry a real fee
                // the joiner reads via its own branches, so their `quote.fees` is left untouched.
                val isLiteralOneInch =
                    swapProviderFromWireId(quote.provider) == SwapProvider.ONEINCH
                val estimatedFees =
                    if (isLiteralOneInch && specific is BlockChainSpecific.Ethereum) {
                        quote.fees.copy(value = specific.maxFeePerGasWei * gasLimit.toBigInteger())
                    } else {
                        quote.fees
                    }

                RegularSwapTransaction(
                    id = Uuid.random().toString(),
                    vaultId = vaultId,
                    srcToken = srcToken,
                    srcTokenValue = srcTokenValue,
                    dstToken = dstToken,
                    dstAddress = dstAddress,
                    approveSpender = approveSpender,
                    expectedDstTokenValue = dstTokenValue,
                    blockChainSpecific = effectiveSpecificAndUtxo,
                    estimatedFees = estimatedFees,
                    gasFees = displayGasFees,
                    memo = null,
                    isApprovalRequired = approval != ApprovalRequirement.NotRequired,
                    resetAllowanceFirst = approval == ApprovalRequirement.ResetThenApprove,
                    gasFeeFiatValue = displayGasFeeFiat,
                    externalRecipient = externalRecipient,
                    swapFeePercent = feeDisplay.swapFeePercent,
                    swapFeeIncludedInRate = feeDisplay.swapFeeIncludedInRate,
                    vultBpsDiscount = feeDisplay.vultBpsDiscount,
                    vultBpsDiscountFiatValue = feeDisplay.vultBpsDiscountFiatValue,
                    referralBpsDiscount = feeDisplay.referralBpsDiscount,
                    referralBpsDiscountFiatValue = feeDisplay.referralBpsDiscountFiatValue,
                    priceImpact = quote.priceImpact,
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

    /** The chain specifics, allowance and route gas an EVM swap tx spends with. */
    private data class EvmSpend(
        val specificAndUtxo: BlockChainSpecificAndUtxo,
        val approval: ApprovalRequirement,
        val routeGas: Long,
    )

    /**
     * A SwapKit ERC-20 deposit is a plain token transfer: it spends no allowance and is priced and
     * gas-limited as that transfer, so its route gas is the transfer limit, not SwapKit's.
     */
    private suspend fun erc20DepositSpend(
        tx: OneInchSwapTxJson,
        srcToken: Coin,
        srcAddress: String,
        srcTokenValue: TokenValue,
        gasFee: TokenValue,
    ): EvmSpend {
        val specificAndUtxo =
            swapGasCalculator.getErc20DepositTransferSpecific(
                srcToken = srcToken,
                srcAddress = srcAddress,
                gasFee = gasFee,
                // A tx addressed to the sold token decodes to its deposit recipient or throws.
                recipient =
                    checkNotNull(swapKitErc20DepositRecipient(tx, srcToken, srcTokenValue.value)),
                amount = srcTokenValue.value,
            )
        return EvmSpend(
            specificAndUtxo = specificAndUtxo,
            approval = ApprovalRequirement.NotRequired,
            routeGas =
                requireEthereumSpec(specificAndUtxo.blockChainSpecific).gasLimit.toLongExact(),
        )
    }

    /**
     * Displayed/staged EVM swap network fee (never the signed tx): valued at the exact gas
     * parameters stamped into the payload — [BlockChainSpecific.Ethereum.maxFeePerGasWei] times the
     * co-signer-aligned [displayLimit] ([evmSwapPayloadDisplayGasLimit], falling back to
     * [EthereumFeeService.DEFAULT_SWAP_LIMIT]). This is the identical formula the joiner applies in
     * `computeJoinKeysignSwapNetworkFee` off the same stamped `tx.gas` (which already folds in a
     * user gas-limit override, #4858), so every device — including OP-stack L2s and sub-floor
     * overrides that [evmSwapDisplayGasLimit] floors — shows the same crypto network fee instead of
     * a separately-fetched estimate that runs lower than what is signed (#5329, #5056). Fiat is
     * re-priced from the matched estimate/gas-pass reference pair so token and fiat stay
     * consistent; non-Ethereum plans keep the estimate/gas-pass baseline.
     */
    private fun displayedSwapGasFee(
        specific: BlockChainSpecific,
        displayLimit: BigInteger?,
        gasFee: TokenValue,
        gasFeeFiatValue: FiatValue,
        estimatedNetworkFeeTokenValue: TokenValue?,
        estimatedNetworkFeeFiatValue: FiatValue?,
    ): Pair<TokenValue, FiatValue> {
        // Use the route-gas estimate when it is a real positive value, else fall back to the
        // gas-pass baseline — a non-null zero estimate must not suppress the re-price (it would
        // also make repriceFee divide by zero). Take token and fiat as an atomic pair: a positive
        // token estimate with a null fiat estimate must NOT reprice gas-pass fiat against the
        // unrelated estimate token fee, so require both estimate values before using them.
        val estimatePair =
            estimatedNetworkFeeTokenValue
                ?.takeIf { it.value.signum() > 0 }
                ?.let { fee -> estimatedNetworkFeeFiatValue?.let { fiat -> fee to fiat } }
        val (referenceFee, referenceFiat) = estimatePair ?: (gasFee to gasFeeFiatValue)
        if (specific !is BlockChainSpecific.Ethereum || referenceFee.value.signum() <= 0) {
            return referenceFee to referenceFiat
        }
        // OP-stack L2s (null → DEFAULT_SWAP_LIMIT) land on the same limit the joiner falls back to.
        val feeWei =
            specific.maxFeePerGasWei * (displayLimit ?: EthereumFeeService.DEFAULT_SWAP_LIMIT)
        return gasFee.copy(value = feeWei) to repriceFee(feeWei, referenceFee, referenceFiat)
    }

    /** Re-values [feeWei] at the native price implied by the matched [refFee]/[refFiat] pair. */
    private fun repriceFee(feeWei: BigInteger, refFee: TokenValue, refFiat: FiatValue): FiatValue {
        val value =
            refFiat.value
                .multiply(feeWei.toBigDecimal())
                .divide(refFee.value.toBigDecimal(), 10, RoundingMode.HALF_UP)
        return FiatValue(value, refFiat.currency)
    }
}
