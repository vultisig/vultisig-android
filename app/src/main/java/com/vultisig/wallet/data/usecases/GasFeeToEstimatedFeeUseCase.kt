package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import kotlinx.coroutines.flow.first
import java.math.BigInteger
import javax.inject.Inject

internal interface GasFeeToEstimatedFeeUseCase :
    suspend (GasFeeParams) -> EstimatedGasFee

internal class GasFeeToEstimatedFeeUseCaseImpl @Inject constructor(
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val tokenRepository: TokenRepository,
    private val convertTokenValueToString: TokenValueToStringWithUnitMapper,
    ) : GasFeeToEstimatedFeeUseCase {

    override suspend fun invoke(from: GasFeeParams): EstimatedGasFee {
        val appCurrency = appCurrencyRepository.currency.first()

        val nativeToken = tokenRepository.getNativeToken(from.selectedToken.chain.id)

        val chain = nativeToken.chain

        var tokenValue = TokenValue(
            value = from.gasFee.value.multiply(from.gasLimit),
            unit = from.gasFee.unit,
            decimals = from.gasFee.decimals
        )

        val fiatFees = convertTokenValueToFiat(
            nativeToken,
            tokenValue,
            appCurrency
        )


        tokenValue = when {
            chain.feeUnit.equals("Gwei", ignoreCase = true) ->
                tokenValue.copy(
                    value = tokenValue.value.divide(BigInteger.TEN.pow(9)),
                    unit = nativeToken.ticker,
                )

            chain == Chain.Bitcoin && from.perUnit->
                tokenValue.copy(
                    unit = chain.feeUnit,
                )

            else ->
                tokenValue.copy(
                    unit = nativeToken.ticker,
                )
        }

        return EstimatedGasFee(
            formattedFiatValue = fiatValueToStringMapper.map(fiatFees),
            formattedTokenValue = convertTokenValueToString(tokenValue),
            fiatValue = fiatFees,
        )
    }
}