package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
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

private const val ONE_GWEI = 1_000_000_000L

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
        var decimal = tokenValue.decimal
        if (token.chain.feeUnit.equals("Gwei")) {
            decimal = decimal.divide(BigDecimal.valueOf(ONE_GWEI))
        }


        return FiatValue(
            value = decimal * price,
            currency = appCurrency.ticker,
        )
    }

}