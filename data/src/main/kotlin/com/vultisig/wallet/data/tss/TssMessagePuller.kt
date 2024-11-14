package com.vultisig.wallet.data.tss

import android.util.Base64
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.decrypt
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class TssMessagePuller(
    private val service: tss.ServiceImpl,
    private val hexEncryptionKey: String,
    serverAddress: String,
    private val localPartyKey: String,
    private val sessionId: String,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val isEncryptionGCM: Boolean,
) {
    private val serverUrl = "$serverAddress/message/$sessionId/$localPartyKey"

    private var job: Job? = null

    private val appliedMessageKeys = mutableSetOf<String>()

    // start pulling messages from the server
    @OptIn(DelicateCoroutinesApi::class)
    fun pullMessages(messageID: String?) {
        this.job = GlobalScope.launch {
            while (isActive) {
                fetchMessages(messageID)
                delay(1.seconds)
            }
        }
    }

    private suspend fun fetchMessages(messageId: String?) {
        try {
            val messages = sessionApi.getTssMessages(serverUrl)

            for (msg in messages.sortedBy { it.sequenceNo }) {
                val key = messageId
                    ?.let { "$sessionId-$localPartyKey-$messageId-${msg.hash}" }
                    ?: "$sessionId-$localPartyKey-${msg.hash}"

                // when the message is already in the cache, skip it
                if (key in appliedMessageKeys) {
                    Timber.tag(TAG)
                        .d("skip message: $key, applied already")
                } else {
                    appliedMessageKeys += key

                    val decryptedBody = if (isEncryptionGCM) {
                        Timber.d("decrypting message with AES+GCM")

                        encryption.decrypt(
                            Base64.decode(msg.body, Base64.DEFAULT),
                            Numeric.hexStringToByteArray(hexEncryptionKey)
                        )
                    } else {
                        Timber.d("decrypting message with AES+CBC")

                        msg.body.decrypt(hexEncryptionKey).toByteArray(Charsets.UTF_8)
                    }

                    if (decryptedBody == null) {
                        Timber.tag(TAG)
                            .e("fail to decrypt message: $key")
                    } else {
                        Timber.d("apply message to TSS: hash: %s, messageID: %s", msg.hash, key)
                        this.service.applyData(String(decryptedBody, Charsets.UTF_8))
                        deleteMessageFromServer(msg.hash, messageId)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG)
                .e(e, "Failed to get messages from server")
        }
    }

    private suspend fun deleteMessageFromServer(msgHash: String, messageID: String?) {
        val urlString = "$serverUrl/$msgHash"
        sessionApi.deleteTssMessage(urlString, messageID)

        Timber.tag(TAG).d("Delete message success")
    }

    fun stop() {
        this.job?.cancel()
    }

    companion object {
        private const val TAG = "TssMessagePuller"
    }

}