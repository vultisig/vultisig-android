package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.TokenValue
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

    override fun invoke(from: TokenValue): String = decimalFormat.format(
        from.decimal
            .setScale(MAX_UI_TOKEN_VALUE_DECIMALS, RoundingMode.HALF_UP)
            .stripTrailingZeros()
    )

    companion object {
        private const val MAX_UI_TOKEN_VALUE_DECIMALS = 8
    }

}