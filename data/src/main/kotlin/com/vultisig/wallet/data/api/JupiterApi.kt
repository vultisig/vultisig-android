package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.quotes.QuoteSwapTotalDataJson
import com.vultisig.wallet.data.api.models.quotes.QuoteSwapTransactionJson
import com.vultisig.wallet.data.api.models.quotes.SwapRouteResponseJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import javax.inject.Inject

interface JupiterApi {
    suspend fun getSwapQuote(
        fromAmount: String,
        fromToken: String,
        toToken: String,
        fromAddress: String,
    ): QuoteSwapTotalDataJson
}

internal class JupiterApiImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
) : JupiterApi {
    override suspend fun getSwapQuote(
        fromAmount: String,
        fromToken: String,
        toToken: String,
        fromAddress: String,
    ): QuoteSwapTotalDataJson {
        val quoteResponse = httpClient.get("https://lite-api.jup.ag/swap/v1/quote") {
            parameter("inputMint", fromToken)
            parameter("outputMint", toToken)
            parameter("amount", fromAmount)
        }
        val body = quoteResponse.body<SwapRouteResponseJson>()
        val outAmount = body.outAmount
        val routePlan = body.routePlan

        val quoteSwapRequestBody = buildJsonObject {
            put("quoteResponse", json.encodeToJsonElement(body))
            put("userPublicKey", fromAddress)
        }
        val quoteSwapData = httpClient.post("https://lite-api.jup.ag/swap/v1/swap") {
            setBody(quoteSwapRequestBody)
        }.body<QuoteSwapTransactionJson>()

        return QuoteSwapTotalDataJson(
            swapTransaction = quoteSwapData,
            dstAmount = outAmount,
            routePlan = routePlan
        )
    }

}