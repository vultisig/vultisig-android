package com.voltix.wallet.tss

import android.util.Log
import com.voltix.wallet.common.Encrypt
import com.voltix.wallet.common.md5
import com.voltix.wallet.mediator.Message
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class TssMessenger(
    private val serverAddress: String,
    private val sessionID: String,
    private val encryptionHex: String,
    private val messageID: String? = null,
) : tss.Messenger {
    private val serverUrl = "$serverAddress/message/$sessionID"
    private var counter = 1
    override fun send(from: String, to: String, body: String) {
        val encryptedBody = body.Encrypt(encryptionHex)
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
                Log.d("TssMessenger", "sending message from: $from to: $to, hash: ${message.hash}")
                client.newCall(request.build()).execute().use { response ->
                    if (response.code == 201) {
                        Log.d("TssMessenger", "send message success")
                        return
                    }
                }
                // when it reach to this point , it means the message was sent successfully
                break
            } catch (e: Exception) {
                Log.e(
                    "TssMessenger", "fail to send message: ${e.stackTraceToString()} , attempt: $i"
                )

            }
        }
    }
}