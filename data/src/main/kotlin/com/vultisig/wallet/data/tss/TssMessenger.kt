package com.vultisig.wallet.data.tss

import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.encrypt
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.mediator.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class TssMessenger(
    serverAddress: String,
    private val sessionID: String,
    private val encryptionHex: String,
    private val sessionApi: SessionApi,
    private val coroutineScope: CoroutineScope
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
        coroutineScope.launch {
            for (i in 1..3) {
                try {
                    sessionApi.sendTssMessage(serverUrl, messageID, message)
                    Timber.tag("TssMessenger").d("send message success")
                    // when it reach to this point , it means the message was sent successfully
                    break
                } catch (e: Exception) {
                    Timber.tag("TssMessenger")
                        .e("fail to send message: ${e.stackTraceToString()} , attempt: $i")

                }
            }
        }
    }
}