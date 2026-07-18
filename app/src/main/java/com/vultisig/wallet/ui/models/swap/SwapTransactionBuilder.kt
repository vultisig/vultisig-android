package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService
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
import com.vultisig.wallet.data.repositories.swap.convertToTokenValue
import java.math.BigInteger
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
                // Aggregators can return a non-positive tx.gas; fall back to the standard EVM swap
                // unit so a malformed (zero or negative) gas limit never reaches the shared signed
                // payload (matches SwapQuoteManager's fee path).
                //
                // A user gas-limit override (#4858) replaces the aggregator estimate. OneInchSwap
                // signs with maxOf(tx.gas, ethSpecific.gasLimit), so set BOTH to the override —
                // maxOf(x, x) = x — making it effective whether the user raises or lowers the
                // limit. Auto (null/non-positive) keeps the estimate and the current behavior.
                val gasLimit =
                    gasLimitOverride?.takeIf { it > 0L }
                        ?: (quote.data.tx.gas.takeIf { it > 0L }
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

                val (displayGasFees, displayGasFeeFiat) =
                    displayedSwapGasFee(
                        specific = specific,
                        srcToken = srcToken,
                        gasLimit = gasLimit,
                        gasFee = gasFee,
                        gasFeeFiatValue = gasFeeFiatValue,
                        estimatedNetworkFeeTokenValue = estimatedNetworkFeeTokenValue,
                        estimatedNetworkFeeFiatValue = estimatedNetworkFeeFiatValue,
                    )
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
                    id = UUID.randomUUID().toString(),
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

    /**
     * Displayed/staged EVM swap network fee (never the signed tx): valued at the exact gas
     * parameters stamped into the payload — [BlockChainSpecific.Ethereum.maxFeePerGasWei] times the
     * co-signer-aligned display gas limit ([evmSwapDisplayGasLimit], falling back to
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
        srcToken: Coin,
        gasLimit: Long,
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
        // Floor an override / route gas exactly as the joiner does so both devices agree, even for
        // OP-stack L2s (null → DEFAULT_SWAP_LIMIT) and sub-floor overrides.
        val displayLimit =
            evmSwapDisplayGasLimit(srcToken, gasLimit) ?: EthereumFeeService.DEFAULT_SWAP_LIMIT
        val feeWei = specific.maxFeePerGasWei * displayLimit
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
