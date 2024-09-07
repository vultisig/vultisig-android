package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject

internal interface TokenValueToDecimalUiStringMapper : MapperFunc<TokenValue, String>

internal class TokenValueToDecimalUiStringMapperImpl @Inject constructor() :
    TokenValueToDecimalUiStringMapper {

    private val decimalFormat = DecimalFormat(
        "#,###.${"#".repeat(MAX_UI_TOKEN_VALUE_DECIMALS)}",
        DecimalFormatSymbols(Locale.getDefault())
    )

    override fun invoke(from: TokenValue): String {
        try {
            val decimal = from.decimal
            val decimalValue = when {
                decimal >= ONE_BILLION -> {
                    String.format(
                        "%sB",
                        formatDecimal(decimal.divide(ONE_BILLION))
                    )
                }
                decimal >= ONE_MILLION -> {
                    String.format(
                        "%sM",
                        formatDecimal(decimal.divide(ONE_MILLION))
                    )
                }
                else -> formatDecimal(decimal)
            }
            return decimalValue
        } catch (e: Exception) {
            e.printStackTrace()
            return BigDecimal.ZERO.toString()
        }
    }


    companion object {
        private const val MAX_UI_TOKEN_VALUE_DECIMALS = 8
        private val ONE_BILLION = BigDecimal(1_000_000_000)
        private val ONE_MILLION = BigDecimal(1_000_000)
    }

    private fun formatDecimal(decimal: BigDecimal): String = decimalFormat.format(
        decimal
            .setScale(
                MAX_UI_TOKEN_VALUE_DECIMALS,
                RoundingMode.HALF_UP
            )
            .stripTrailingZeros()
    )
}