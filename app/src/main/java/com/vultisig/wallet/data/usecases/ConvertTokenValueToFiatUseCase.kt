package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import javax.inject.Inject

internal interface ConvertTokenValueToFiatUseCase :
    suspend (Coin, TokenValue, AppCurrency) -> FiatValue

internal class ConvertTokenValueToFiatUseCaseImpl @Inject constructor(
    private val tokenPriceRepository: TokenPriceRepository,
) : ConvertTokenValueToFiatUseCase {

    override suspend fun invoke(
        token: Coin,
        tokenValue: TokenValue,
        appCurrency: AppCurrency,
    ): FiatValue {
        val priceDraft = tokenPriceRepository.getPrice(
            token,
            appCurrency,
        ).first()

        val price = if (priceDraft == BigDecimal.ZERO) {
            tokenPriceRepository.refresh(listOf(token))
            tokenPriceRepository.getPrice(
                token,
                appCurrency,
            ).first()
        } else {
            priceDraft
        }

        val decimal = if (tokenValue.unit == GWEI_UNIT) {
            tokenValue.decimal.divide(ONE_GWEI)
        } else {
            tokenValue.decimal
        }

        return FiatValue(
            value = decimal * price,
            currency = appCurrency.ticker,
        )
    }

    companion object {
        private val ONE_GWEI = BigDecimal.valueOf(1_000_000_000L)
        private const val GWEI_UNIT = "Gwei"
    }

}