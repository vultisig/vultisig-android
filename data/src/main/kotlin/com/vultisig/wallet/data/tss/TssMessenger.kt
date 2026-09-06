package com.vultisig.wallet.data.tss

import com.vultisig.wallet.data.api.RelaySendBudget
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.encryptNoEncode
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.utils.Numeric
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

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
    @Volatile private var messageID: String? = null
    // Atomic because the DKLS ceremonies fan one outbound round out to every peer concurrently,
    // and a lost increment would give two messages bound for the same peer the same sequence
    // number, which the receiver sorts by before applying.
    private val counter = AtomicInteger(1)

    fun setMessageID(messageID: String?) {
        this.messageID = messageID
    }

    /**
     * Sends one message and does not return until the relay has accepted it or the retry budget is
     * spent, in which case it throws.
     *
     * This is what the DKLS-family ceremonies use. [send] cannot be it: `tss.Messenger` is a
     * gomobile interface called synchronously from native code on the legacy GG20 path, so its
     * signature is fixed and it can only fire the send off into [coroutineScope]. That is the bug
     * behind issue #5813 — a failed relay POST left the ceremony waiting on a reply that could
     * never arrive until the 60 s stall fired, and then restarting locally while the peers were
     * still on the old session.
     *
     * The retry itself lives in [SessionApi.sendTssMessage]. The message is built once here so
     * every attempt carries the same hash and sequence number and the relay and receiver dedupe it
     * instead of applying it twice.
     *
     * @param deadlineNanos [System.nanoTime] instant this send may not outlive, or `null` for the
     *   full [RelaySendBudget]. Callers whose own deadline is absolute pass one so the send cannot
     *   outlive the round it belongs to.
     */
    suspend fun sendAwait(from: String, to: String, body: String, deadlineNanos: Long? = null) {
        sessionApi.sendTssMessage(
            serverUrl = serverUrl,
            messageId = messageID,
            message = buildMessage(from, to, body),
            budget = RelaySendBudget(deadlineNanos = deadlineNanos),
        )
    }

    /**
     * Fire-and-forget send required by the gomobile `tss.Messenger` interface, still used by the
     * legacy GG20 keygen and keysign paths. Prefer [sendAwait] anywhere the call site can suspend.
     */
    override fun send(from: String, to: String, body: String) {
        val message = buildMessage(from, to, body)
        coroutineScope.launch {
            for (i in 1..3) {
                try {
                    sessionApi.sendTssMessage(serverUrl, messageID, message)
                    Timber.tag("TssMessenger").d("send message success")
                    // when it reach to this point , it means the message was sent successfully
                    break
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.tag("TssMessenger")
                        .e("fail to send message: ${e.stackTraceToString()} , attempt: $i")
                }
            }
        }
    }

    private fun buildMessage(from: String, to: String, body: String): Message {
        val encryptedBody: ByteArray =
            if (isEncryptionGCM) {
                Timber.d("encrypting message with AES+GCM")
                encryption.encrypt(body.toByteArray(), Numeric.hexStringToByteArray(encryptionHex))
            } else {
                Timber.d("encrypting message with AES+CBC")
                body.encryptNoEncode(encryptionHex)
            }
        return Message(
            sessionID,
            from,
            listOf(to),
            Base64.encode(encryptedBody),
            body.md5(),
            counter.getAndIncrement(),
        )
    }
}
