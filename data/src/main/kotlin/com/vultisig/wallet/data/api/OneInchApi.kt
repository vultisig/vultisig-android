package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.api.models.OneInchTokenJson
import com.vultisig.wallet.data.api.models.OneInchTokensJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.oneInchChainId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.math.BigInteger
import javax.inject.Inject

interface OneInchApi {

    suspend fun getSwapQuote(
        chain: Chain,
        srcTokenContractAddress: String,
        dstTokenContractAddress: String,
        srcAddress: String,
        amount: String,
        isAffiliate: Boolean,
    ): OneInchSwapQuoteJson

    suspend fun getTokens(
        chain: Chain,
    ): OneInchTokensJson

    suspend fun getContractsWithBalance(
        chain: Chain,
        address: String,
    ): List<String>

    suspend fun getTokensByContracts(
        chain: Chain,
        contractAddresses: List<String>,
    ): Map<String, OneInchTokenJson>

}

class OneInchApiImpl @Inject constructor(
    private val httpClient: HttpClient,
) : OneInchApi {

    override suspend fun getSwapQuote(
        chain: Chain,
        srcTokenContractAddress: String,
        dstTokenContractAddress: String,
        srcAddress: String,
        amount: String,
        isAffiliate: Boolean,
    ): OneInchSwapQuoteJson =
        httpClient.get("https://api.vultisig.com/1inch/swap/v6.0/${chain.oneInchChainId()}/swap") {
            parameter(
                "src",
                srcTokenContractAddress.takeIf { it.isNotEmpty() } ?: ONEINCH_NULL_ADDRESS)
            parameter(
                "dst",
                dstTokenContractAddress.takeIf { it.isNotEmpty() } ?: ONEINCH_NULL_ADDRESS)
            parameter("amount", amount)
            parameter("from", srcAddress)
            parameter("slippage", "0.5")
            parameter("disableEstimate", true)
            parameter("includeGas", true)
            if (isAffiliate) {
                parameter("referrer", ONEINCH_REFERRER_ADDRESS)
                parameter("fee", ONEINCH_REFERRER_FEE)
            }
        }.body()


    override suspend fun getTokens(
        chain: Chain,
    ): OneInchTokensJson =
        httpClient.get("https://api.vultisig.com/1inch/swap/v6.0/${chain.oneInchChainId()}/tokens")
            .body()

    override suspend fun getContractsWithBalance(chain: Chain, address: String): List<String> {
        val response =
            httpClient.get("https://api.vultisig.com/1inch/balance/v1.2/${chain.oneInchChainId()}/balances/$address")
        return response.body<Map<String, String>>().mapNotNull { (key, value) ->
            if (value.toBigInteger() > BigInteger.ZERO) key else null
        }
    }

    override suspend fun getTokensByContracts(
        chain: Chain,
        contractAddresses: List<String>,
    ): Map<String, OneInchTokenJson> = httpClient.get(
        "https://api.vultisig.com/1inch/token/v1.2/${chain.oneInchChainId()}/custom"
    ) {
        parameter("addresses", contractAddresses.joinToString(","))
    }.body()

    companion object {
        private const val ONEINCH_REFERRER_ADDRESS = "0xa4a4f610e89488eb4ecc6c63069f241a54485269"
        private const val ONEINCH_REFERRER_FEE = 0.5
        private const val ONEINCH_NULL_ADDRESS = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    }
}