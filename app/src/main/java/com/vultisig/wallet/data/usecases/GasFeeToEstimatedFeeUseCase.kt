package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

internal interface GasFeeToEstimatedFeeUseCase :
    suspend (GasFeeParams) -> Pair<String, String>

internal class GasFeeToEstimatedFeeUseCaseImpl @Inject constructor(
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val tokenRepository: TokenRepository,
    private val convertTokenValueToString: TokenValueToStringWithUnitMapper,
    ) : GasFeeToEstimatedFeeUseCase {

    override suspend fun invoke(from: GasFeeParams): Pair<String, String> {
        val appCurrency = appCurrencyRepository.currency.first()

        var tokenValue = TokenValue(
            value = from.gasFee.value.multiply(from.gasLimit),
            unit = from.gasFee.unit,
            decimals = from.gasFee.decimals
        )
        val nativeToken = tokenRepository.getNativeToken(from.selectedToken.chain.id)
        val fiatFees = convertTokenValueToFiat(
            nativeToken,
            tokenValue,
            appCurrency
        )

        if (nativeToken.chain.feeUnit.equals("Gwei", ignoreCase = true)) {
            tokenValue = tokenValue.copy(
                value = tokenValue.value.divide(BigInteger.TEN.pow(9)),
                unit = "ETH"
            )
        }


        return Pair(
            fiatValueToStringMapper.map(fiatFees),
            convertTokenValueToString(tokenValue)
        )
    }
}