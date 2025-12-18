package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.TokenValue
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject

internal interface TokenValueToDecimalUiStringMapper : MapperFunc<TokenValue, String>

internal class TokenValueToDecimalUiStringMapperImpl @Inject constructor() :
    TokenValueToDecimalUiStringMapper {

    override fun invoke(from: TokenValue): String {
        try {
            val decimal = from.decimal
            val decimalValue = when {
                decimal >= ONE_BILLION -> {
                    formatDecimal(
                        decimal.divide(ONE_BILLION),
                        1
                    ) + "B"
                }

                decimal >= ONE_MILLION -> {
                    formatDecimal(
                        decimal.divide(ONE_MILLION),
                        1
                    ) + "M"
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
        decimal: BigDecimal, decimalPoints: Int = MAX_UI_TOKEN_VALUE_DECIMALS,
    ): String {
        val decimalFormat = DecimalFormat(
            "#,###.${"#".repeat(decimalPoints)}",
            DecimalFormatSymbols(Locale.getDefault())
        )
        return decimalFormat.format(
            decimal
                .setScale(
                    MAX_UI_TOKEN_VALUE_DECIMALS,
                    RoundingMode.HALF_UP
                )
                .stripTrailingZeros()
        )
    }

    companion object {
        private const val MAX_UI_TOKEN_VALUE_DECIMALS = 8
        private val ONE_BILLION = BigDecimal(1_000_000_000)
        private val ONE_MILLION = BigDecimal(1_000_000)
    }
}