package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

internal interface TokenValueToDecimalUiStringMapper : MapperFunc<TokenValue, String>

internal class TokenValueToDecimalUiStringMapperImpl @Inject constructor() :
    TokenValueToDecimalUiStringMapper {

    private val numberFormat = NumberFormat.getInstance(Locale.getDefault())

    override fun invoke(from: TokenValue): String {
        val decimal = from.decimal
            .setScale(MAX_UI_TOKEN_VALUE_DECIMALS, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        // We need to handle values below 0.001 without using NumberFormat because it rounds them to 0
        return if (decimal < BigDecimal.valueOf(0.001))
            decimal.toPlainString()
        else numberFormat.format(decimal)
    }

    companion object {
        private const val MAX_UI_TOKEN_VALUE_DECIMALS = 8
    }

}