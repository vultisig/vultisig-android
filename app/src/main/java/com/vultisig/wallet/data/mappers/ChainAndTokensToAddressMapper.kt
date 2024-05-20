package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import javax.inject.Inject

internal data class ChainAndTokens(
    val chain: Chain,
    val tokens: List<Coin>,
)

internal interface ChainAndTokensToAddressMapper : Mapper<ChainAndTokens, Address>

internal class ChainAndTokensToAddressMapperImpl @Inject constructor() :
    ChainAndTokensToAddressMapper {

    override fun map(from: ChainAndTokens): Address =
        Address(
            chain = from.chain,
            address = from.tokens.first { it.isNativeToken }.address,
            accounts = from.tokens.map {
                Account(
                    token = it,
                    tokenValue = null,
                    fiatValue = null,
                )
            }
        )

}