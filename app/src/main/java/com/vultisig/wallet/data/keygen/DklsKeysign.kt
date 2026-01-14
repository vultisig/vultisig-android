@file:OptIn(ExperimentalEncodingApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.keygen

import com.silencelaboratories.godkls.BufferUtilJNI
import com.silencelaboratories.godkls.Handle
import com.silencelaboratories.godkls.go_slice
import com.silencelaboratories.godkls.godkls.dkls_decode_message
import com.silencelaboratories.godkls.godkls.dkls_keyshare_from_bytes
import com.silencelaboratories.godkls.godkls.dkls_keyshare_key_id
import com.silencelaboratories.godkls.godkls.dkls_sign_session_finish
import com.silencelaboratories.godkls.godkls.dkls_sign_session_from_setup
import com.silencelaboratories.godkls.godkls.dkls_sign_session_input_message
import com.silencelaboratories.godkls.godkls.dkls_sign_session_message_receiver
import com.silencelaboratories.godkls.godkls.dkls_sign_session_output_message
import com.silencelaboratories.godkls.godkls.dkls_sign_setupmsg_new
import com.silencelaboratories.godkls.godkls.tss_buffer_free
import com.silencelaboratories.godkls.lib_error
import com.silencelaboratories.godkls.lib_error.LIB_OK
import com.silencelaboratories.godkls.tss_buffer
import com.vultisig.wallet.data.api.KeysignVerify
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import timber.log.Timber
import tss.KeysignResponse
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class DKLSKeysign(
    val keysignCommittee: List<String>,
    val mediatorURL: String,
    val sessionID: String,
    val messageToSign: List<String>,
    val vault: Vault,
    val encryptionKeyHex: String,
    val chainPath: String,
    val isInitiateDevice: Boolean,

    private val sessionApi: SessionApi,
    private val encryption: Encryption,
) {
    val localPartyID: String = vault.localPartyID
    private val publicKeyECDSA: String = vault.pubKeyECDSA
    private var messenger: TssMessenger =
        TssMessenger(
            serverAddress = mediatorURL,
            sessionID = sessionID,
            encryptionHex = encryptionKeyHex,
            sessionApi = sessionApi,
            coroutineScope = CoroutineScope(Dispatchers.IO),
            encryption = encryption,
            isEncryptionGCM = true
        )
    private val cache = mutableMapOf<String, Any>()
    val signatures = mutableMapOf<String, KeysignResponse>()
    private fun getKeyshareString(): String? {
        for (ks in vault.keyshares) {
            if (ks.pubKey == publicKeyECDSA) {
                return ks.keyShare
            }
        }
        return null
    }

    @Throws(Exception::class)
    private fun getKeyshareBytes(): ByteArray {
        val localKeyshare = getKeyshareString() ?: error("fail to get local keyshare")
        val keyshareData = Base64.decode(localKeyshare)
        return keyshareData
    }

    @Throws(Exception::class)
    private fun getDKLSKeyshareID(): ByteArray {
        val buf = tss_buffer()
        try {
            val keyShareBytes = getKeyshareBytes()
            val keyshareSlice = keyShareBytes.toGoSlice()
            val h = Handle()
            val result = dkls_keyshare_from_bytes(keyshareSlice, h)
            if (result != LIB_OK) {
                error("fail to create keyshare handle from bytes, $result")
            }
            val keyIDResult = dkls_keyshare_key_id(h, buf)
            if (keyIDResult != LIB_OK) {
                error("fail to get key id from keyshare: $keyIDResult")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            tss_buffer_free(buf)
        }
    }

    @Throws(Exception::class)
    private fun getDKLSKeysignSetupMessage(message: String): ByteArray {
        val buf = tss_buffer()
        try {
            val keyIdArr = getDKLSKeyshareID()
            val keyIdSlice = keyIdArr.toGoSlice()
            val byteArray = DklsHelper.arrayToBytes(keysignCommittee)
            val ids = byteArray.toGoSlice()
            var chainPathSlice: go_slice?
            when (vault.libType) {
                SigningLibType.DKLS -> {
                    val chainPathArr = chainPath.replace("'", "").toByteArray(Charsets.UTF_8)
                    chainPathSlice = chainPathArr.toGoSlice()
                }

                SigningLibType.KeyImport -> {
                    chainPathSlice = null
                }

                else -> {
                    error("unsupported lib type for DKLS keysign: ${vault.libType}")
                }
            }


            val decodedMsgData = message.hexToByteArray()
            val msgSlice = decodedMsgData.toGoSlice()
            val err = dkls_sign_setupmsg_new(keyIdSlice, chainPathSlice, msgSlice, ids, buf)
            if (err != LIB_OK) {
                error("fail to setup keysign message, dkls error: $err")
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
            val result = dkls_decode_message(setupMsgSlice, buf)
            if (result != LIB_OK) {
                error("fail to extract message from setup message: $result")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf).toHexString()
        } finally {
            tss_buffer_free(buf)
        }
    }

    private fun getOutboundMessageReceiver(
        handle: Handle,
        message: go_slice,
        idx: Long
    ): ByteArray {
        val bufReceiver = tss_buffer()
        try {
            val receiverResult =
                dkls_sign_session_message_receiver(handle, message, idx, bufReceiver)
            if (receiverResult != LIB_OK) {
                println("fail to get receiver message, error: $receiverResult")
                return byteArrayOf()
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(bufReceiver)
        } finally {
            tss_buffer_free(bufReceiver)
        }
    }

    private fun getDKLSOutboundMessage(handle: Handle): Pair<lib_error, ByteArray> {
        val buf = tss_buffer()
        try {
            val result = dkls_sign_session_output_message(handle, buf)
            if (result != LIB_OK) {
                println("fail to get outbound message: $result")
                return Pair(result, byteArrayOf())
            }
            return Pair(result, BufferUtilJNI.get_bytes_from_tss_buffer(buf))
        } finally {
            tss_buffer_free(buf)
        }
    }

    private fun processDKLSOutboundMessage(handle: Handle) {
        while (true) {
            val (result, outboundMessage) = getDKLSOutboundMessage(handle)
            if (result != LIB_OK) {
                println("fail to get outbound message, $result")
            }
            if (outboundMessage.isEmpty()) {
                return
            }
            val message = outboundMessage.toGoSlice()
            val encodedOutboundMessage = Base64.encode(outboundMessage)
            for (i in keysignCommittee.indices) {
                val receiverArray = getOutboundMessageReceiver(handle, message, i.toLong())
                if (receiverArray.isEmpty()) {
                    break
                }
                val receiverString = String(receiverArray, Charsets.UTF_8)
                println("sending message from $localPartyID to: $receiverString, content length: ${encodedOutboundMessage.length}")
                messenger.send(localPartyID, receiverString, encodedOutboundMessage)
            }
        }
    }

    private suspend fun pullInboundMessages(handle: Handle, messageID: String): Boolean {
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
            }

            val elapsedTime = (System.nanoTime() - start) / 1_000_000_000.0
            if (elapsedTime > 60) {
                error("timeout: failed to create vault within 60 seconds")
            }
        }
    }

    private suspend fun processInboundMessage(
        handle: Handle,
        msgs: List<Message>,
        messageID: String
    ): Boolean {
        val sortedMsgs = msgs.sortedBy { it.sequenceNo }
        for (msg in sortedMsgs) {
            val key = "$sessionID-$localPartyID-$messageID-${msg.hash}"
            if (cache[key] != null) {
                println("message with key: $key has been applied before")
                continue
            }
            println("Got message from: ${msg.from}, to: ${msg.to}, key: $key")
            val decryptedBody = encryption.decrypt(
                Base64.decode(msg.body),
                Numeric.hexStringToByteArray(encryptionKeyHex)
            ) ?: error("fail to decrypt message body")
            val decodedMsg = Base64.decode(decryptedBody)
            val decryptedBodySlice = decodedMsg.toGoSlice()
            val isFinished = intArrayOf(0)
            val result = dkls_sign_session_input_message(handle, decryptedBodySlice, isFinished)
            if (result != LIB_OK) {
                error("fail to apply message to dkls, $result")
            }
            cache[key] = Any()
            deleteMessageFromServer(msg.hash, messageID)
            processDKLSOutboundMessage(handle)
            if (isFinished[0] != 0) {
                return true
            }
        }
        return false
    }

    private suspend fun deleteMessageFromServer(hash: String, messageID: String) {
        sessionApi.deleteTssMessage(mediatorURL, sessionID, localPartyID, hash, messageID)
    }

    private suspend fun keysignOneMessageWithRetry(attempt: Int, messageToSign: String) {
        val msgHash = messageToSign.md5()
        val localMessenger = TssMessenger(
            mediatorURL, sessionID, encryptionKeyHex,
            sessionApi, CoroutineScope(Dispatchers.IO), encryption, true
        )
        localMessenger.setMessageID(msgHash)
        messenger = localMessenger
        try {
            val keysignSetupMsg: ByteArray

            if (isInitiateDevice && attempt == 0) {
                keysignSetupMsg = getDKLSKeysignSetupMessage(messageToSign)

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
                keysignSetupMsg = sessionApi.getSetupMessage(mediatorURL, sessionID, msgHash)
                    .let {
                        encryption.decrypt(
                            Base64.decode(it),
                            Numeric.hexStringToByteArray(encryptionKeyHex)
                        )!!
                    }.let {
                        Base64.decode(it)
                    }
            }

            val signingMsg = decodeMessage(keysignSetupMsg)
            if (signingMsg != messageToSign) {
                error("message doesn't match ($messageToSign) vs ($signingMsg)")
            }
            val decodedSetupMsg = keysignSetupMsg.toGoSlice()
            val handler = Handle()
            val localPartyIDArr = localPartyID.toByteArray()
            val localPartySlice = localPartyIDArr.toGoSlice()
            val keyShareBytes = getKeyshareBytes()
            val keyshareSlice = keyShareBytes.toGoSlice()
            val keyshareHandle = Handle()
            val result = dkls_keyshare_from_bytes(keyshareSlice, keyshareHandle)
            if (result != LIB_OK) {
                error("fail to create keyshare handle from bytes, $result")
            }
            val sessionResult = dkls_sign_session_from_setup(
                decodedSetupMsg,
                localPartySlice,
                keyshareHandle,
                handler
            )
            if (sessionResult != LIB_OK) {
                error("fail to create sign session from setup message, error: $sessionResult")
            }
            processDKLSOutboundMessage(handler)
            val isFinished = pullInboundMessages(handler, msgHash)
            if (isFinished) {
                val sig = dklsSignSessionFinish(handler)
                val resp = KeysignResponse()
                resp.msg = messageToSign
                val r = sig.copyOfRange(0, 32)
                val s = sig.copyOfRange(32, 64)
                resp.r = r.toHexString()
                resp.s = s.toHexString()
                resp.recoveryID = String.format("%02x", sig[64])
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
    private fun dklsSignSessionFinish(handle: Handle): ByteArray {
        val buf = tss_buffer()
        try {
            val result = dkls_sign_session_finish(handle, buf)
            if (result != LIB_OK) {
                error("fail to get keysign signature $result")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            tss_buffer_free(buf)
        }
    }

    suspend fun keysignWithRetry() {
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