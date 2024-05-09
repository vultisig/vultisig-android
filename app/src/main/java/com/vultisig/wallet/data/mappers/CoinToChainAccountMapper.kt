package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.ChainAccount
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.logo
import javax.inject.Inject

internal data class CoinWithFiatValue(
    val coin: Coin,
    val tokenValue: TokenValue?,
    val fiatValue: FiatValue?,
)

internal interface CoinToChainAccountMapper : Mapper<CoinWithFiatValue, ChainAccount>

internal class CoinToChainAccountMapperImpl @Inject constructor() : CoinToChainAccountMapper {

    override fun map(from: CoinWithFiatValue): ChainAccount =
        ChainAccount(
            chainName = from.coin.chain.raw,
            logo = from.coin.chain.logo,
            address = from.coin.address,
            nativeTokenAmount = from.tokenValue?.balance?.toString(),
            fiatValue = from.fiatValue,
        )

}