package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.models.OneInchTokenJson
import com.vultisig.wallet.data.api.models.OneInchTokensJson
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapQuoteErrorResponse
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.oneInchChainId
import com.vultisig.wallet.data.utils.OneInchSwapQuoteResponseJsonSerializer
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.math.BigInteger
import javax.inject.Inject
import kotlin.math.round
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject

interface OneInchApi {

    suspend fun getSwapQuote(
        chain: Chain,
        srcTokenContractAddress: String,
        dstTokenContractAddress: String,
        srcAddress: String,
        amount: String,
        isAffiliate: Boolean,
        bpsDiscount: Int,
        slippageBps: Int? = null,
    ): EVMSwapQuoteDeserialized

    suspend fun getTokens(chain: Chain): OneInchTokensJson

    suspend fun getContractsWithBalance(chain: Chain, address: String): List<String>

    suspend fun getTokensByContracts(
        chain: Chain,
        contractAddresses: List<String>,
    ): Map<String, OneInchTokenJson>
}

class OneInchApiImpl
@Inject
constructor(
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
        bpsDiscount: Int,
        slippageBps: Int?,
    ): EVMSwapQuoteDeserialized = coroutineScope {
        try {
            val baseSwapQuoteUrl =
                "https://api.vultisig.com/1inch/swap/v6.1/${chain.oneInchChainId()}"
            val requestParams: HttpRequestBuilder.() -> Unit = {
                createQuoteParams(
                    srcTokenContractAddress,
                    dstTokenContractAddress,
                    amount,
                    srcAddress,
                    isAffiliate,
                    bpsDiscount,
                    slippageBps,
                )
            }
            val swapResponseAsync = async {
                httpClient.get("$baseSwapQuoteUrl/swap", block = requestParams)
            }
            val quoteResponseAsync = async {
                httpClient.get("$baseSwapQuoteUrl/quote", block = requestParams)
            }

            val (swapResponse, quoteResponse) =
                listOf(swapResponseAsync, quoteResponseAsync).awaitAll().also { responses ->
                    responses.forEach { response ->
                        if (!response.status.isSuccess()) {
                            val resp =
                                json.decodeFromString<OneInchSwapQuoteErrorResponse>(
                                    response.bodyAsText()
                                )
                            return@coroutineScope EVMSwapQuoteDeserialized.Error(
                                error = resp.description
                            )
                        }
                    }
                }

            json.decodeFromJsonElement(
                oneInchSwapQuoteResponseJsonSerializer,
                buildJsonObject {
                    put("swap", swapResponse.bodyOrThrow<JsonElement>())
                    put("quote", quoteResponse.bodyOrThrow<JsonElement>())
                },
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            EVMSwapQuoteDeserialized.Error(error = e.message ?: "Unknown error")
        }
    }

    private fun HttpRequestBuilder.createQuoteParams(
        srcTokenContractAddress: String,
        dstTokenContractAddress: String,
        amount: String,
        srcAddress: String,
        isAffiliate: Boolean,
        bpsDiscount: Int,
        slippageBps: Int?,
    ) {
        // bps → percent (100 bps = 1%); Auto (null) keeps 1inch's historical 0.5% default.
        val slippagePercent = slippageBps?.let { it.toDouble() / 100.0 } ?: ONEINCH_DEFAULT_SLIPPAGE

        parameter("src", srcTokenContractAddress.takeIf { it.isNotEmpty() } ?: ONEINCH_NULL_ADDRESS)
        parameter("dst", dstTokenContractAddress.takeIf { it.isNotEmpty() } ?: ONEINCH_NULL_ADDRESS)
        parameter("amount", amount)
        parameter("from", srcAddress)
        parameter("slippage", slippagePercent.toString())
        parameter("disableEstimate", true)
        parameter("includeGas", true)
        parameter("referrer", ONEINCH_REFERRER_ADDRESS)
        parameter("fee", if (isAffiliate) discountedReferrerFee(bpsDiscount) else "0")
    }

    override suspend fun getTokens(chain: Chain): OneInchTokensJson =
        httpClient
            .get("https://api.vultisig.com/1inch/swap/v6.1/${chain.oneInchChainId()}/tokens")
            .bodyOrThrow<OneInchTokensJson>()

    override suspend fun getContractsWithBalance(chain: Chain, address: String): List<String> {
        val response =
            httpClient.get(
                "https://api.vultisig.com/1inch/balance/v1.2/${chain.oneInchChainId()}/balances/$address"
            )
        return response.bodyOrThrow<Map<String, String>>().mapNotNull { (key, value) ->
            if (value.toBigInteger() > BigInteger.ZERO) key else null
        }
    }

    override suspend fun getTokensByContracts(
        chain: Chain,
        contractAddresses: List<String>,
    ): Map<String, OneInchTokenJson> {
        if (contractAddresses.isEmpty()) return emptyMap()
        return httpClient
            .get("https://api.vultisig.com/1inch/token/v1.2/${chain.oneInchChainId()}/custom") {
                parameter("addresses", contractAddresses.joinToString(","))
            }
            .bodyOrThrow<Map<String, OneInchTokenJson>>()
    }

    companion object {
        private const val ONEINCH_REFERRER_ADDRESS = "0x8E247a480449c84a5fDD25974A8501f3EFa4ABb9"
        private const val ONEINCH_REFERRER_FEE = 0.5
        private const val ONEINCH_NULL_ADDRESS = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        /** 1inch's historical default slippage (percent) used when the user leaves it on Auto. */
        private const val ONEINCH_DEFAULT_SLIPPAGE = 0.5

        /**
         * The affiliate fee percent sent to 1inch, reduced by the VULT [bpsDiscount] and clamped at
         * zero so a discount larger than [ONEINCH_REFERRER_FEE] never produces a negative fee
         * (which 1inch rejects). Mirrors iOS' `bps(for:)` and the zero-clamp every other provider
         * applies.
         */
        internal fun discountedReferrerFee(bpsDiscount: Int): Double {
            val discountPercent = round(bpsDiscount.toDouble()) / 100.0
            return round(maxOf(ONEINCH_REFERRER_FEE - discountPercent, 0.0) * 100) / 100.0
        }
    }
}
