@file:OptIn(ExperimentalEncodingApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.keygen

import com.silencelaboratories.goschnorr.BufferUtilJNI
import com.silencelaboratories.goschnorr.Handle
import com.silencelaboratories.goschnorr.go_slice
import com.silencelaboratories.goschnorr.goschnorr.schnorr_decode_message
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keyshare_from_bytes
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keyshare_key_id
import com.silencelaboratories.goschnorr.goschnorr.schnorr_sign_session_finish
import com.silencelaboratories.goschnorr.goschnorr.schnorr_sign_session_from_setup
import com.silencelaboratories.goschnorr.goschnorr.schnorr_sign_session_input_message
import com.silencelaboratories.goschnorr.goschnorr.schnorr_sign_session_message_receiver
import com.silencelaboratories.goschnorr.goschnorr.schnorr_sign_session_output_message
import com.silencelaboratories.goschnorr.goschnorr.schnorr_sign_setupmsg_new
import com.silencelaboratories.goschnorr.goschnorr.tss_buffer_free
import com.silencelaboratories.goschnorr.schnorr_lib_error
import com.silencelaboratories.goschnorr.schnorr_lib_error.LIB_OK
import com.silencelaboratories.goschnorr.tss_buffer
import com.vultisig.wallet.data.api.KeysignVerify
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import tss.KeysignResponse
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class SchnorrKeysign(
    val keysignCommittee: List<String>,
    val mediatorURL: String,
    val sessionID: String,
    val messageToSign: List<String>,
    val vault: Vault,
    val encryptionKeyHex: String,
    val isInitiateDevice: Boolean,


    private val sessionApi: SessionApi,
    private val encryption: Encryption,
) {
    val localPartyID: String = vault.localPartyID
    val publicKeyEdDSA: String = vault.pubKeyEDDSA
    var messenger: TssMessenger? = null
    var keysignDoneIndicator = false
    val keySignLock = ReentrantLock()
    val cache = mutableMapOf<String, Any>()
    val signatures = mutableMapOf<String, KeysignResponse>()
    var keyshare: ByteArray = byteArrayOf()

    fun isKeysignDone(): Boolean = keySignLock.withLock { keysignDoneIndicator }

    fun setKeysignDone(status: Boolean) = keySignLock.withLock { keysignDoneIndicator = status }

    fun getKeyshareString(): String? {
        for (ks in vault.keyshares) {
            if (ks.pubKey == publicKeyEdDSA) {
                return ks.keyShare
            }
        }
        return null
    }

    @Throws(Exception::class)
    fun getKeyshareBytes(): ByteArray {
        val localKeyshare =
            getKeyshareString() ?: throw RuntimeException("fail to get local keyshare")
        return Base64.decode(localKeyshare)
    }

    @Throws(Exception::class)
    fun getKeyshareID(): ByteArray {
        val buf = tss_buffer()
        try {
            val keyShareBytes = getKeyshareBytes()
            val keyshareSlice = keyShareBytes.toGoSlice()
            val h = Handle()
            val result = schnorr_keyshare_from_bytes(keyshareSlice, h)
            if (result != LIB_OK) {
                throw RuntimeException("fail to create keyshare handle from bytes, $result")
            }
            val keyIDResult = schnorr_keyshare_key_id(h, buf)
            if (keyIDResult != LIB_OK) {
                throw RuntimeException("fail to get key id from keyshare: $keyIDResult")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            tss_buffer_free(buf)
        }
    }

    @Throws(Exception::class)
    fun getKeysignSetupMessage(message: String): ByteArray {
        val buf = tss_buffer()
        try {
            val keyIdArr = getKeyshareID()
            val keyIdSlice = keyIdArr.toGoSlice()
            val byteArray = DklsHelper.arrayToBytes(keysignCommittee)
            val ids = byteArray.toGoSlice()
            val decodedMsgData = message.hexToByteArray()
            val msgSlice = decodedMsgData.toGoSlice()
            val err = schnorr_sign_setupmsg_new(keyIdSlice, null, msgSlice, ids, buf)
            if (err != LIB_OK) {
                throw RuntimeException("fail to setup keysign message, error: $err")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            tss_buffer_free(buf)
        }
    }

    @Throws(Exception::class)
    private fun decodeMessage(setupMsg: ByteArray): String {
        val buf = tss_buffer()
        try {
            val setupMsgSlice = setupMsg.toGoSlice()
            val result = schnorr_decode_message(setupMsgSlice, buf)
            if (result != LIB_OK) {
                throw RuntimeException("fail to extract message from setup message: $result")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf).toHexString()
        } finally {
            tss_buffer_free(buf)
        }
    }

    fun getOutboundMessageReceiver(handle: Handle, message: go_slice, idx: Long): ByteArray {
        val bufReceiver = tss_buffer()
        try {
            val receiverResult =
                schnorr_sign_session_message_receiver(handle, message, idx, bufReceiver)
            if (receiverResult != LIB_OK) {
                Timber.d("fail to get receiver message, error: $receiverResult")
                return byteArrayOf()
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(bufReceiver)
        } finally {
            tss_buffer_free(bufReceiver)
        }
    }

    fun getSchnorrOutboundMessage(handle: Handle): Pair<schnorr_lib_error, ByteArray> {
        val buf = tss_buffer()
        try {
            val result = schnorr_sign_session_output_message(handle, buf)
            if (result != LIB_OK) {
                Timber.d("fail to get outbound message: $result")
                return Pair(result, byteArrayOf())
            }
            return Pair(result, BufferUtilJNI.get_bytes_from_tss_buffer(buf))
        } finally {
            tss_buffer_free(buf)
        }
    }

    suspend fun processSchnorrOutboundMessage(handle: Handle) {
        while (true) {
            val (result, outboundMessage) = getSchnorrOutboundMessage(handle)
            if (result != LIB_OK) {
                println("fail to get outbound message")
            }
            if (outboundMessage.isEmpty()) {
                if (isKeysignDone()) {
                    println("EdDSA keysign finished")
                    return
                }
                delay(100)
                continue
            }
            val message = outboundMessage.toGoSlice()
            val encodedOutboundMessage = Base64.encode(outboundMessage)
            for (i in keysignCommittee.indices) {
                val receiverArray = getOutboundMessageReceiver(handle, message, i.toLong())
                if (receiverArray.isEmpty()) {
                    break
                }
                val receiverString = String(receiverArray, Charsets.UTF_8)
                Timber.d("sending message from $localPartyID to: $receiverString, content length: ${encodedOutboundMessage.length}")
                messenger?.send(localPartyID, receiverString, encodedOutboundMessage)
            }
        }
    }

    suspend fun pullInboundMessages(handle: Handle, messageID: String): Boolean {
        Timber.d("start pulling inbound messages")

        val start = System.nanoTime()
        while (true) {
            try {
                val msgs = sessionApi
                    .getTssMessages(mediatorURL, sessionID, localPartyID, messageID)

                if (msgs.isNotEmpty()) {
                    if (processInboundMessage(handle, msgs, messageID)) {
                        return true
                    }
                } else {
                    delay(100)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to get messages")
                delay(100)
            }

            val elapsedTime = (System.nanoTime() - start) / 1_000_000_000.0
            if (elapsedTime > 60) {
                error("timeout: Schnorr keysign did not finish within 60 seconds")
            }
        }

        return false
    }

    suspend fun processInboundMessage(
        handle: Handle,
        msgs: List<Message>,
        messageID: String
    ): Boolean {
        val sortedMsgs = msgs.sortedBy { it.sequenceNo }
        for (msg in sortedMsgs) {
            val key = "$sessionID-$localPartyID-$messageID-${msg.hash}"
            if (cache.containsKey(key)) {
                println("message with key: $key has been applied before")
                continue
            }
            println("Got message from: ${msg.from}, to: ${msg.to}, key: $key")
            val decryptedBody = encryption.decrypt(
                Base64.Default.decode(msg.body),
                Numeric.hexStringToByteArray(encryptionKeyHex)
            ) ?: error("fail to decrypt message body")
            val decodedMsg = Base64.decode(decryptedBody)
            val decryptedBodySlice = decodedMsg.toGoSlice()
            val isFinished = intArrayOf(0)
            val result = schnorr_sign_session_input_message(handle, decryptedBodySlice, isFinished)
            if (result != LIB_OK) {
                throw RuntimeException("fail to apply message to dkls, $result")
            }
            cache[key] = Any()
            deleteMessageFromServer(msg.hash, messageID)
            if (isFinished[0] != 0) {
                return true
            }
        }
        return false
    }

    private suspend fun deleteMessageFromServer(hash: String, messageID: String) {
        sessionApi.deleteTssMessage(mediatorURL, sessionID, localPartyID, hash, messageID)
    }

    suspend fun keysignOneMessageWithRetry(attempt: Int, messageToSign: String) {
        setKeysignDone(false)
        val msgHash = messageToSign.md5()
        val localMessenger = TssMessenger(
            mediatorURL, sessionID, encryptionKeyHex, sessionApi,
            CoroutineScope(Dispatchers.IO), encryption, true
        )
        localMessenger.setMessageID(msgHash)
        messenger = localMessenger
        try {
            val keysignSetupMsg: ByteArray

            if (isInitiateDevice) {
                keysignSetupMsg = getKeysignSetupMessage(messageToSign)

                sessionApi.uploadSetupMessage(
                    serverUrl = mediatorURL,
                    sessionId = sessionID,
                    message = Base64.encode(
                        encryption.encrypt(
                            Base64.encodeToByteArray(keysignSetupMsg),
                            Numeric.hexStringToByteArray(encryptionKeyHex)
                        )
                    ),
                    messageId = msgHash
                )
            } else {
                keysignSetupMsg = sessionApi.getSetupMessage(mediatorURL, sessionID,msgHash)
                    .let {
                        encryption.decrypt(
                            Base64.Default.decode(it),
                            Numeric.hexStringToByteArray(encryptionKeyHex)
                        )!!
                    }.let {
                        Base64.decode(it)
                    }
            }

            val signingMsg = decodeMessage(keysignSetupMsg)
            if (signingMsg != messageToSign) {
                throw RuntimeException("message doesn't match ($messageToSign) vs ($signingMsg)")
            }
            val finalSetupMsgArr = keysignSetupMsg
            val decodedSetupMsg = finalSetupMsgArr.toGoSlice()
            val handler = Handle()
            val localPartyIDArr = localPartyID.toByteArray()
            val localPartySlice = localPartyIDArr.toGoSlice()
            val keyShareBytes = getKeyshareBytes()
            val keyshareSlice = keyShareBytes.toGoSlice()
            val keyshareHandle = Handle()
            val result = schnorr_keyshare_from_bytes(keyshareSlice, keyshareHandle)
            if (result != LIB_OK) {
                throw RuntimeException("fail to create keyshare handle from bytes, $result")
            }
            val sessionResult = schnorr_sign_session_from_setup(
                decodedSetupMsg,
                localPartySlice,
                keyshareHandle,
                handler
            )
            if (sessionResult != LIB_OK) {
                throw RuntimeException("fail to create sign session from setup message, error: $sessionResult")
            }
            val h = handler
            val task = CoroutineScope(Dispatchers.IO).launch {
                processSchnorrOutboundMessage(h)
            }
            val isFinished = pullInboundMessages(h, msgHash)
            if (isFinished) {
                setKeysignDone(true)
                val sig = signSessionFinish(h)
                val resp = KeysignResponse()
                resp.msg = messageToSign
                val r = sig.copyOfRange(0, 32).reversedArray()
                val s = sig.copyOfRange(32, 64).reversedArray()
                resp.r = r.toHexString()
                resp.s = s.toHexString()
                resp.derSignature = DklsHelper.createDERSignature(r, s).toHexString()
                val keySignVerify = KeysignVerify(mediatorURL, sessionID, sessionApi)
                keySignVerify.markLocalPartyKeysignComplete(msgHash, resp)
                signatures[messageToSign] = resp
            }
        } catch (e: Exception) {
            println("Failed to sign message ($messageToSign), error: ${e.localizedMessage}")
            if (attempt < 3) {
                keysignOneMessageWithRetry(attempt + 1, messageToSign)
            }
        }
    }

    @Throws(Exception::class)
    fun signSessionFinish(handle: Handle): ByteArray {
        val buf = tss_buffer()
        try {
            val result = schnorr_sign_session_finish(handle, buf)
            if (result != LIB_OK) {
                throw RuntimeException("fail to get keysign signature $result")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            tss_buffer_free(buf)
        }
    }

    suspend fun keysignWithRetry(attempt: Int) {
        for (msg in messageToSign) {
            keysignOneMessageWithRetry(0, msg)
        }
    }

    private fun ByteArray.toGoSlice(): go_slice {
        val slice = go_slice()
        BufferUtilJNI.set_bytes_on_go_slice(slice, this)
        return slice
    }
}