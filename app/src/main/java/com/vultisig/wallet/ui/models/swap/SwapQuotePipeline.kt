package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.usecases.getTierType
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

/** One source-amount change that should (re)fetch a quote. */
internal data class QuoteInput(
    val address: Pair<SendSrc, SendSrc>,
    val amount: BigDecimal?,
    // True when the change should bypass the typing debounce (percentage / Max / paste).
    val immediate: Boolean,
)

/** How a resolved UTXO network fee should be applied to the form's gas-driven fee state. */
internal sealed interface NetworkFeeUpdate {
    /** Clear the network-fee token/fiat flows and their breakdown rows (stale or unresolvable). */
    data object Clear : NetworkFeeUpdate

    /** Write the resolved UTXO plan fee. */
    data class Set(
        val tokenValue: TokenValue,
        val fiatValue: FiatValue,
        val formattedTokenValue: String,
        val formattedFiatValue: String,
    ) : NetworkFeeUpdate
}

/**
 * Outcome of [SwapQuotePipeline.resolveNetworkFee]: the network-fee mutation to apply (or `null` to
 * leave the gas-driven fee untouched), plus the final swap-enablement and form-error state after
 * the UTXO plan-fee step and balance validation.
 */
internal data class NetworkFeeOutcome(
    val networkFee: NetworkFeeUpdate?,
    val isSwapDisabled: Boolean,
    val formError: UiText?,
)

/**
 * Result of running the swap quote pipeline for one [QuoteInput]. The ViewModel only wires the
 * result into UI state; all provider lookup, per-provider discount fan-out, best-quote resolution,
 * referral handling, fee computation and the SwapKit-UTXO fee special case live in
 * [SwapQuotePipeline].
 */
internal sealed interface SwapQuotePipelineResult {
    /** Empty source field: clear the stale quote silently, with no error (#4712). */
    data object Empty : SwapQuotePipelineResult

    /** The fetch failed; [error] is already mapped for display, [cause]/[tag] are for logging. */
    data class Failure(val error: UiText, val cause: Throwable, val tag: String) :
        SwapQuotePipelineResult

    /**
     * A quote resolved. Carries the fully-computed display state the ViewModel writes verbatim,
     * plus the inputs [resolveNetworkFee] needs for the follow-up UTXO plan-fee / balance pass.
     *
     * The two-step shape is deliberate: the ViewModel applies this display state first (stopping
     * the spinner and showing the quote) and keeps the swap disabled for UTXO swaps until the plan
     * fee is verified by [resolveNetworkFee], so a tap in between can never submit with sats/byte
     * as the total fee.
     */
    data class Success(
        val quote: SwapQuote,
        val provider: SwapProvider,
        // Non-null when the resolved external referral should be cached back into the referral
        // flow.
        val referralCodeToStore: String?,
        val discountInfo: DiscountInfo,
        val swapFeeFiat: FiatValue,
        val srcFiatValue: String,
        val providerUiText: UiText,
        val estimatedDstTokenValue: String,
        val estimatedDstFiatValue: String,
        val expiredAt: Instant?,
        val feeText: String,
        val outboundFeeText: String?,
        val swapFeePercent: String?,
        // Below: inputs for resolveNetworkFee().
        val isUtxoSwap: Boolean,
        val utxoDstAddress: String?,
        val utxoMemo: String?,
        val srcTokenValue: BigInteger,
    ) : SwapQuotePipelineResult
}

/**
 * Runs the swap quote pipeline: from a source-amount change to a renderable result. Stateless apart
 * from its collaborators — every dependency is the same instance the ViewModel uses (notably the
 * cache-bearing [SwapQuoteManager]), so this is constructed by the ViewModel rather than injected
 * separately.
 */
