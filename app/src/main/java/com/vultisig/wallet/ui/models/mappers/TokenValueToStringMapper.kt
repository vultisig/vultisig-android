package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.TokenValue
import javax.inject.Inject

internal interface TokenValueToStringMapper : MapperFunc<TokenValue, String>

internal class TokenValueToStringMapperImpl @Inject constructor() : TokenValueToStringMapper {

    override fun invoke(from: TokenValue): String =
        "${from.decimal.toPlainString()} ${from.unit}"

}