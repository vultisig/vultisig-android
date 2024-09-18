package com.vultisig.wallet.data.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class KeysignVerify(
    serverAddress: String,
    sessionId: String,
    private val sessionApi: SessionApi,
    private val gson: Gson,
) {
    private val serverURL = "$serverAddress/complete/$sessionId/keysign"
    suspend fun markLocalPartyKeysignComplete(messageId: String, sig: tss.KeysignResponse) {
        withContext(Dispatchers.IO) {
            try {
                sessionApi.markLocalPartyKeysignComplete(serverURL, messageId, sig)
                Timber.tag("KeysignVerify").d("markLocalPartyKeysignComplete")
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
                return@withContext sessionApi.checkKeysignComplete(serverURL, message)
            } catch (e: Exception) {
                Timber.tag("KeysignVerify")
                    .d("checkKeysignComplete error: %s", e.stackTraceToString())
            }
            return@withContext null
        }
    }

}