internal class SwapQuotePipeline(
    private val swapQuoteRepository: SwapQuoteRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val referralRepository: ReferralCodeSettingsRepository,
    private val getDiscountBpsUseCase: GetDiscountBpsUseCase,
    private val convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase,
    private val swapQuoteManager: SwapQuoteManager,
    private val swapDiscountChecker: SwapDiscountChecker,
    private val swapGasCalculator: SwapGasCalculator,
    private val swapValidator: SwapValidator,
    private val fiatValueToString: FiatValueToStringMapper,
) {

    /**
     * Resolves the best quote for [input] and returns a renderable [SwapQuotePipelineResult]. On
     * [SwapQuotePipelineResult.Success] the caller must follow up with [resolveNetworkFee].
     * Cancellation is rethrown so the caller's `collectLatest` can still cancel an in-flight fetch.
     *
     * @param isAmountFieldEmpty the live emptiness of the amount field, evaluated by the caller.
     */
    suspend fun resolveQuote(
        input: QuoteInput,
        isAmountFieldEmpty: Boolean,
        vaultId: String?,
        referralCode: String?,
        currentDiscountInfo: DiscountInfo,
        selectedSrcTokenTitle: String?,
        slippageBps: Int?,
        externalRecipient: String?,
    ): SwapQuotePipelineResult {
        val (src, dst) = input.address
        val amount = input.amount
        val srcToken = src.account.token
        val dstToken = dst.account.token

        val srcTokenValue = amount?.movePointRight(src.account.token.decimal)?.toBigInteger()

        // An empty field (the initial state on entry, or a cleared field) is not an error. The
        // empty-input filter was removed so clearing the field clears the stale quote (#4712);
        // without this guard that same empty emission would throw AmountCannotBeZero and flash
        // "Invalid amount" the moment the screen opens. Clear the quote silently and wait for a
        // real
        // amount instead. An explicitly entered zero still falls through to AmountCannotBeZero
        // below.
        if (isAmountFieldEmpty) {
            return SwapQuotePipelineResult.Empty
        }

        return try {
            if (srcTokenValue == null || srcTokenValue <= BigInteger.ZERO) {
                throw SwapException.AmountCannotBeZero("Amount must be positive")
            }
            if (srcToken == dstToken) {
                throw SwapException.SameAssets("Can't swap same assets ${srcToken.id})")
            }

            val tokenValue = convertTokenAndValueToTokenValue(srcToken, srcTokenValue)

            val allEligibleProviders = swapQuoteRepository.getEligibleProviders(srcToken, dstToken)
            // External recipient (#4858): only the native protocols (THORChain / Maya) route the
            // output to a custom address — they carry it as the memo `destination`. Every general
            // aggregator (1inch / Kyber / Jupiter / LI.FI / SwapKit) is dropped when a recipient is
            // set, never silently misrouting funds. This mirrors the cross-platform decision
            // (vultisig-sdk#757 / vultisig-windows#4152): threading a recipient through the
            // aggregators is a deferred follow-up there too.
            val eligibleProviders =
                if (externalRecipient.isNullOrBlank()) {
                    allEligibleProviders
                } else {
                    allEligibleProviders.filter {
                        it == SwapProvider.THORCHAIN || it == SwapProvider.MAYA
                    }
                }
            if (eligibleProviders.isEmpty()) {
                throw SwapException.SwapIsNotSupported("Swap is not supported for this pair")
            }

            val currency = appCurrencyRepository.currency.first()

            val baselineReferral =
                referralCode ?: vaultId?.let { referralRepository.getExternalReferralBy(it) }

            val candidates = coroutineScope {
                eligibleProviders
                    .map { p ->
                        async {
                            val discount =
                                vaultId?.let { id ->
                                    getDiscountBpsUseCase.invoke(id, p).takeIf { bps -> bps != 0 }
                                }
                            QuoteCandidate(
                                provider = p,
                                vultBPSDiscount = discount,
                                referral = baselineReferral,
                            )
                        }
                    }
                    .awaitAll()
            }

            val resolution =
                swapQuoteManager.resolveBestQuote(
                    candidates = candidates,
                    src = src,
                    dst = dst,
                    srcToken = srcToken,
                    dstToken = dstToken,
                    srcTokenValue = srcTokenValue,
                    tokenValue = tokenValue,
                    currency = currency,
                    amount = amount,
                    selectedSrcTokenTitle = selectedSrcTokenTitle,
                    slippageBps = slippageBps,
                    externalRecipient = externalRecipient,
                )
            // Map the sealed result: a typed fetch failure becomes a Failure carrying its
            // already-mapped error; only a Success continues into fee processing.
            val bestQuote =
                when (resolution) {
                    is QuoteResolution.Failure ->
                        return SwapQuotePipelineResult.Failure(
                            error =
                                recipientAwareError(
                                    resolution.formError,
                                    resolution.cause,
                                    externalRecipient,
                                ),
                            cause = resolution.cause,
                            tag = resolution.tag,
                        )
                    is QuoteResolution.Success -> resolution.best
                }

            buildSuccess(
                bestQuote = bestQuote,
                src = src,
                srcTokenValue = srcTokenValue,
                tokenValue = tokenValue,
                currentDiscountInfo = currentDiscountInfo,
            )
        } catch (e: SwapException) {
            SwapQuotePipelineResult.Failure(
                error =
                    recipientAwareError(
                        swapQuoteManager.mapSwapExceptionToFormError(
                            e,
                            srcToken,
                            selectedSrcTokenTitle,
                        ),
                        e,
                        externalRecipient,
                    ),
                cause = e,
                tag = "swapError",
            )
        } catch (e: SwapKitError) {
            SwapQuotePipelineResult.Failure(
                error = swapQuoteManager.mapSwapKitErrorToFormError(e),
                cause = e,
                tag = "swapKitError",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SwapQuotePipelineResult.Failure(
                error = UiText.StringResource(R.string.swap_error_quote_failed),
                cause = e,
                tag = "swapUnexpectedError",
            )
        }
    }

    /**
     * Rewrites a quote failure into a recipient-aware message when an external recipient is active.
     *
     * Setting a recipient narrows the eligible providers to THORChain/Maya (the only protocols that
     * route output to a custom address — see the native-only filter above). When the pair has no
     * such route at all, the bare "not supported" error never explains that the recipient is the
     * cause, so name it (#4858).
     *
     * Sub-minimum failures are deliberately NOT rewritten here: THORChain surfaces the concrete
     * required minimum ("Minimum amount is X") via [SwapException.SmallSwapAmount], which is more
     * actionable than a generic recipient note and must not be masked.
     */
    private fun recipientAwareError(
        error: UiText,
        cause: Throwable,
        externalRecipient: String?,
    ): UiText {
        if (externalRecipient.isNullOrBlank()) return error
        return when (cause) {
            is SwapException.SwapIsNotSupported,
            is SwapException.SwapRouteNotAvailable ->
                UiText.StringResource(R.string.swap_external_recipient_unsupported)
            else -> error
        }
    }

    /** Builds the display-ready [SwapQuotePipelineResult.Success] from the winning quote. */
    private suspend fun buildSuccess(
        bestQuote: BestQuote,
        src: SendSrc,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        currentDiscountInfo: DiscountInfo,
    ): SwapQuotePipelineResult.Success {
        val quoteResult = bestQuote.result
        val provider = quoteResult.provider
        val srcToken = src.account.token

        val vultBPSDiscount = bestQuote.candidate.vultBPSDiscount
        val referral = bestQuote.candidate.referral

        var discountInfo = currentDiscountInfo
        var referralCodeToStore: String? = null
        if (provider == SwapProvider.THORCHAIN) {
            referral?.let { code ->
                val tierType = vultBPSDiscount?.getTierType()
                val result =
                    swapDiscountChecker.checkReferralBpsDiscount(
                        tierType,
                        srcToken,
                        tokenValue,
                        code,
                    )
                referralCodeToStore = result.referralCode
                discountInfo =
                    discountInfo.copy(
                        referralBpsDiscount = result.referralBpsDiscount,
                        referralBpsDiscountFiatValue = result.referralBpsDiscountFiatValue,
                    )
            }
        } else {
            discountInfo =
                discountInfo.copy(referralBpsDiscount = null, referralBpsDiscountFiatValue = null)
        }

        val vultResult =
            swapDiscountChecker.checkVultBpsDiscount(srcToken, tokenValue, vultBPSDiscount)
        discountInfo =
            discountInfo.copy(
                vultBpsDiscount = vultResult.vultBpsDiscount,
                vultBpsDiscountFiatValue = vultResult.vultBpsDiscountFiatValue,
                tierType = vultResult.tierType,
            )

        // SwapKit BTC settles by broadcasting the provider's PSBT, whose miner fee is the only
        // network cost — and it is already surfaced as the UTXO plan network fee below. SwapKit
        // reports that same deposit cost as its inbound fee, so counting it again as a swap fee
        // would
        // double-count the BTC network cost in the headline total (iOS shows it once). Zero the
        // swap-fee contribution and the breakdown row so Total reconciles to Network Fee alone; the
        // affiliate fee is already baked into expectedDstValue.
        val quote = quoteResult.quote
        val isSwapKitUtxoSwap =
            quote is SwapQuote.SwapKit && srcToken.chain.standard == TokenStandard.UTXO
        val effectiveSwapFeeFiat =
            if (isSwapKitUtxoSwap) FiatValue(BigDecimal.ZERO, quoteResult.swapFeeFiat.currency)
            else quoteResult.swapFeeFiat
        val feeText =
            if (isSwapKitUtxoSwap) fiatValueToString(effectiveSwapFeeFiat, asFee = true)
            else quoteResult.feeText

        // Determine destination address and memo for UTXO plan fee computation.
        val utxoFeeData: Pair<String, String?>? =
            when (quote) {
                is SwapQuote.ThorChain ->
                    (quote.data.router ?: quote.data.inboundAddress ?: src.address.address) to
                        quote.data.memo
                is SwapQuote.MayaChain ->
                    (quote.data.inboundAddress ?: src.address.address) to quote.data.memo
                // SwapKit BTC is a PSBT deposit to targetAddress; route it through the same UTXO
                // plan-fee path so the network fee is computed and swap() doesn't abort with
                // invalid_gas_fee_calculation.
                is SwapQuote.SwapKit ->
                    if (srcToken.chain.standard == TokenStandard.UTXO) {
                        quote.data.targetAddress to quote.data.memo
                    } else null
                else -> null
            }
        val isUtxoSwap =
            utxoFeeData != null &&
                srcToken.chain.standard == TokenStandard.UTXO &&
                srcToken.chain != Chain.Cardano

        return SwapQuotePipelineResult.Success(
            quote = quote,
            provider = provider,
            referralCodeToStore = referralCodeToStore,
            discountInfo = discountInfo,
            swapFeeFiat = effectiveSwapFeeFiat,
            srcFiatValue = quoteResult.srcFiatValueText,
            providerUiText = quoteResult.providerUiText,
            estimatedDstTokenValue = quoteResult.estimatedDstTokenValue,
            estimatedDstFiatValue = quoteResult.estimatedDstFiatValue,
            expiredAt = quote.expiredAt,
            feeText = feeText,
            outboundFeeText = quoteResult.outboundFeeText,
            swapFeePercent = quoteResult.swapFeePercent,
            isUtxoSwap = isUtxoSwap,
            utxoDstAddress = utxoFeeData?.first,
            utxoMemo = utxoFeeData?.second,
            srcTokenValue = srcTokenValue,
        )
    }

    /**
     * Follow-up to a [SwapQuotePipelineResult.Success]: resolves the UTXO plan fee (when
     * applicable) and runs the final balance validation, returning the [NetworkFeeOutcome] the
     * ViewModel applies. For non-UTXO swaps the network fee is left untouched (the gas flow owns
     * it) and only balance validation runs.
     *
     * @param networkFeeTokenValue the current gas-driven network fee, used for the balance check
     *   when no UTXO plan fee is computed.
     */
    suspend fun resolveNetworkFee(
        result: SwapQuotePipelineResult.Success,
        src: SendSrc,
        vaultId: String?,
        gasFee: TokenValue?,
        gasFeeChain: Chain?,
        networkFeeTokenValue: TokenValue?,
    ): NetworkFeeOutcome {
        val srcToken = src.account.token
        // Base state mirrors the display update: UTXO swaps stay disabled until the plan fee lands.
        var isSwapDisabled = result.isUtxoSwap
        var formError: UiText? = null
        var networkFee: NetworkFeeUpdate? = null
        var effectiveNetworkFeeTokenValue = networkFeeTokenValue

        if (result.isUtxoSwap) {
            val currentGasFee = gasFee?.takeIf { gasFeeChain == srcToken.chain }
            if (currentGasFee != null && vaultId != null) {
                when (
                    val planFee =
                        swapGasCalculator.resolveUtxoPlanFee(
                            vaultId = vaultId,
                            srcToken = srcToken,
                            srcAddress = src.address.address,
                            dstAddress = result.utxoDstAddress!!,
                            memo = result.utxoMemo,
                            tokenAmountInt = result.srcTokenValue,
                            gasFee = currentGasFee,
                        )
                ) {
                    is UtxoPlanFeeResult.Success -> {
                        networkFee =
                            NetworkFeeUpdate.Set(
                                tokenValue = planFee.estimated.tokenValue,
                                fiatValue = planFee.estimated.fiatValue,
                                formattedTokenValue = planFee.estimated.formattedTokenValue,
                                formattedFiatValue = planFee.estimated.formattedFiatValue,
                            )
                        effectiveNetworkFeeTokenValue = planFee.estimated.tokenValue
                        isSwapDisabled = false
                    }
                    UtxoPlanFeeResult.InsufficientUtxos -> {
                        networkFee = NetworkFeeUpdate.Clear
                        effectiveNetworkFeeTokenValue = null
                        isSwapDisabled = true
                        formError = UiText.StringResource(R.string.insufficient_utxos_error)
                    }
                    UtxoPlanFeeResult.Unavailable -> {
                        networkFee = NetworkFeeUpdate.Clear
                        effectiveNetworkFeeTokenValue = null
                        isSwapDisabled = true
                    }
                }
            } else {
                // gasFeeChain lags srcToken.chain after a token switch: clear any stale fee from
                // the
                // previous chain. isSwapDisabled stays true until the plan fee is verified.
                networkFee = NetworkFeeUpdate.Clear
                effectiveNetworkFeeTokenValue = null
            }
        }

        val balanceError =
            swapValidator.validateBalanceForSwap(
                src,
                result.srcTokenValue,
                effectiveNetworkFeeTokenValue,
            )
        if (balanceError != null) {
            isSwapDisabled = true
            formError = balanceError.formError
        }

        return NetworkFeeOutcome(
            networkFee = networkFee,
            isSwapDisabled = isSwapDisabled,
            formError = formError,
        )
    }
}
