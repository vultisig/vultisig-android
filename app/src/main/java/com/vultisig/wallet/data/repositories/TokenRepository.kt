package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.EvmApiFactory
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
import java.math.BigInteger
import javax.inject.Inject

internal interface TokenRepository {

    suspend fun getToken(tokenId: String): Coin?

    fun getChainTokens(chain: Chain): Flow<List<Coin>>

    suspend fun getNativeToken(chainId: String): Coin

    suspend fun getTokenByContract(chainId: String, contractAddress: String): Coin?

    val allTokens: Flow<List<Coin>>

    val nativeTokens: Flow<List<Coin>>

}

internal class TokenRepositoryImpl @Inject constructor(
    private val oneInchApi: OneInchApi,
    private val evmApiFactory: EvmApiFactory,
    private val customTokenRepository: CustomTokenRepository,
) : TokenRepository {
    override suspend fun getToken(tokenId: String): Coin? =
        allTokens.map { allTokens -> allTokens.firstOrNull { it.id == tokenId } }.firstOrNull()

    override fun getChainTokens(chain: Chain): Flow<List<Coin>> =
        if (chain.standard == TokenStandard.EVM) {
            flow {
                val tokens = oneInchApi.getTokens(chain)
                val allTokens = allTokens.first().filter { it.chain == chain }
                val customTokens = customTokenRepository.getAll(chain.id).first().toMutableList()
                val oneInchTokens = tokens.tokens.asSequence()
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

                customTokens.forEach {
                    if (oneInchTokens.map { oneInchToken -> oneInchToken.id }.contains(it.id) ||
                        allTokens.map { token -> token.id }.contains(it.id)
                    ) {
                        customTokenRepository.remove(it.id)
                        customTokens.remove(it)
                    }
                }
                emit(
                    allTokens +
                            customTokens +
                            oneInchTokens
                )
            }
        } else {
            allTokens.map { allTokens ->
                allTokens.filter { it.chain.id == chain.id }
            }
        }

    override suspend fun getNativeToken(chainId: String): Coin =
        nativeTokens.map { it.first { it.chain.id == chainId } }.first()

    override suspend fun getTokenByContract(chainId: String, contractAddress: String): Coin? {
        val chain = Chain.fromRaw(chainId)
        val rpcResponses = evmApiFactory.createEvmApi(chain)
            .findCustomToken(contractAddress)
        if (rpcResponses.isEmpty())
            return null
        var ticker = ""
        var decimal = 0
        rpcResponses.forEach {
            if (it.result == null) {
                return null
            }
            if (it.id == CUSTOM_TOKEN_RESPONSE_TICKER_ID)
                ticker = it.result.decodeContractString() ?: return null
            else decimal =
                it.result.decodeContractDecimal().takeIf { dec -> dec != 0 } ?: return null
        }
        val coin = Coin(
            chain = chain,
            ticker = ticker.uppercase(),
            logo = "https://tokens-data.1inch.io/images/$contractAddress.png",
            address = "",
            decimal = decimal,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = false,
        )
        return coin
    }

    override val allTokens: Flow<List<Coin>> = flowOf(Coins.SupportedCoins)

    override val nativeTokens: Flow<List<Coin>> = allTokens
        .map { it.filterNatives() }

    private fun Iterable<Coin>.filterNatives() =
        filter { it.isNativeToken }

    private fun String.decodeContractString(): String? {
        try {
            val bytes =
                removePrefix("0x")
                    .chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray()
            val length = BigInteger(bytes.sliceArray(32..63)).toInt()
            return String(bytes.sliceArray(64 until 64 + length)).lowercase()
        } catch (e: Exception) {
            return null
        }
    }

    private fun String.decodeContractDecimal(): Int {
        return BigInteger(
            removePrefix("0x"),
            16
        ).toInt()
    }

    companion object {
        private const val CUSTOM_TOKEN_RESPONSE_TICKER_ID = 2
    }

}
