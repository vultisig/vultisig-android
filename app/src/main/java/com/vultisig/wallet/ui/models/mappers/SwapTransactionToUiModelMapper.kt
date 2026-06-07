package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
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

        val quotesFeesFiat = convertTokenValueToFiat(tokenValue, from.estimatedFees, currency)

        // SwapKit collapses every sub-provider onto the canonical `"SwapKit"` id, so render the
        // persisted sub-provider (Chainflip / NEAR / Garden) for the done/history label instead —
        // matching the live form/verify label. Other providers keep their specific id.
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
                    fiatValue =
                        fiatValueToStringMapper(
                            convertTokenValueToFiat(from.srcToken, from.srcTokenValue, currency)
                        ),
                ),
            dst =
                ValuedToken(
                    value = mapTokenValueToDecimalUiString(from.expectedDstTokenValue),
                    token = from.dstToken,
                    fiatValue =
                        fiatValueToStringMapper(
                            convertTokenValueToFiat(
                                from.dstToken,
                                from.expectedDstTokenValue,
                                currency,
                            )
                        ),
                ),
            hasConsentAllowance = from.isApprovalRequired,
            providerFee =
                ValuedToken(
                    token = tokenValue,
                    value = from.estimatedFees.value.toString(),
                    fiatValue = fiatValueToStringMapper(quotesFeesFiat, asFee = true),
                ),
            networkFee =
                ValuedToken(
                    token = from.srcToken,
                    value = mapTokenValueToDecimalUiString(from.gasFees),
                    fiatValue = fiatValueToStringMapper(from.gasFeeFiatValue, asFee = true),
                ),
            networkFeeFormatted =
                mapTokenValueToDecimalUiString(from.gasFees) + " ${from.gasFees.unit}",
            totalFee = fiatValueToStringMapper(quotesFeesFiat + from.gasFeeFiatValue, asFee = true),
            provider = providerLabel,
            swapId = swapId,
        )
    }
}
