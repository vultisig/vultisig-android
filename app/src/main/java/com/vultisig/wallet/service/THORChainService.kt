package com.vultisig.wallet.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.models.cosmos.CosmosBalance
import com.vultisig.wallet.models.cosmos.CosmosBalanceResponse
import com.vultisig.wallet.models.cosmos.CosmosTransactionBroadcastResponse
import com.vultisig.wallet.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.models.swap.THORChainSwapQuote
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class THORChainService(
    private val gson: Gson,
) {
    private val xClientID = "X-Client-ID"
    private val xClientIDValue = "vultisig"
    fun getBalance(address: String): List<CosmosBalance> {
        try {
            val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true).build()
            val request = okhttp3.Request.Builder()
                .url(Endpoints.fetchAccountBalanceTHORChain9r(address))
                .method("GET", null)
                .addHeader(xClientID, xClientIDValue)
                .build()
            val response = client.newCall(request).execute()
            val resp = gson.fromJson(response.body?.string(), CosmosBalanceResponse::class.java)
            return resp.balances
        } catch (e: Exception) {
            Log.d("THORChainService", "Error getting $address balance: ${e.message}")
        }
        return emptyList()
    }

    fun getAccountNumber(address: String): THORChainAccountValue? {
        try {
            val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true).build()
            val request = okhttp3.Request.Builder()
                .url(Endpoints.fetchAccountNumberTHORChain9r(address))
                .method("GET", null)
                .addHeader(xClientID, xClientIDValue)
                .build()
            val response = client.newCall(request).execute()
            val jsonObject = gson.fromJson(response.body?.string(), JsonObject::class.java)
            val valueObject = jsonObject.get("result")?.asJsonObject?.get("value")?.asJsonObject
            valueObject?.let {
                return gson.fromJson(valueObject, THORChainAccountValue::class.java)
            }

        } catch (e: Exception) {
            Log.d("THORChainService", "Error getting $address account number: ${e.message}")
        }
        return null
    }

    fun fetchSwapQuotes(
        address: String,
        fromAsset: String,
        toAsset: String,
        amount: String,
        interval: String,
    ): THORChainSwapQuote {
        try {
            val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true).build()
            val request = okhttp3.Request.Builder()
                .url(
                    Endpoints.fetchSwaoQuoteThorchain9r(
                        address,
                        fromAsset,
                        toAsset,
                        amount,
                        interval
                    )
                )
                .method("GET", null)
                .addHeader(xClientID, xClientIDValue)
                .build()
            val response = client.newCall(request).execute()
            return gson.fromJson(response.body?.string(), THORChainSwapQuote::class.java)
        } catch (e: Exception) {
            Log.d("THORChainService", "Error getting swap quotes: ${e.message}")
            throw e
        }
    }

    fun broadcastTransaction(tx: String): String? {
        try {
            val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true).build()
            val request = okhttp3.Request.Builder()
                .url(Endpoints.THORCHAINBroadcastTx)
                .method("POST", tx.toRequestBody("application/json".toMediaType()))
                .addHeader(xClientID, xClientIDValue)
                .build()
            val response = client.newCall(request).execute()
            val responseRawString = response.body?.string()
            val result = Gson().fromJson(
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
            Log.d("THORChainService", "Error broadcasting transaction: ${e.message}")
            throw e
        }
        return null
    }
}