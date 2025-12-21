package com.vultisig.wallet.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

class KeysignVerify(
    serverAddress: String,
    sessionId: String,
    private val sessionApi: SessionApi,
) {
    private val serverURL = "$serverAddress/complete/$sessionId/keysign"
    suspend fun markLocalPartyKeysignComplete(messageId: String, sig: tss.KeysignResponse) {
        withContext(Dispatchers.IO) {
            try {
                sessionApi.markLocalPartyKeysignComplete(serverURL, messageId, sig)
                Timber.tag("KeysignVerify").d("markLocalPartyKeysignComplete")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
                if (e is CancellationException) throw e
                Timber.tag("KeysignVerify")
                    .d("checkKeysignComplete error: %s", e.stackTraceToString())
            }
            return@withContext null
        }
    }

}