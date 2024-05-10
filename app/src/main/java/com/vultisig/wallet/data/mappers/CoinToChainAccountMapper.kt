package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.ChainAccount
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.logo
import javax.inject.Inject

internal data class ChainAddressValue(
    val chain: Chain,
    val address: String,
    val tokenValue: TokenValue?,
    val fiatValue: FiatValue?,
)

internal interface CoinToChainAccountMapper : Mapper<ChainAddressValue, ChainAccount>

internal class CoinToChainAccountMapperImpl @Inject constructor() : CoinToChainAccountMapper {

    override fun map(from: ChainAddressValue): ChainAccount =
        ChainAccount(
            chainName = from.chain.raw,
            logo = from.chain.logo,
            address = from.address,
            nativeTokenAmount = from.tokenValue?.balance?.toString(),
            fiatValue = from.fiatValue,
        )

}