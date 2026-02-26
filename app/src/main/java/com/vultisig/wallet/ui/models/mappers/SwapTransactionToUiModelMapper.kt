package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.resolveprovider.ResolveProviderUseCase
import com.vultisig.wallet.data.usecases.resolveprovider.SwapSelectionContext
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal interface SwapTransactionToUiModelMapper :
    SuspendMapperFunc<SwapTransaction, SwapTransactionUiModel>

internal class SwapTransactionToUiModelMapperImpl @Inject constructor(
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val resolveProviderUseCase: ResolveProviderUseCase,
    private val tokenRepository: TokenRepository,
) : SwapTransactionToUiModelMapper {
    override suspend fun invoke(from: SwapTransaction): SwapTransactionUiModel {
        val currency = appCurrencyRepository.currency.first()
        val provider = resolveProviderUseCase(
            SwapSelectionContext(
                from.srcToken,
                from.dstToken,
                from.srcTokenValue
            )
        ) ?: error("provider not found")

        val tokenValue = when (provider) {
            SwapProvider.THORCHAIN, SwapProvider.MAYA ->
                from.dstToken

            SwapProvider.ONEINCH, SwapProvider.KYBER ->
                tokenRepository.getNativeToken(from.srcToken.chain.id)

            SwapProvider.LIFI -> getLiFiProviderFee(from)

            SwapProvider.JUPITER ->
                from.srcToken
        }

        val quotesFeesFiat = convertTokenValueToFiat(
            tokenValue,
            from.estimatedFees,
            currency
        )

        return SwapTransactionUiModel(
            src = ValuedToken(
                value = mapTokenValueToDecimalUiString(from.srcTokenValue),
                token = from.srcToken,
                fiatValue = fiatValueToStringMapper(
                    convertTokenValueToFiat(
                        from.srcToken,
                        from.srcTokenValue,
                        currency
                    )
                ),
            ),
            dst = ValuedToken(
                value = mapTokenValueToDecimalUiString(from.expectedDstTokenValue),
                token = from.dstToken,
                fiatValue = fiatValueToStringMapper(
                    convertTokenValueToFiat(
                        from.dstToken,
                        from.expectedDstTokenValue,
                        currency
                    )
                ),
            ),
            hasConsentAllowance = from.isApprovalRequired,
            providerFee = ValuedToken(
                token = tokenValue,
                value = from.estimatedFees.value.toString(),
                fiatValue = fiatValueToStringMapper(quotesFeesFiat),
            ),
            networkFee = ValuedToken(
                token = from.srcToken,
                value = mapTokenValueToDecimalUiString(from.gasFees),
                fiatValue = fiatValueToStringMapper(from.gasFeeFiatValue),
            ),
            networkFeeFormatted = mapTokenValueToDecimalUiString(from.gasFees)
                    + " ${from.gasFees.unit}",
            totalFee = fiatValueToStringMapper(quotesFeesFiat + from.gasFeeFiatValue),
            provider = provider.getSwapProviderId()
        )
    }

    private suspend fun getLiFiProviderFee(from: SwapTransaction): Coin {
        val estimateFeesUnit = from.estimatedFees.unit
        val nativeCoin = tokenRepository.getNativeToken(from.srcToken.chain.id)

        return if (estimateFeesUnit.equals(nativeCoin.ticker, true)) {
            return nativeCoin
        } else {
            from.srcToken
        }
    }
}