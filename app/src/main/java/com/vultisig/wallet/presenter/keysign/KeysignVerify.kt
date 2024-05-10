package com.vultisig.wallet.presenter.keysign

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

class KeysignVerify(
    serverAddr: String,
    sessionId: String,
    private val gson: Gson,
) {
    private val serverURL = "$serverAddr/complete/$sessionId/keysign"
    suspend fun markLocalPartyKeysignComplete(message: String, sig: tss.KeysignResponse) {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true).build()
                val jsonResult = gson.toJson(sig)
                val request = okhttp3.Request.Builder()
                    .url(serverURL)
                    .header("message_id", message)
                    .post(jsonResult.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use {
                    Timber.tag("KeysignVerify").d(
                        "markLocalPartyKeysignComplete: Response code: ${it.code}"
                    )
                }
            } catch (e: Exception) {
                Timber.tag("KeysignVerify").d(
                    "markLocalPartyKeysignComplete error: ${e.stackTraceToString()}"
                )
            }
        }
    }

    suspend fun checkKeysignComplete(message: String): tss.KeysignResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true).build()
                val request = okhttp3.Request.Builder()
                    .url(serverURL)
                    .header("message_id", message)
                    .get()
                    .build()
                val result = client.newCall(request)
                    .execute()
                    .use { response ->
                        when (response.code) {
                            200 -> response.body?.let {
                                val result = it.string()
                                gson.fromJson(result, tss.KeysignResponse::class.java)
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