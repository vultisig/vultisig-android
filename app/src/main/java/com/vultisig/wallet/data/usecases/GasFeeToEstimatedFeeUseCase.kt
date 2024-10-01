package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import kotlinx.coroutines.flow.first
import java.math.RoundingMode
import javax.inject.Inject

internal interface GasFeeToEstimatedFeeUseCase :
    suspend (GasFeeParams) -> Pair<String, String>

internal class GasFeeToEstimatedFeeUseCaseImpl @Inject constructor(
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val tokenRepository: TokenRepository,

    ) : GasFeeToEstimatedFeeUseCase {

    override suspend fun invoke(from: GasFeeParams): Pair<String, String> {
        val appCurrency = appCurrencyRepository.currency.first()

        val tokenValue = TokenValue(
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

        return Pair(
            fiatValueToStringMapper.map(fiatFees),
            formatGasValue("${tokenValue.decimal} ${tokenValue.unit}")           )
    }

}

private fun formatGasValue(gasValue: String?): String {
    return gasValue?.let {
        val parts = it.split(" ")
        if (parts.size == 2) {
            val value = parts[0].toBigDecimalOrNull()?.setScale(
                4,
                RoundingMode.HALF_UP
            )
            if (value != null) {
                return "$value ${parts[1]}"
            }
        }
        it
    } ?: ""
}