package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.ChainAccount
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.getBalance
import com.vultisig.wallet.models.getBalanceInFiatString
import com.vultisig.wallet.models.logo
import javax.inject.Inject

internal interface CoinToChainAccountMapper : Mapper<Coin, ChainAccount>

internal class CoinToChainAccountMapperImpl @Inject constructor() : CoinToChainAccountMapper {

    override fun map(from: Coin): ChainAccount =
        ChainAccount(
            chainName = from.chain.raw,
            logo = from.chain.logo,
            address = from.address,
            nativeTokenAmount = from.getBalance().toString(),
            fiatAmount = from.getBalanceInFiatString(),
        )

}