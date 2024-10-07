package com.vultisig.wallet.data.tss

import android.util.Base64
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
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

class TssMessagePuller(
    private val service: tss.ServiceImpl,
    private val hexEncryptionKey: String,
    private val serverAddress: String,
    private val localPartyKey: String,
    private val sessionID: String,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val isEncryptionGCM: Boolean,
) {
    private val serverURL = "$serverAddress/message/$sessionID/$localPartyKey"
    private var job: Job? = null
    private val cache: Cache<String, Any> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .build()

    // start pulling messages from the server
    @OptIn(DelicateCoroutinesApi::class)
    fun pullMessages(messageID: String?) {
        this.job = GlobalScope.launch {
            while (isActive) {
                getMessagesFromServer(messageID)
                delay(1000)
            }
        }
    }

    private suspend fun getMessagesFromServer(messageID: String?) {
        try {
            val messages = sessionApi.getTssMessages(serverURL)
            for (msg in messages.sortedBy { it.sequenceNo }) {
                val key = messageID?.let { "$sessionID-$localPartyKey-$messageID-${msg.hash}" }
                    ?: run { "$sessionID-$localPartyKey-${msg.hash}" }
                // when the message is already in the cache, skip it
                if (cache.getIfPresent(key) != null) {
                    Timber.tag("TssMessagePuller")
                        .d("skip message: $key, applied already")
                    continue
                }
                cache.put(key, msg)
                val decryptedBody = if (isEncryptionGCM) {
                    encryption.decrypt(
                        Base64.decode(msg.body, Base64.DEFAULT),
                        Numeric.hexStringToByteArray(hexEncryptionKey)
                    )
                } else {
                    msg.body.decrypt(hexEncryptionKey).toByteArray(Charsets.UTF_8)
                }
                if (decryptedBody == null) {
                    Timber.tag("TssMessagePuller")
                        .e("fail to decrypt message: $key")
                    continue
                }
                Timber.d("apply message to TSS: hash: %s, messageID: %s", msg.hash, key)
                this.service.applyData(String(decryptedBody, Charsets.UTF_8))
                deleteMessageFromServer(msg.hash, messageID)
            }
        } catch (e: Exception) {
            Timber.tag("TssMessagePuller")
                .e("fail to get messages from server: ${e.stackTraceToString()}")
        }
    }

    private suspend fun deleteMessageFromServer(msgHash: String, messageID: String?) {
        val urlString = "$serverAddress/message/$sessionID/$localPartyKey/$msgHash"
        sessionApi.deleteTssMessage(urlString, messageID)
        Timber.tag("TssMessagePuller").d("delete message success")
    }

    fun stop() {
        this.job?.cancel()
    }
}