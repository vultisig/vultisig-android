package com.vultisig.wallet.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vultisig.wallet.data.api.models.CosmosBalance
import com.vultisig.wallet.data.api.models.CosmosBalanceResponse
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.cosmos.CosmosTransactionBroadcastResponse
import com.vultisig.wallet.models.cosmos.THORChainAccountValue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import timber.log.Timber
import javax.inject.Inject

internal interface CosmosApi {
    suspend fun getBalance(address: String): List<CosmosBalance>
    suspend fun getAccountNumber(address: String): THORChainAccountValue
    suspend fun broadcastTransaction(tx: String): String?
}

internal interface CosmosApiFactory {
    fun createCosmosApi(chain: Chain): CosmosApi
}

internal class CosmosApiFactoryImp @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
) : CosmosApiFactory {
    override fun createCosmosApi(chain: Chain): CosmosApi {
        return when (chain) {
            Chain.GaiaChain -> CosmosApiImp(gson, httpClient, "https://cosmos-rest.publicnode.com")
            Chain.Kujira -> CosmosApiImp(gson, httpClient, "https://kujira-rest.publicnode.com")
            Chain.Dydx -> CosmosApiImp(gson, httpClient, "https://dydx-rest.publicnode.com")
            else -> throw IllegalArgumentException("Unsupported chain $chain")
        }
    }
}

internal class CosmosApiImp @Inject constructor(
    private val gson: Gson,
    private val httpClient: HttpClient,
    private val rpcEndpoint: String,
) : CosmosApi {
    override suspend fun getBalance(address: String): List<CosmosBalance> {
        val response = httpClient
            .get("$rpcEndpoint/cosmos/bank/v1beta1/balances/$address") {
            }
        val resp = gson.fromJson(response.bodyAsText(), CosmosBalanceResponse::class.java)
        return resp.balances ?: emptyList()
    }

    override suspend fun getAccountNumber(address: String): THORChainAccountValue {
        val response = httpClient
            .get("$rpcEndpoint/cosmos/auth/v1beta1/accounts/$address") {
            }
        val responseBody = response.bodyAsText()
        Timber.d("getAccountNumber: $responseBody")
        val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
        val valueObject = jsonObject.get("account").asJsonObject

        return valueObject?.let {
            gson.fromJson(valueObject, THORChainAccountValue::class.java)
        } ?: error("Error getting account")
    }

    override suspend fun broadcastTransaction(tx: String): String? {
        try {
            val response =
                httpClient.post("$rpcEndpoint/cosmos/tx/v1beta1/txs") {
                    contentType(ContentType.Application.Json)
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
            Timber.tag("CosmosApiService").e("Error broadcasting transaction: ${e.message}")
            throw e
        }
        return null
    }
}