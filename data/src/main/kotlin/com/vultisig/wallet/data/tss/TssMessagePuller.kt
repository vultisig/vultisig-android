package com.vultisig.wallet.data.tss

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.gson.Gson
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.decrypt
import com.vultisig.wallet.data.mediator.Message
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Callback
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException

class TssMessagePuller(
    private val service: tss.ServiceImpl,
    private val hexEncryptionKey: String,
    private val serverAddress: String,
    private val localPartyKey: String,
    private val sessionID: String,
    private val sessionApi: SessionApi,
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
                val decryptedBody = msg.body.decrypt(hexEncryptionKey)
                Timber.d("apply message to TSS: hash: " + msg.hash + ", messageID: " + key)
                this.service.applyData(decryptedBody)
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