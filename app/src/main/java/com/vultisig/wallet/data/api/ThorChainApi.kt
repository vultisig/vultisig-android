package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.data.models.CosmosBalance
import com.vultisig.wallet.models.cosmos.CosmosBalanceResponse
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

internal interface ThorChainApi {

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
    ): THORChainSwapQuote

    suspend fun broadcastTransaction(tx: String): String?
}

internal class ThorChainApiImpl @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : ThorChainApi {

    private val xClientID = "X-Client-ID"
    private val xClientIDValue = "vultisig"

    override suspend fun getBalance(address: String): List<CosmosBalance> {
        val response = httpClient
            .get("https://thornode.ninerealms.com/cosmos/bank/v1beta1/balances/$address") {
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
    ): THORChainSwapQuote {
        val response = httpClient
            .get("https://thornode.ninerealms.com/thorchain/quote/swap") {
                parameter("from_asset", fromAsset)
                parameter("to_asset", toAsset)
                parameter("amount", amount)
                parameter("destination", address)
                parameter("streaming_interval", interval)
                header(xClientID, xClientIDValue)
            }
        return gson.fromJson(response.bodyAsText(), THORChainSwapQuote::class.java)
    }

    override suspend fun getAccountNumber(address: String): THORChainAccountValue {
        val response = httpClient
            .get("https://thornode.ninerealms.com/auth/accounts/$address") {
                header(xClientID, xClientIDValue)
            }
        val jsonObject = gson.fromJson(response.bodyAsText(), JsonObject::class.java)
        val valueObject = jsonObject.get("result")?.asJsonObject?.get("value")?.asJsonObject

        return valueObject?.let {
            gson.fromJson(valueObject, THORChainAccountValue::class.java)
        } ?: error("Field value is not found in the response")
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val response = httpClient.post(Endpoints.THORCHAINBroadcastTx) {
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
                    return it.txResponse.txhash
                }
                throw Exception("Error broadcasting transaction: $responseRawString")
            }
        } catch (e: Exception) {
            Timber.tag("THORChainService").e("Error broadcasting transaction: ${e.message}")
            throw e
        }
        return null
    }
}