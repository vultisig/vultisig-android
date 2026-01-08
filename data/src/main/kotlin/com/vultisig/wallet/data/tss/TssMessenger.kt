package com.vultisig.wallet.data.tss

import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.encryptNoEncode
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class TssMessenger(
    serverAddress: String,
    private val sessionID: String,
    private val encryptionHex: String,
    private val sessionApi: SessionApi,
    private val coroutineScope: CoroutineScope,
    private val encryption: Encryption,
    private val isEncryptionGCM: Boolean,
) : tss.Messenger {
    private val serverUrl = "$serverAddress/message/$sessionID"
    private var messageID: String? = null
    private var counter = 1
    fun setMessageID(messageID: String?) {
        this.messageID = messageID
    }

    override fun send(from: String, to: String, body: String) {
        val encryptedBody: ByteArray =
            if (isEncryptionGCM) {
                Timber.d("encrypting message with AES+GCM")
                encryption.encrypt(body.toByteArray(), Numeric.hexStringToByteArray(encryptionHex))
            } else {
                Timber.d("encrypting message with AES+CBC")
                body.encryptNoEncode(encryptionHex)
            }
        val message = Message(
            sessionID, from, listOf(to), Base64.encode(encryptedBody), body.md5(), counter++
        )
        coroutineScope.launch {
            for (i in 1..3) {
                try {
                    sessionApi.sendTssMessage(serverUrl, messageID, message)
                    Timber.tag("TssMessenger").d("send message success")
                    break
                } catch (e: Exception) {
                    Timber.tag("TssMessenger")
                        .e("fail to send message: ${e.stackTraceToString()} , attempt: $i")
                }
            }
        }
    }
}