package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

interface ConvertTokenToToken {
    suspend fun convertTokenToToken(
        fromAmount: BigInteger,
        coinAndFiatValue: CoinAndFiatValue,
        toToken: Coin,
    ): BigInteger
}

internal class ConvertTokenToTokenImpl @Inject constructor(
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
): ConvertTokenToToken {
    override suspend fun convertTokenToToken(
        fromAmount: BigInteger,
        coinAndFiatValue: CoinAndFiatValue,
        toToken: Coin,
    ): BigInteger {
        val fromToken = coinAndFiatValue.coin
        if (fromToken.id == toToken.id) {
            return fromAmount
        }
        
        if (fromAmount == BigInteger.ZERO) {
            return BigInteger.ZERO
        }
        
        val appCurrency = appCurrencyRepository.currency.first()
        val fromAmountDecimal = fromToken.toDecimalValue(fromAmount)
        val fromPrice = coinAndFiatValue.fiatValue.value
        
        val toPrice = tokenPriceRepository.getPrice(toToken, appCurrency).first()
        
        if (fromPrice == BigDecimal.ZERO || toPrice == BigDecimal.ZERO) {
            return BigInteger.ZERO
        }
        
        // Calculate value in currency (USD, EUR, etc.)
        val valueInCurrency = fromAmountDecimal.multiply(fromPrice)
        
        // Convert to target token amount
        val toAmountDecimal = valueInCurrency
            .divide(toPrice, CALCULATION_SCALE, RoundingMode.HALF_UP)
        
        // Convert back to raw amount with target token decimals
        return toToken.toRawAmount(toAmountDecimal)
    }
    
    companion object {
        private const val CALCULATION_SCALE = 18
    }
}

private fun Coin.toDecimalValue(rawAmount: BigInteger): BigDecimal {
    return rawAmount.toBigDecimal().movePointLeft(this.decimal)
}

private fun Coin.toRawAmount(decimalValue: BigDecimal): BigInteger {
    return decimalValue.movePointRight(this.decimal).toBigInteger()
}