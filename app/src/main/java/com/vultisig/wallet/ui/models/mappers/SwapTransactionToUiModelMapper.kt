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
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.models.swap.clampDstFiatToSrcFiat
import com.vultisig.wallet.ui.models.swap.formatSwapKitProviderLabel
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
                SwapProvider.LIFI -> from.dstToken

                SwapProvider.ONEINCH,
                SwapProvider.KYBER,
                SwapProvider.SWAPKIT -> tokenRepository.getNativeToken(from.srcToken.chain.id)

                SwapProvider.JUPITER -> from.srcToken
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

        // SwapTransaction carries no destination fiat, so it is recomputed here for the verify and
        // keysign screens. Apply the same value-preserving clamp the swap form uses (#4878) so an
        // illiquid token's inflated market mark can't reappear on the screens the user signs from.
        val srcFiat = convertTokenValueToFiat(from.srcToken, from.srcTokenValue, currency)
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
                    fiatValue = fiatValueToStringMapper(swapFeeFiat ?: quotesFeesFiat, asFee = true),
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
            swapFeePercent = from.swapFeePercent,
            swapFeeIncludedInRate = from.swapFeeIncludedInRate,
            swapFeeHidden = isSwapKitUtxoSwap,
            vultBpsDiscount = from.vultBpsDiscount,
            vultBpsDiscountFiatValue = from.vultBpsDiscountFiatValue,
            referralBpsDiscount = from.referralBpsDiscount,
            referralBpsDiscountFiatValue = from.referralBpsDiscountFiatValue,
        )
    }
}
