package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal interface TokenRepository {

    fun getToken(tokenId: String): Flow<Coin>

    fun getChainTokens(chainId: String): Flow<List<Coin>>

    fun getNativeToken(chainId: String): Flow<Coin>

    val allTokens: Flow<List<Coin>>

    val nativeTokens: Flow<List<Coin>>

}

internal class TokenRepositoryImpl @Inject constructor() : TokenRepository {

    override fun getToken(tokenId: String): Flow<Coin> =
        allTokens.map { allTokens -> allTokens.first { it.id == tokenId } }

    override fun getChainTokens(chainId: String): Flow<List<Coin>> = allTokens
        .map { allTokens ->
            allTokens.filter { it.chain.id == chainId }
        }

    override fun getNativeToken(chainId: String): Flow<Coin> =
        nativeTokens.map { it.first { it.chain.id == chainId } }

    override val allTokens: Flow<List<Coin>> = flowOf(Coins.SupportedCoins)

    override val nativeTokens: Flow<List<Coin>> = allTokens
        .map { it.filterNatives() }

    private fun Iterable<Coin>.filterNatives() =
        filter { it.isNativeToken }

}