package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.models.SplResponseAccountJson
import com.vultisig.wallet.data.api.models.SplTokenJson
import com.vultisig.wallet.data.api.models.SplTokenListJson
import com.vultisig.wallet.data.db.dao.TokenValueDao
import com.vultisig.wallet.data.db.models.TokenValueEntity
import com.vultisig.wallet.data.mappers.KeysignMessageFromProtoMapper
import com.vultisig.wallet.data.mappers.SplTokenJsonFromSplTokenInfoMapper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

private data class SplTokenResponse(
    val mint: String,
    val amount: BigInteger,
)

interface SplTokenRepository {
    suspend fun getTokens(address: String, vault: Vault): List<Coin>
    suspend fun getCachedBalance(coin: Coin): BigInteger
    suspend fun getBalance(coin: Coin): BigInteger?
}

internal class SplTokenRepositoryImpl @Inject constructor(
    private val solanaApi: SolanaApi,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tokenValueDao: TokenValueDao,
    private val mapSplTokenJsonFromSplTokenInfo: SplTokenJsonFromSplTokenInfoMapper,
    ) : SplTokenRepository {

    override suspend fun getTokens(address: String, vault: Vault): List<Coin> {
        val rawSPLTokens = solanaApi.getSPLTokens(address) ?: return emptyList()
        val splTokenResponse = rawSPLTokens.map { processRawSPLToken(it) }
        var result = solanaApi.getSPLTokensInfo(splTokenResponse.map { it.mint })
        if (result.size != splTokenResponse.size) {
            //search for missing tokens in splTokenResponse
            val missingMints = splTokenResponse.map { it.mint }.filter { mint ->
                result.none { it.mint == mint }
            }
            val mutableResult = result.toMutableList()
            solanaApi.getSPLTokensInfo2(missingMints).forEach {
                mutableResult.add(mapSplTokenJsonFromSplTokenInfo(it))
            }
            result = mutableResult
        }
        return splTokenResponse.mapNotNull { key ->
            result.firstOrNull { resultItem -> resultItem.mint == key.mint }
                ?.let { matchingResult ->
                    createCoin(
                        matchingResult,
                        key.mint,
                        vault
                    ).apply {
                        saveToDatabase(
                            this,
                            key
                        )
                    }
                }
        }

    }
    private suspend fun saveToDatabase(
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

    override suspend fun getCachedBalance(coin: Coin): BigInteger {
        return try {
            tokenValueDao.getTokenValue(
                Chain.Solana.id,
                coin.address,
                coin.ticker,
            )!!.toBigInteger()
        } catch (e: Exception) {
            Timber.e("get spl balance error", e)
            BigInteger.ZERO
        }
    }

    private suspend fun createCoin(
        tokenResponse: SplTokenJson,
        contractAddress: String,
        vault: Vault
    ): Coin {
        val coin = Coin(
            chain = Chain.Solana,
            ticker = tokenResponse.tokenList.ticker,
            logo = tokenResponse.tokenList.logo?: "",
            decimal = tokenResponse.decimals,
            priceProviderID = tokenResponse.tokenList.extensions?.coingeckoId ?: "0",
            contractAddress = contractAddress,
            isNativeToken = false,
            address = "",
            hexPublicKey = ""
        )
        val (derivedAddress, derivedPublicKey) = chainAccountAddressRepository
            .getAddress(coin, vault)
        return coin.copy(address = derivedAddress, hexPublicKey = derivedPublicKey)
    }

    private fun processRawSPLToken(response: SplResponseAccountJson): SplTokenResponse {
        val amount = response.account.data.parsed.info.tokenAmount.amount.toBigInteger()
        val mint = response.account.data.parsed.info.mint
        return SplTokenResponse(mint, amount)
    }
}
