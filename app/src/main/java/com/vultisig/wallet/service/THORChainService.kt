package com.vultisig.wallet.service

import android.util.Log
import com.google.gson.Gson
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.models.cosmos.CosmosBalance
import com.vultisig.wallet.models.cosmos.CosmosBalanceResponse
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

    fun getAccountNumber(address: String) {

    }
}