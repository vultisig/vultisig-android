package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.quotes.OneInchSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.OneInchTokenJson
import com.vultisig.wallet.data.api.models.OneInchTokensJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapQuoteErrorResponse
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.oneInchChainId
import com.vultisig.wallet.data.utils.OneInchSwapQuoteResponseJsonSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
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
    ): OneInchSwapQuoteDeserialized

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
    private val oneInchSwapQuoteResponseJsonSerializer: OneInchSwapQuoteResponseJsonSerializer,
    private val json: Json,
) : OneInchApi {

    override suspend fun getSwapQuote(
        chain: Chain,
        srcTokenContractAddress: String,
        dstTokenContractAddress: String,
        srcAddress: String,
        amount: String,
        isAffiliate: Boolean,
    ): OneInchSwapQuoteDeserialized = coroutineScope {
        try {
            val baseSwapQuoteUrl = "https://api.vultisig.com/1inch/swap/v6.1/${chain.oneInchChainId()}"
            val requestParams: HttpRequestBuilder.() -> Unit = {
                createQuoteParams(
                    srcTokenContractAddress,
                    dstTokenContractAddress,
                    amount,
                    srcAddress,
                    isAffiliate
                )
            }
            val swapResponseAsync = async {
                httpClient.get("$baseSwapQuoteUrl/swap", block = requestParams)
            }
            val quoteResponseAsync = async {
                httpClient.get("$baseSwapQuoteUrl/quote", block = requestParams)
            }

            val (swapResponse, quoteResponse) =
                listOf(swapResponseAsync, quoteResponseAsync)
                    .awaitAll()
                    .also { responses ->
                        responses.forEach { response ->
                            if (!response.status.isSuccess()) {
                                val resp = response.body<OneInchSwapQuoteErrorResponse>()
                                return@coroutineScope OneInchSwapQuoteDeserialized.Error(
                                    error = resp.description
                                )
                            }
                        }
                    }

            json.decodeFromJsonElement(
                oneInchSwapQuoteResponseJsonSerializer,
                buildJsonObject {
                    put("swap", swapResponse.body())
                    put("quote", quoteResponse.body())
                }
            )
        } catch (e: Exception) {
            OneInchSwapQuoteDeserialized.Error(error = e.message ?: "Unknown error")
        }
    }

    private fun HttpRequestBuilder.createQuoteParams(
        srcTokenContractAddress: String,
        dstTokenContractAddress: String,
        amount: String,
        srcAddress: String,
        isAffiliate: Boolean,
    ) {
        parameter(
            "src",
            srcTokenContractAddress.takeIf { it.isNotEmpty() } ?: ONEINCH_NULL_ADDRESS)
        parameter(
            "dst",
            dstTokenContractAddress.takeIf { it.isNotEmpty() } ?: ONEINCH_NULL_ADDRESS)
        parameter("amount", amount)
        parameter("from", srcAddress)
        parameter("slippage", "0.5")
        parameter("disableEstimate", false)
        parameter("includeGas", true)
        parameter("referrer", ONEINCH_REFERRER_ADDRESS)
        parameter("fee", if(isAffiliate) ONEINCH_REFERRER_FEE else "0")
    }


    override suspend fun getTokens(
        chain: Chain,
    ): OneInchTokensJson =
        httpClient.get("https://api.vultisig.com/1inch/swap/v6.1/${chain.oneInchChainId()}/tokens")
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