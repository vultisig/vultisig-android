package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.models.OneInchTokenJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import javax.inject.Inject

interface OneInchToCoinsUseCase : suspend (Map<String, OneInchTokenJson>, Chain) -> List<Coin>

internal class  OneInchToCoinsUseCaseImpl @Inject constructor() : OneInchToCoinsUseCase {
    override suspend fun invoke(
        tokenResult: Map<String, OneInchTokenJson>,
        chain: Chain
    ): List<Coin>  =
        tokenResult.asSequence()
            .map { it.value }
            .map {
                val supportedCoin = Coins.coins.getOrDefault(chain, emptyList())
                    .firstOrNull { coin -> coin.id == "${it.symbol}-${chain.id}" }
                Coin(
                    contractAddress = it.address,
                    chain = chain,
                    ticker = it.symbol,
                    logo = it.logoURI ?: "",
                    decimal = it.decimals,
                    isNativeToken = supportedCoin?.isNativeToken == true,
                    priceProviderID = "",
                    address = "",
                    hexPublicKey = "",
                )
            }
            .toList()

}