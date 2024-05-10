package com.vultisig.wallet.presenter.keysign

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class KeysignVerify(
    private val serverAddr: String,
    private val sessionId: String,
) {
    private val serverURL = "$serverAddr/complete/$sessionId/keysign"
    suspend fun markLocalPartyKeysignComplete(message: String, sig: tss.KeysignResponse) {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true).build()
                val jsonResult = Gson().toJson(sig)
                val request = okhttp3.Request.Builder()
                    .url(serverURL)
                    .header("message_id", message)
                    .post(jsonResult.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use {
                    Log.d(
                        "KeysignVerify",
                        "markLocalPartyKeysignComplete: Response code: ${it.code}"
                    )
                }
            } catch (e: Exception) {
                Log.d(
                    "KeysignVerify",
                    "markLocalPartyKeysignComplete error: ${e.stackTraceToString()}"
                )
            }
        }
    }

    suspend fun checkKeysignComplete(message: String): tss.KeysignResponse?{
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true).build()
                val request = okhttp3.Request.Builder()
                    .url(serverURL)
                    .header("message_id", message)
                    .get()
                    .build()
                val result =  client.newCall(request)
                    .execute()
                    .use { response ->
                        when (response.code) {
                            200 -> response.body?.let {
                                val result = it.string()
                                Gson().fromJson(result, tss.KeysignResponse::class.java)
                            }

                            else -> {
                                Log.d(
                                    "KeysignVerify",
                                    "checkKeysignComplete: Failed to check keysign complete: Response code: ${response.code}"
                                )
                                null
                            }
                        }
                    }
                return@withContext result
            } catch (e: Exception) {
                Log.d("KeysignVerify", "checkKeysignComplete error: ${e.stackTraceToString()}")
            }
            return@withContext null
        }
    }

}