package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject

internal interface OneInchApi {

    suspend fun getSwapQuote(
        chain: Int,
        srcTokenContractAddress: String,
        dstTokenContractAddress: String,
        srcAddress: String,
        amount: String,
        isAffiliate: Boolean
    ): OneInchSwapQuoteJson

}

internal class OneInchApiImpl @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : OneInchApi {

    override suspend fun getSwapQuote(
        chain: Int,
        srcTokenContractAddress: String,
        dstTokenContractAddress: String,
        srcAddress: String,
        amount: String,
        isAffiliate: Boolean
    ): OneInchSwapQuoteJson {
        val response = httpClient.get("https://api.vultisig.com/1inch/swap/v6.0/$chain/swap") {
            parameter("src", srcTokenContractAddress.takeIf { it.isNotEmpty() } ?: ONEINCH_NULL_ADDRESS)
            parameter("dst", dstTokenContractAddress.takeIf { it.isNotEmpty() } ?: ONEINCH_NULL_ADDRESS)
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

        return gson.fromJson(response.bodyAsText(), OneInchSwapQuoteJson::class.java)
    }

    companion object {
        private const val ONEINCH_REFERRER_ADDRESS = "0xa4a4f610e89488eb4ecc6c63069f241a54485269"
        private const val ONEINCH_REFERRER_FEE = 0.5
        private const val ONEINCH_NULL_ADDRESS = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    }

}