package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.models.OneInchTokenJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.decodeBytes32HexOrSelf
import javax.inject.Inject

interface OneInchToCoinsUseCase : suspend (Map<String, OneInchTokenJson>, Chain) -> List<Coin>

internal class OneInchToCoinsUseCaseImpl @Inject constructor() : OneInchToCoinsUseCase {
    override suspend fun invoke(
        tokenResult: Map<String, OneInchTokenJson>,
        chain: Chain,
    ): List<Coin> =
        tokenResult
            .asSequence()
            .map { it.value }
            .map {
                // Legacy tokens (e.g. MKR) declare bytes32 name()/symbol(); the aggregator
                // surfaces those as a 64-char hex string. Decode back to text so the swap
                // selector shows "MKR" instead of "4d4b52…" (issue #4873).
                val ticker = it.symbol.decodeBytes32HexOrSelf()
                val supportedCoin =
                    Coins.coins.getOrDefault(chain, emptyList()).firstOrNull { coin ->
                        coin.id == "$ticker-${chain.id}"
                    }
                Coin(
                    contractAddress = it.address,
                    chain = chain,
                    ticker = ticker,
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
