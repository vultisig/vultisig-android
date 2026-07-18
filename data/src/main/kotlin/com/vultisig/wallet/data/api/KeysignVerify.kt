package com.vultisig.wallet.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

class KeysignVerify(serverAddress: String, sessionId: String, private val sessionApi: SessionApi) {
    private val serverURL = "$serverAddress/complete/$sessionId/keysign"

    /**
     * Publishes the completed signature so peers stuck behind a dropped relay message can recover
     * it via [checkKeysignComplete]. This is a POST, so the shared Ktor client does not auto-retry
     * it (unlike the idempotent recovery GET); a single transport blip here would strand every
     * other peer with "signatures empty". Reposting the same [sig] under the same [messageId] is
     * idempotent at the relay, so we retry the write ourselves before giving up.
     */
    suspend fun markLocalPartyKeysignComplete(messageId: String, sig: tss.KeysignResponse) {
        withContext(Dispatchers.IO) {
            repeat(MAX_MARK_COMPLETE_RETRIES) { attempt ->
                try {
                    sessionApi.markLocalPartyKeysignComplete(serverURL, messageId, sig)
                    Timber.tag("KeysignVerify").d("markLocalPartyKeysignComplete")
                    return@withContext
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.tag("KeysignVerify")
                        .d(
                            "markLocalPartyKeysignComplete error (attempt %d): %s",
                            attempt + 1,
                            e.stackTraceToString(),
                        )
                    if (attempt < MAX_MARK_COMPLETE_RETRIES - 1) {
                        delay(MARK_COMPLETE_BACKOFF_MS)
                    }
                }
            }
        }
    }

    suspend fun checkKeysignComplete(message: String): tss.KeysignResponse? {
        return withContext(Dispatchers.IO) {
            try {
                return@withContext sessionApi.checkKeysignComplete(serverURL, message)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag("KeysignVerify")
                    .d("checkKeysignComplete error: %s", e.stackTraceToString())
            }
            return@withContext null
        }
    }

    private companion object {
        const val MAX_MARK_COMPLETE_RETRIES = 3
        const val MARK_COMPLETE_BACKOFF_MS = 1000L
    }
}
