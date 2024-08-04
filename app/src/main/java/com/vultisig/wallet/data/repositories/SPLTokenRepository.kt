package com.vultisig.wallet.data.repositories

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Vault
import java.math.BigInteger
import javax.inject.Inject

private data class SPLTokenResponse(
    val mint: String,
    val amount: BigInteger,
)

internal interface SPLTokenRepository {
    suspend fun getTokens(address: String, vault: Vault): List<Coin>
    suspend fun getBalance(address: String, coin: Coin): BigInteger
}

internal class SPLTokenRepositoryImpl @Inject constructor(
    private val solanaApi: SolanaApi,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val gson: Gson,
) : SPLTokenRepository {

    override suspend fun getTokens(address: String, vault: Vault): List<Coin> {
        val rawSPLTokens = solanaApi.getSPLTokens(address)
        val splTokenResponse = gson
            .fromJson(
                rawSPLTokens, JsonArray::class.java
            ).map { processRawSPLToken(it) }
        val result = gson.fromJson(
            solanaApi.getSPLTokensInfo(splTokenResponse.map { it.mint }), JsonObject::class.java
        )
        val splTokens = splTokenResponse.map { key ->
            createCoin(result, key, vault)
        }
        return splTokens
    }

    override suspend fun getBalance(address: String, coin: Coin): BigInteger {
        return try {
            val splTokens = solanaApi.getSPLTokens(address)
            splTokens?.let {
                val responses = gson.fromJson(splTokens, JsonArray::class.java)
                    .map { processRawSPLToken(it) }
                responses.first { it.mint == coin.contractAddress }.amount
            } ?: BigInteger.ZERO
        } catch (e: Exception) {
            BigInteger.ZERO
        }
    }

    private suspend fun createCoin(
        result: JsonObject, key: SPLTokenResponse, vault: Vault
    ): Coin {
        val value = result.getAsJsonObject(key.mint)
        val ticker = value.getAsJsonObject("tokenMetadata")
            .getAsJsonObject("onChainInfo")
            .getAsJsonPrimitive("symbol").asString
        val logo = value.getAsJsonObject("tokenList")
            .getAsJsonPrimitive("image").asString
        val decimals = value.getAsJsonPrimitive("decimals").asInt
        val priceProviderId = value.getAsJsonObject("tokenList")
            .getAsJsonObject("extensions")
            .getAsJsonPrimitive("coingeckoId").asString

        val coin = Coin(
            chain = Chain.solana,
            ticker = ticker,
            logo = logo,
            decimal = decimals,
            priceProviderID = priceProviderId,
            contractAddress = key.mint,
            isNativeToken = false,
            address = "",
            hexPublicKey = ""
        )
        val (derivedAddress, derivedPublicKey) = chainAccountAddressRepository.getAddress(
            coin,
            vault
        )
        return coin.copy(address = derivedAddress, hexPublicKey = derivedPublicKey)
    }

    private fun processRawSPLToken(res: JsonElement): SPLTokenResponse {
        val info = res.asJsonObject.getAsJsonObject("account")
            .getAsJsonObject("data")
            .getAsJsonObject("parsed").getAsJsonObject("info")
        val mint = info.getAsJsonPrimitive("mint").asString
        val amount = info.getAsJsonObject("tokenAmount")
            .getAsJsonPrimitive("amount").asBigInteger
        return SPLTokenResponse(mint, amount)
    }
}
