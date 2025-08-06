package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.TokenValue
import javax.inject.Inject

internal interface TokenValueToStringWithUnitMapper : MapperFunc<TokenValue, String>

internal class TokenValueToStringWithUnitMapperImpl @Inject constructor(
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
) : TokenValueToStringWithUnitMapper {

    override fun invoke(from: TokenValue): String =
        "${mapTokenValueToDecimalUiString(from)} ${from.unit}"
}