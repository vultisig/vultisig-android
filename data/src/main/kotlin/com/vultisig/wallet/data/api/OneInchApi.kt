package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.OneInchSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.api.models.OneInchTokenJson
import com.vultisig.wallet.data.api.models.OneInchTokensJson
import com.vultisig.wallet.data.api.models.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.THORChainSwapQuoteError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.oneInchChainId
import com.vultisig.wallet.data.utils.OneInchSwapQuoteResponseJsonSerializer
import com.vultisig.wallet.data.utils.ThorChainSwapQuoteResponseJsonSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
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
    ): OneInchSwapQuoteDeserialized {
        try {
            val response =
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
                }
            if (!response.status.isSuccess()) {
                return OneInchSwapQuoteDeserialized.Error(
                    error = HttpStatusCode.fromValue(response.status.value).description
                )
            }
            return json.decodeFromString(
                oneInchSwapQuoteResponseJsonSerializer,
                response.body<String>()
            )
        } catch (e: Exception) {
            return OneInchSwapQuoteDeserialized.Error(error = e.message ?: "Unknown error")
        }
    }


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