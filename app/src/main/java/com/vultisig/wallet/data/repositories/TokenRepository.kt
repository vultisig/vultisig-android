package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.OneInchApi
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal interface TokenRepository {

    suspend fun getToken(tokenId: String): Coin?

    fun getChainTokens(chain: Chain): Flow<List<Coin>>

    suspend fun getNativeToken(chainId: String): Coin

    val allTokens: Flow<List<Coin>>

    val nativeTokens: Flow<List<Coin>>

}

internal class TokenRepositoryImpl @Inject constructor(
    private val oneInchApi: OneInchApi,
) : TokenRepository {

    override suspend fun getToken(tokenId: String): Coin? =
        allTokens.map { allTokens -> allTokens.firstOrNull { it.id == tokenId } }.firstOrNull()

    override fun getChainTokens(chain: Chain): Flow<List<Coin>> =
        if (chain.standard == TokenStandard.EVM) {
            flow {
                val tokens = oneInchApi.getTokens(chain)
                val allTokens = allTokens.first().filter { it.chain == chain }
                emit(
                    allTokens +
                            tokens.tokens.asSequence()
                                .map { it.value }
                                .map {
                                    Coin(
                                        contractAddress = it.address,
                                        chain = chain,
                                        ticker = it.symbol,
                                        logo = it.logoURI ?: "",
                                        decimal = it.decimals,
                                        isNativeToken = false,
                                        priceProviderID = "",
                                        address = "",
                                        hexPublicKey = "",
                                    )
                                }
                                .filter { newCoin -> allTokens.none { it.chain == newCoin.chain && it.ticker == newCoin.ticker } }
                                .toList()
                )
            }
        } else {
            allTokens.map { allTokens ->
                allTokens.filter { it.chain.id == chain.id }
            }
        }

    override suspend fun getNativeToken(chainId: String): Coin =
        nativeTokens.map { it.first { it.chain.id == chainId } }.first()

    override val allTokens: Flow<List<Coin>> = flowOf(Coins.SupportedCoins)

    override val nativeTokens: Flow<List<Coin>> = allTokens
        .map { it.filterNatives() }

    private fun Iterable<Coin>.filterNatives() =
        filter { it.isNativeToken }

}
