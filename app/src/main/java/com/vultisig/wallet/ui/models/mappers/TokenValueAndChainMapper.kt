package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenValue
import javax.inject.Inject

internal interface TokenValueAndChainMapper :
    MapperFunc<Pair<TokenValue, Chain>, String>


internal class TokenValueAndChainImp @Inject constructor(
    private val mapTokenValueToStringWithUnit : TokenValueToStringWithUnitMapper,
) :
TokenValueAndChainMapper {

    override fun invoke(from: Pair<TokenValue, Chain>): String {
        val (tokenValue, chain) = from
        return "${mapTokenValueToStringWithUnit(tokenValue)} (${chain.name})"
    }
}
