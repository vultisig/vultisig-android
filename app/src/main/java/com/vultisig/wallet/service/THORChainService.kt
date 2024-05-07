package com.vultisig.wallet.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.models.cosmos.CosmosBalance
import com.vultisig.wallet.models.cosmos.CosmosBalanceResponse
import com.vultisig.wallet.models.cosmos.THORChainAccountValue
import com.vultisig.wallet.models.swap.THORChainSwapQuote
import okhttp3.OkHttpClient

class THORChainService {
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
            val resp = Gson().fromJson(response.body?.string(), CosmosBalanceResponse::class.java)
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
            val jsonObject = Gson().fromJson(response.body?.string(), JsonObject::class.java)
            val valueObject = jsonObject.get("result")?.asJsonObject?.get("value")?.asJsonObject
            valueObject?.let {
                return Gson().fromJson(valueObject, THORChainAccountValue::class.java)
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
            return Gson().fromJson(response.body?.string(), THORChainSwapQuote::class.java)
        } catch (e: Exception) {
            Log.d("THORChainService", "Error getting swap quotes: ${e.message}")
            throw e
        }
    }
}