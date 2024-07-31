package com.vultisig.wallet.data.api

import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject

internal interface LiFiChainApi {
    suspend fun getSwapQuote(
        fromChain: String,
        toChain: String,
        fromToken: String,
        toToken: String,
        fromAmount: String,
        fromAddress: String,
    )
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
    ) {
        val response = httpClient
            .get("https://li.quest/v1/quote"){
                parameter("fromChain", fromChain)
                parameter("toChain", toChain)
                parameter("fromToken", fromToken)
                parameter("toToken", toToken)
                parameter("fromAmount", fromAmount)
                parameter("fromAddress", fromAddress)
            }
    }
}