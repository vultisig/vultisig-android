package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.logo
import javax.inject.Inject

internal data class ChainAddressValue(
    val token: Coin,
    val chain: Chain,
    val address: String,
    val balance: TokenBalance?,
)

internal interface CoinToChainAccountMapper : Mapper<ChainAddressValue, Account>

internal class CoinToChainAccountMapperImpl @Inject constructor() : CoinToChainAccountMapper {

    override fun map(from: ChainAddressValue): Account =
        Account(
            token = from.token,
            chainName = from.chain.raw,
            logo = from.chain.logo,
            address = from.address,
            tokenAmount = from.balance?.tokenValue?.balance,
            fiatValue = from.balance?.fiatValue,
        )

}