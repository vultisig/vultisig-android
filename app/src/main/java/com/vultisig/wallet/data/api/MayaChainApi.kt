package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vultisig.wallet.chains.THORChainSwaps
import com.vultisig.wallet.data.api.models.CosmosBalance
import com.vultisig.wallet.data.api.models.CosmosBalanceResponse
import com.vultisig.wallet.models.cosmos.CosmosTransactionBroadcastResponse
import com.vultisig.wallet.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.models.swap.THORChainSwapQuote
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import timber.log.Timber
import javax.inject.Inject

internal interface MayaChainApi {

    suspend fun getBalance(
        address: String,
    ): List<CosmosBalance>

    suspend fun getAccountNumber(
        address: String,
    ): THORChainAccountValue

    suspend fun getSwapQuotes(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        interval: String,
        isAffiliate: Boolean,
    ): THORChainSwapQuote

    suspend fun broadcastTransaction(tx: String): String?
}

internal class MayaChainApiImp @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : MayaChainApi {

    private val xClientID = "X-Client-ID"
    private val xClientIDValue = "vultisig"

    override suspend fun getBalance(address: String): List<CosmosBalance> {
        val response = httpClient
            .get("https://mayanode.mayachain.info/cosmos/bank/v1beta1/balances/$address") {
                header(xClientID, xClientIDValue)
            }
        val resp = gson.fromJson(response.bodyAsText(), CosmosBalanceResponse::class.java)
        return resp.balances ?: emptyList()
    }

    override suspend fun getSwapQuotes(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        interval: String,
        isAffiliate: Boolean,
    ): THORChainSwapQuote {
        val response = httpClient
            .get("https://mayanode.mayachain.info/mayachain/quote/swap") {
                parameter("from_asset", fromAsset)
                parameter("to_asset", toAsset)
                parameter("amount", amount)
                parameter("destination", address)
                parameter("streaming_interval", interval)
                if (isAffiliate) {
                    parameter("affiliate", THORChainSwaps.AFFILIATE_FEE_ADDRESS)
                    parameter("affiliate_bps", THORChainSwaps.AFFILIATE_FEE_RATE)
                }
                header(xClientID, xClientIDValue)
            }
        return gson.fromJson(response.bodyAsText(), THORChainSwapQuote::class.java)
    }

    override suspend fun getAccountNumber(address: String): THORChainAccountValue {
        val response = httpClient
            .get("https://mayanode.mayachain.info/auth/accounts/$address") {
                header(xClientID, xClientIDValue)
            }
        val responseBody = response.bodyAsText()
        Timber.d("getAccountNumber: $responseBody")
        val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
        val valueObject = jsonObject.get("result")?.asJsonObject?.get("value")?.asJsonObject

        return valueObject?.let {
            gson.fromJson(valueObject, THORChainAccountValue::class.java)
        } ?: error("Field value is not found in the response")
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val response =
                httpClient.post("https://mayanode.mayachain.info/cosmos/tx/v1beta1/txs") {
                    contentType(ContentType.Application.Json)
                    header(xClientID, xClientIDValue)
                    setBody(tx)
                }
            val responseRawString = response.bodyAsText()
            val result = gson.fromJson(
                responseRawString,
                CosmosTransactionBroadcastResponse::class.java
            )
            result?.let {
                if (it.txResponse?.code == 0 || it.txResponse?.code == 19) {
                    return it.txResponse.txHash
                }
                throw Exception("Error broadcasting transaction: $responseRawString")
            }
        } catch (e: Exception) {
            Timber.tag("MayaChainService").e("Error broadcasting transaction: ${e.message}")
            throw e
        }
        return null
    }
}