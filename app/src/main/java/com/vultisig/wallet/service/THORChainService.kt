package com.vultisig.wallet.service

import android.util.Log
import com.google.gson.Gson
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.models.cosmos.CosmosTransactionBroadcastResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class THORChainService(
    private val gson: Gson,
) {
    private val xClientID = "X-Client-ID"
    private val xClientIDValue = "vultisig"

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