package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.vultisig.wallet.data.api.models.LiFiSwapQuoteJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject

internal interface LiFiChainApi {
    suspend fun getSwapQuote(
        fromChain: String,
        toChain: String,
        fromToken: String,
        toToken: String,
        fromAmount: String,
        fromAddress: String,
        toAddress: String,
    ) : LiFiSwapQuoteJson
}

internal class LiFiChainApiImpl @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : LiFiChainApi {
    override suspend fun getSwapQuote(
        fromChain: String,
        toChain: String,
        fromToken: String,
        toToken: String,
        fromAmount: String,
        fromAddress: String,
        toAddress: String,
    ): LiFiSwapQuoteJson {
        val response = httpClient
            .get("https://li.quest/v1/quote"){
                parameter("fromChain", fromChain)
                parameter("toChain", toChain)
                parameter("fromToken", fromToken)
                parameter("toToken", toToken)
                parameter("fromAmount", fromAmount)
                parameter("fromAddress", fromAddress)
                parameter("toAddress", toAddress)
            }
        return gson.fromJson(response.bodyAsText(), LiFiSwapQuoteJson::class.java)
    }
}