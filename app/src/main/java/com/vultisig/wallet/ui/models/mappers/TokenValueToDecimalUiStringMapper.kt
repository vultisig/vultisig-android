package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject
import timber.log.Timber

internal const val UNLIMITED_TOKEN_AMOUNT = "Unlimited"

internal interface TokenValueToDecimalUiStringMapper : MapperFunc<TokenValue, String>

internal class TokenValueToDecimalUiStringMapperImpl @Inject constructor() :
    TokenValueToDecimalUiStringMapper {

    override fun invoke(from: TokenValue): String {
        if (from.value == MAX_UINT256) return UNLIMITED_TOKEN_AMOUNT
        try {
            val decimal = from.decimal
            val decimalValue =
                when {
                    decimal >= ONE_BILLION -> {
                        formatDecimal(decimal.divide(ONE_BILLION), 1) + "B"
                    }

                    decimal >= ONE_MILLION -> {
                        formatDecimal(decimal.divide(ONE_MILLION), 1) + "M"
                    }

                    else -> formatDecimal(decimal)
                }
            return decimalValue
        } catch (e: Exception) {
            Timber.tag("TokenValueToDecimalUiStringMapper").e(e)
            return BigDecimal.ZERO.toString()
        }
    }

    private fun formatDecimal(
        decimal: BigDecimal,
        decimalPoints: Int = MAX_UI_TOKEN_VALUE_DECIMALS,
    ): String {
        val decimalFormat =
            DecimalFormat(
                "#,###.${"#".repeat(decimalPoints)}",
                DecimalFormatSymbols(Locale.getDefault()),
            )
        return decimalFormat.format(
            decimal.setScale(MAX_UI_TOKEN_VALUE_DECIMALS, RoundingMode.DOWN).stripTrailingZeros()
        )
    }

    companion object {
        private const val MAX_UI_TOKEN_VALUE_DECIMALS = 8
        private val ONE_BILLION = BigDecimal(1_000_000_000)
        private val ONE_MILLION = BigDecimal(1_000_000)
        private val MAX_UINT256 =
            BigInteger(
                "115792089237316195423570985008687907853269984665640564039457584007913129639935"
            )
    }
}
