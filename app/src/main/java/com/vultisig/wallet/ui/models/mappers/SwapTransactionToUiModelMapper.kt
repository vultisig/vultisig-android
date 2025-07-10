package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.SuspendMapperFunc
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
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
    private val swapQuoteRepository: SwapQuoteRepository,
    private val tokenRepository: TokenRepository,
) : SwapTransactionToUiModelMapper {
    override suspend fun invoke(from: SwapTransaction): SwapTransactionUiModel {
        val currency = appCurrencyRepository.currency.first()
        val provider: SwapProvider = swapQuoteRepository
            .resolveProvider(from.srcToken, from.dstToken) ?: error("provider not found")
        val tokenValue = when (provider) {
            SwapProvider.THORCHAIN, SwapProvider.MAYA ->
                from.dstToken

            SwapProvider.LIFI, SwapProvider.ONEINCH, SwapProvider.KYBER ->
                tokenRepository.getNativeToken(from.srcToken.chain.id)

            SwapProvider.JUPITER ->
                from.srcToken

        }

        val fiatFees = convertTokenValueToFiat(
            tokenValue,
            from.estimatedFees,
            currency
        )
        return SwapTransactionUiModel(
            src = ValuedToken(
                value = mapTokenValueToDecimalUiString(from.srcTokenValue),
                token = from.srcToken,
                fiatValue = fiatValueToStringMapper.map(
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
                fiatValue = fiatValueToStringMapper.map(
                    convertTokenValueToFiat(
                        from.dstToken,
                        from.expectedDstTokenValue,
                        currency
                    )
                ),
            ),

            hasConsentAllowance = from.isApprovalRequired,
            totalFee = fiatValueToStringMapper.map(fiatFees + from.gasFeeFiatValue)
        )
    }

}
