package com.vultisig.wallet.data.tss

import com.vultisig.wallet.data.common.encrypt
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.mediator.Message
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

class TssMessenger(
    serverAddress: String,
    private val sessionID: String,
    private val encryptionHex: String,

    ) : tss.Messenger {
    private val serverUrl = "$serverAddress/message/$sessionID"
    private var messageID: String? = null
    private var counter = 1
    fun setMessageID(messageID: String?) {
        this.messageID = messageID
    }

    override fun send(from: String, to: String, body: String) {
        val encryptedBody = body.encrypt(encryptionHex)
        val message = Message(
            sessionID, from, listOf(to), encryptedBody, body.md5(), counter++
        )
        for (i in 1..3) {
            try {
                val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
                val request = okhttp3.Request.Builder().url(serverUrl)
                    .post(message.toJson().toRequestBody("application/json".toMediaType()))
                this.messageID?.let {
                    request.addHeader("message_id", it)
                }

                Timber.tag("TssMessenger")
                    .d("sending message from: $from to: $to, hash: ${message.hash}")
                client.newCall(request.build()).execute().use { response ->
                    if (response.code == 201) {
                        Timber.tag("TssMessenger").d("send message success")
                        return
                    }
                }
                // when it reach to this point , it means the message was sent successfully
                break
            } catch (e: Exception) {
                Timber.tag("TssMessenger")
                    .e("fail to send message: ${e.stackTraceToString()} , attempt: $i")

            }
        }
    }
}