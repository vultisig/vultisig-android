package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.models.JupiterTokenResponseJson
import com.vultisig.wallet.data.api.models.SplTokenJson
import com.vultisig.wallet.data.db.dao.TokenValueDao
import com.vultisig.wallet.data.db.models.TokenValueEntity
import com.vultisig.wallet.data.mappers.SplResponseAccountJsonMapper
import com.vultisig.wallet.data.mappers.SplTokenJsonFromSplTokenInfoMapper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

internal data class SplTokenResponse(
    val mint: String,
    val amount: BigInteger,
)

interface SplTokenRepository {
    suspend fun getTokens(address: String, vault: Vault): List<Coin>
    suspend fun getCachedBalance(coin: Coin): BigInteger
    suspend fun getBalance(coin: Coin): BigInteger?
    suspend fun getTokenByContract(contractAddress: String): Coin?
    suspend fun getTokens(address: String): List<Coin>
    suspend fun getJupiterTokens(): List<Coin>
}

internal class SplTokenRepositoryImpl @Inject constructor(
    private val solanaApi: SolanaApi,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tokenValueDao: TokenValueDao,
    private val mapSplTokenJsonFromSplTokenInfo: SplTokenJsonFromSplTokenInfoMapper,
    private val mapSplAccountJsonToSplToken: SplResponseAccountJsonMapper,
) : SplTokenRepository {

    override suspend fun getTokens(address: String, vault: Vault) =
        fetchTokens(address)
            .filterNotNull()
            .map { (key, coin) ->
                createCoin(coin, vault).apply {
                    saveTokenValueToDatabase(this, key)
                }
            }

    override suspend fun getTokens(address: String) = fetchTokens(address)
        .filterNotNull()
        .map { it.second }

    private suspend fun fetchTokens(address: String): List<Pair<SplTokenResponse, Coin>?> {
        val rawSPLTokens = solanaApi.getSPLTokens(address) ?: return emptyList()
        val splTokenResponse = rawSPLTokens.map(mapSplAccountJsonToSplToken)
        val result = getSplTokensByContractAddress(splTokenResponse.map { it.mint })
        return splTokenResponse.map { key ->
            result.firstOrNull { resultItem -> resultItem.mint == key.mint }
                ?.let { matchingResult ->
                    key to initCoinData(matchingResult, key.mint)
                }
        }
    }

    private suspend fun getSplTokensByContractAddress(contractAddresses: List<String>): List<SplTokenJson> {
        var result = solanaApi.getSPLTokensInfo(contractAddresses)
        if (result.size != contractAddresses.size) {
            //search for missing tokens in splTokenResponse
            val missingMints = contractAddresses.filter { mint ->
                result.none { it.mint == mint }
            }
            val mutableResult = result.toMutableList()
            solanaApi.getSPLTokensInfo2(missingMints).forEach {
                mutableResult.add(mapSplTokenJsonFromSplTokenInfo(it))
            }
            result = mutableResult
        }
        return result
    }

    private suspend fun saveTokenValueToDatabase(
        coin: Coin,
        splTokenData: SplTokenResponse,
    ) {
        tokenValueDao.insertTokenValue(
            TokenValueEntity(
                Chain.Solana.id,
                coin.address,
                coin.ticker,
                splTokenData.amount.toString()
            )
        )
    }

    override suspend fun getBalance(coin: Coin): BigInteger? {
        return solanaApi.getSPLTokenBalance(
            coin.address,
            coin.contractAddress
        )?.toBigInteger()
    }

    override suspend fun getTokenByContract(contractAddress: String) =
        getSplTokensByContractAddress(listOf(contractAddress))
            .firstOrNull()
            ?.run { initCoinData(this, contractAddress) }

    override suspend fun getJupiterTokens(): List<Coin> = try {
        solanaApi.getJupiterTokens().toCoins()
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun getCachedBalance(coin: Coin): BigInteger {
        return try {
            tokenValueDao.getTokenValue(
                Chain.Solana.id,
                coin.address,
                coin.ticker,
            )!!.toBigInteger()
        } catch (e: Exception) {
            Timber.e(e,"get spl balance error")
            BigInteger.ZERO
        }
    }

    private suspend fun createCoin(
        initialCoin: Coin,
        vault: Vault
    ): Coin {
        val (derivedAddress, derivedPublicKey) = chainAccountAddressRepository
            .getAddress(initialCoin, vault)
        return initialCoin.copy(address = derivedAddress, hexPublicKey = derivedPublicKey)
    }

    private fun initCoinData(
        tokenResponse: SplTokenJson,
        contractAddress: String
    ) = Coin(
        chain = Chain.Solana,
        ticker = tokenResponse.tokenList.ticker,
        logo = tokenResponse.tokenList.logo ?: "",
        decimal = tokenResponse.decimals,
        priceProviderID = tokenResponse.tokenList.extensions?.coingeckoId
            ?: tokenResponse.usdPrice?.toString() ?: "",
        contractAddress = contractAddress,
        isNativeToken = false,
        address = "",
        hexPublicKey = ""
    )

    private fun List<JupiterTokenResponseJson>.toCoins() =
        asSequence()
            .map {
                Coin(
                    contractAddress = it.contractAddress,
                    chain = Chain.Solana,
                    ticker = it.ticker,
                    address = "",
                    logo = it.logo ?: "",
                    decimal = it.decimals,
                    hexPublicKey = "",
                    priceProviderID = it.extensions?.coingeckoId ?: "",
                    isNativeToken = false,
                )
            }
            .toList()
}
