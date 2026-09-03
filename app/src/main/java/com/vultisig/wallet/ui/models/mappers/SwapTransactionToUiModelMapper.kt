package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.swap.FormatLimitOrderLabelsUseCase
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.models.swap.bpsOfSourceFiat
import com.vultisig.wallet.ui.models.swap.clampDstFiatToSrcFiat
import com.vultisig.wallet.ui.models.swap.formatPriceImpact
import com.vultisig.wallet.ui.models.swap.formatSwapKitProviderLabel
import com.vultisig.wallet.ui.models.swap.signedLimitOrder
import com.vultisig.wallet.ui.models.swap.signedMinimumOutput
import com.vultisig.wallet.ui.models.swap.swapFeeRow
import javax.inject.Inject
import kotlinx.coroutines.flow.first

internal interface SwapTransactionToUiModelMapper :
    SuspendMapperFunc<SwapTransaction, SwapTransactionUiModel>

internal class SwapTransactionToUiModelMapperImpl
@Inject
constructor(
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val tokenRepository: TokenRepository,
    private val formatLimitOrderLabels: FormatLimitOrderLabelsUseCase,
) : SwapTransactionToUiModelMapper {
    override suspend fun invoke(from: SwapTransaction): SwapTransactionUiModel {
        val currency = appCurrencyRepository.currency.first()
        val provider: SwapProvider =
            when (val payload = from.payload) {
                is SwapPayload.ThorChain -> SwapProvider.THORCHAIN
                is SwapPayload.MayaChain -> SwapProvider.MAYA
                is SwapPayload.EVM ->
                    SwapProvider.entries.find { it.getSwapProviderId() == payload.data.provider }
                        ?: error("Unknown EVM provider: ${payload.data.provider}")
                is SwapPayload.SwapKit -> SwapProvider.SWAPKIT
            }

        // SwapKit `/track` correlation key, threaded onto the tx-history row. EVM/Solana SwapKit
        // routes carry it on the EVM payload; native routes (PSBT/TON/…) on the SwapKit payload.
        val swapId: String? =
            when (val payload = from.payload) {
                is SwapPayload.EVM -> payload.data.swapId?.takeIf { it.isNotBlank() }
                is SwapPayload.SwapKit -> payload.data.swapId.takeIf { it.isNotBlank() }
                is SwapPayload.ThorChain,
                is SwapPayload.MayaChain -> null
            }

        val tokenValue =
            when (provider) {
                SwapProvider.THORCHAIN,
                SwapProvider.MAYA,
                SwapProvider.LIFI,
                SwapProvider.JUPITER -> from.dstToken

                SwapProvider.ONEINCH,
                SwapProvider.KYBER,
                SwapProvider.SWAPKIT -> tokenRepository.getNativeToken(from.srcToken.chain.id)
            }

        // SwapKit UTXO-family sources (Bitcoin PSBT deposit, Cardano CBOR deposit) settle by
        // broadcasting a deposit whose on-chain miner fee is the only network cost, already
        // surfaced
        // as the Network Fee. SwapKit reports that same deposit cost as its wire inbound fee, so
        // showing it again as a Swap Fee here would double-count the source-chain network cost in
        // the total — exactly what the swap form avoids by hiding the row (#5321). Mirror the form:
        // hide the Swap Fee row and drop its contribution to the total (#5358).
        val isSwapKitUtxoSwap =
            provider == SwapProvider.SWAPKIT && from.srcToken.chain.standard == TokenStandard.UTXO

        val quotesFeesFiat = convertTokenValueToFiat(tokenValue, from.estimatedFees, currency)

        // THORChain / MayaChain quotes carry a fee breakdown (affiliate + outbound + liquidity).
        // When present, show the affiliate-only "Swap Fee" and a separate "Outbound Fee" row — the
        // same decomposition the swap form does — instead of rendering the opaque total under the
        // "Swap Fee" label (#5061). Aggregators report no breakdown, so both stay null and the
        // single estimated-fees total is shown as before.
        val swapFeeFiat = from.swapFee?.let { convertTokenValueToFiat(from.dstToken, it, currency) }
        val outboundFeeFiat =
            from.outboundFee?.let { convertTokenValueToFiat(from.dstToken, it, currency) }

        // Headline total mirrors the swap form: gas + affiliate + outbound, dropping the liquidity
        // (asset) component already reflected in the destination amount. Falls back to the opaque
        // total when there is no breakdown.
        val feesFiatForTotal =
            if (swapFeeFiat != null) {
                outboundFeeFiat?.let { swapFeeFiat + it } ?: swapFeeFiat
            } else {
                quotesFeesFiat
            }

        // The liquidity component stays out of the fee total (it is already reflected in the
        // destination amount), which on a thin route leaves most of what the user gave up
        // unexplained. Surface it as its own Price Impact row, formatted as the swap form does
        // (#5335).
        val priceImpactDisplay = formatPriceImpact(from.priceImpact)

        // SwapTransaction carries no destination fiat, so it is recomputed here for the verify and
        // keysign screens. Apply the same value-preserving clamp the swap form uses (#4878) so an
        // illiquid token's inflated market mark can't reappear on the screens the user signs from.
        val srcFiat = convertTokenValueToFiat(from.srcToken, from.srcTokenValue, currency)

        // The discount rows and the fee they were taken off have to come off one price snapshot:
        // the fee above them is re-valued here at the current price, so pairing it with the
        // quote-time discount the transaction recorded would leave the breakdown unable to
        // reconcile once the source price moves. A source with no price prices no discount at all,
        // which [swapFeeRow] answers by leaving the fee ungrossed and the rows hidden.
        val vultDiscountFiat = bpsOfSourceFiat(srcFiat, from.vultBpsDiscount)
        val referralDiscountFiat = bpsOfSourceFiat(srcFiat, from.referralBpsDiscount)
        val feeRow =
            swapFeeRow(
                provider = provider,
                netFee = swapFeeFiat ?: quotesFeesFiat,
                listRate = from.swapFeePercent,
                discountBps = listOf(from.vultBpsDiscount, from.referralBpsDiscount),
                pricedDiscounts = listOf(vultDiscountFiat, referralDiscountFiat),
            )
        // A row that subtracts from a fee it was never added to can't be reconciled against the
        // total, so the rows follow the fee: they appear only when it carries the list rate.
        val showsDiscountRows = feeRow.isListRate
        val dstFiat =
            clampDstFiatToSrcFiat(
                srcFiat,
                convertTokenValueToFiat(from.dstToken, from.expectedDstTokenValue, currency),
            )

        // Display-only label. `provider` below stays the canonical id (the behavioral key that
        // gates SwapKit `/track` settlement); SwapKit collapses every sub-provider onto the
        // canonical `"SwapKit"` id, so render the persisted sub-provider (Chainflip / NEAR /
        // Garden)
        // for the display label instead — matching what the joined device produces via
        // `formatSwapKitProviderLabel`. Other providers reuse their specific id.
        val providerLabel =
            if (provider == SwapProvider.SWAPKIT) {
                val subProvider =
                    when (val payload = from.payload) {
                        is SwapPayload.EVM -> payload.data.subProvider
                        is SwapPayload.SwapKit -> payload.data.subProvider
                        else -> null
                    }
                formatSwapKitProviderLabel(subProvider)
            } else {
                provider.getSwapProviderId()
            }

        // Limit-order confirmation labels, present only for a `=<` order built with them. Both are
        // formatted here rather than at build time so the price lands in the user's currency and
        // the expiry reuses the same string resources as the form's pills. The cosigner formats the
        // same pair through the same use case, from the memo it is asked to sign.
        val limitMemo = signedLimitOrder(memo = from.memo, dstToken = from.dstToken)
        val regular = from as? SwapTransaction.RegularSwapTransaction
        val limitTargetPrice = regular?.limitOrderTargetPrice?.takeIf { limitMemo != null }
        val limitExpiryHours = regular?.limitOrderExpiryHours?.takeIf { limitMemo != null }
        val limitLabels =
            if (limitTargetPrice != null && limitExpiryHours != null) {
                formatLimitOrderLabels(
                    srcToken = from.srcToken,
                    dstToken = from.dstToken,
                    targetPrice = limitTargetPrice,
                    expiryHours = limitExpiryHours,
                )
            } else {
                null
            }

        // The floor the signed memo enforces, or null when it enforces none — which is the case
        // for every aggregator route and for a THORChain/Maya swap left on "Auto" slippage, where
        // the node returns a memo with no LIM at all (#5711).
        val minPayout =
            signedMinimumOutput(payload = from.payload, memo = from.memo, dstToken = from.dstToken)
                ?.let { mapTokenValueToDecimalUiString(it) }

        return SwapTransactionUiModel(
            src =
                ValuedToken(
                    value = mapTokenValueToDecimalUiString(from.srcTokenValue),
                    token = from.srcToken,
                    fiatValue = fiatValueToStringMapper(srcFiat),
                ),
            dst =
                ValuedToken(
                    value = mapTokenValueToDecimalUiString(from.expectedDstTokenValue),
                    token = from.dstToken,
                    fiatValue = fiatValueToStringMapper(dstFiat),
                ),
            hasConsentAllowance = from.isApprovalRequired,
            providerFee =
                ValuedToken(
                    token = tokenValue,
                    value = (from.swapFee ?: from.estimatedFees).value.toString(),
                    // List-rate fee, matching the swap form: the charged affiliate fee plus the
                    // discounts the rows below restate, so the row is not netted twice.
                    fiatValue = fiatValueToStringMapper(feeRow.fee, asFee = true),
                ),
            outboundFee = outboundFeeFiat?.let { fiatValueToStringMapper(it, asFee = true) },
            networkFee =
                ValuedToken(
                    token = from.srcToken,
                    value = mapTokenValueToDecimalUiString(from.gasFees),
                    fiatValue = fiatValueToStringMapper(from.gasFeeFiatValue, asFee = true),
                ),
            networkFeeFormatted =
                mapTokenValueToDecimalUiString(from.gasFees) + " ${from.gasFees.unit}",
            // The Swap Fee adds nothing to the total when it is baked into the quoted rate (1inch)
            // or already surfaced as the Network Fee (SwapKit UTXO deposit) — otherwise an
            // aggregator's opaque `estimatedFees` (gas for 1inch, the deposit cost for SwapKit
            // UTXO)
            // would be counted a second time on top of the Network Fee (#5358, #5334, #5335,
            // #5321).
            totalFee =
                fiatValueToStringMapper(
                    if (from.swapFeeIncludedInRate || isSwapKitUtxoSwap) from.gasFeeFiatValue
                    else feesFiatForTotal + from.gasFeeFiatValue,
                    asFee = true,
                ),
            provider = provider.getSwapProviderId(),
            providerLabel = providerLabel,
            swapId = swapId,
            expectedDstDecimal = from.expectedDstTokenValue.decimal.toPlainString(),
            externalRecipient =
                (from as? SwapTransaction.RegularSwapTransaction)?.externalRecipient,
            // Resolved here rather than read off the transaction: the fee above is re-valued at
            // the current price, and a source that has lost its price since Continue can no longer
            // be grossed up — so the rate the form recorded would then sit over a discounted
            // amount (see [swapFeeRow]).
            swapFeePercent = feeRow.percent,
            swapFeeIncludedInRate = from.swapFeeIncludedInRate,
            swapFeeHidden = isSwapKitUtxoSwap,
            vultBpsDiscount = from.vultBpsDiscount,
            // Shown exactly when the fee above was grossed to the list rate, and never falling
            // back to the amount the form recorded: that one was priced at quote time while the fee
            // row is re-valued here, so pairing them is what let a price move unbalance the panel.
            vultBpsDiscountFiatValue =
                vultDiscountFiat
                    ?.takeIf { showsDiscountRows }
                    ?.let { fiatValueToStringMapper(it, asFee = true) },
            referralBpsDiscount = from.referralBpsDiscount,
            referralBpsDiscountFiatValue =
                referralDiscountFiat
                    ?.takeIf { showsDiscountRows }
                    ?.let { fiatValueToStringMapper(it, asFee = true) },
            minPayout = minPayout,
            priceImpactPercent = priceImpactDisplay?.percent,
            priceImpactLevel = priceImpactDisplay?.level,
            // The memo decides this, not the labels below it: a `=<` order whose lifetime this
            // app has no pill for is still an order, and its amount is still the enforced floor
            // rather than an expectation. The rows that need the labels null-check them
            // themselves (CodeRabbit, #5734).
            isLimitOrder = limitMemo != null,
            limitTargetPriceLabel = limitLabels?.targetPriceLabel,
            limitExpiryLabel = limitLabels?.expiryLabel,
        )
    }
}
