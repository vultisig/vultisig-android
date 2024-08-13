package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.TokenValue
import java.math.RoundingMode
import javax.inject.Inject

internal interface TokenValueToDecimalUiStringMapper : MapperFunc<TokenValue, String>

internal class TokenValueToDecimalUiStringMapperImpl @Inject constructor() :
    TokenValueToDecimalUiStringMapper {

    override fun invoke(from: TokenValue): String {
        return from.decimal
            .setScale(MAX_UI_TOKEN_VALUE_DECIMALS, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    companion object {
        private const val MAX_UI_TOKEN_VALUE_DECIMALS = 8
    }

}