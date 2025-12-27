package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import javax.inject.Inject

internal interface ConvertBpsToFiatUseCase {
    suspend operator fun invoke(
        token: Coin,
        tokenValue: TokenValue,
        bps: Int,
    ): FiatValue
}

internal class ConvertBpsToFiatUseCaseImpl @Inject constructor(
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val appCurrencyRepository: AppCurrencyRepository,
) : ConvertBpsToFiatUseCase {

    override suspend fun invoke(
        token: Coin,
        tokenValue: TokenValue,
        bps: Int,
    ): FiatValue {

        val currency = appCurrencyRepository.currency.first()
        if (bps <= 0) {
            return FiatValue(
                value = BigDecimal.ZERO,
                currency = currency.ticker,
            )
        }

        // 1 bps = 0.01%, 100 bps = 1% → denominator = 10_000
        val denominator = BigDecimal(10_000)
        val factor = BigDecimal(bps).divide(denominator)

        val baseFiat = convertTokenValueToFiat(
            token,
            tokenValue,
            currency,
        )

        return FiatValue(
            value = baseFiat.value.multiply(factor),
            currency = baseFiat.currency,
        )
    }
}


