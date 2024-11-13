package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.LiFiSwapQuoteError
import com.vultisig.wallet.data.api.models.LiFiSwapQuoteJson
import com.vultisig.wallet.data.api.models.LiFiSwapQuoteResponse
import com.vultisig.wallet.data.api.models.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.THORChainSwapQuoteError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import javax.inject.Inject

interface LiFiChainApi {
    suspend fun getSwapQuote(
        fromChain: String,
        toChain: String,
        fromToken: String,
        toToken: String,
        fromAmount: String,
        fromAddress: String,
        toAddress: String,
    ) : LiFiSwapQuoteResponse
}

internal class LiFiChainApiImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
) : LiFiChainApi {
    override suspend fun getSwapQuote(
        fromChain: String,
        toChain: String,
        fromToken: String,
        toToken: String,
        fromAmount: String,
        fromAddress: String,
        toAddress: String,
    ): LiFiSwapQuoteResponse {
        val response = httpClient
            .get("https://li.quest/v1/quote") {
                parameter("fromChain", fromChain)
                parameter("toChain", toChain)
                parameter("fromToken", fromToken)
                parameter("toToken", toToken)
                parameter("fromAmount", fromAmount)
                parameter("fromAddress", fromAddress)
                parameter("toAddress", toAddress)
            }
        if (!response.status.isSuccess()) {
            if (response.status == HttpStatusCode.NotFound) {
                return LiFiSwapQuoteResponse.Error(
                    json.decodeFromString(
                        LiFiSwapQuoteError.serializer(),
                        response.body<String>()
                    )
                )
            }
            return LiFiSwapQuoteResponse.Error(
                LiFiSwapQuoteError(
                    response.status.description
                )
            )
        }
        return try {
            LiFiSwapQuoteResponse.Result(response.body<LiFiSwapQuoteJson>())
        } catch (e: Exception) {
            LiFiSwapQuoteResponse.Error(LiFiSwapQuoteError(response.body<String>()))
        }
    }
}