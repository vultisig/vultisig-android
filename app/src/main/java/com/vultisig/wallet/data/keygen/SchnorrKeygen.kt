@file:OptIn(ExperimentalEncodingApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.keygen

import com.silencelaboratories.goschnorr.BufferUtilJNI
import com.silencelaboratories.goschnorr.Handle
import com.silencelaboratories.goschnorr.go_slice
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keygen_session_finish
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keygen_session_from_setup
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keygen_session_input_message
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keygen_session_message_receiver
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keygen_session_output_message
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keyshare_public_key
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keyshare_to_bytes
import com.silencelaboratories.goschnorr.goschnorr.tss_buffer_free
import com.silencelaboratories.goschnorr.lib_error
import com.silencelaboratories.goschnorr.lib_error.LIB_OK
import com.silencelaboratories.goschnorr.tss_buffer
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class SchnorrKeygen(
    val vault: Vault,
    val keygenCommittee: List<String>,
    val mediatorURL: String,
    val sessionID: String,
    val encryptionKeyHex: String,
    val setupMessage: ByteArray,

    private val encryption: Encryption,
    private val sessionApi: SessionApi,
) {
    var messenger: TssMessenger =
        TssMessenger(
            serverAddress = mediatorURL,
            sessionID = sessionID,
            encryptionHex = encryptionKeyHex,
            sessionApi = sessionApi,
            coroutineScope = CoroutineScope(Dispatchers.IO),
            encryption = encryption,
            isEncryptionGCM = true
        )

    var keygenDoneIndicator = false
    val keyGenLock = ReentrantLock()
    val localPartyID: String = vault.localPartyID
    val cache = mutableMapOf<String, Any>()
    var keyshare: DKLSKeyshare? = null

    fun getSchnorrOutboundMessage(handle: Handle): Pair<lib_error, ByteArray> {
        val buf = tss_buffer()
        return try {
            val result = schnorr_keygen_session_output_message(handle, buf)
            if (result != LIB_OK) {
                Timber.d("fail to get outbound message: $result")
                return Pair(result, byteArrayOf())
            }

            Pair(result, BufferUtilJNI.get_bytes_from_tss_buffer(buf))
        } finally {
            tss_buffer_free(buf)
        }
    }

    fun isKeygenDone(): Boolean {
        return keyGenLock.withLock {
            keygenDoneIndicator
        }
    }

    fun setKeygenDone(status: Boolean) {
        keyGenLock.withLock {
            keygenDoneIndicator = status
        }
    }

    fun getOutboundMessageReceiver(handle: Handle, message: go_slice, idx: Long): ByteArray {
        val bufReceiver = tss_buffer()
        return try {
            val receiverResult = schnorr_keygen_session_message_receiver(handle, message, idx, bufReceiver)
            if (receiverResult != LIB_OK) {
                Timber.d("fail to get receiver message, error: $receiverResult")
                return byteArrayOf()
            }
            BufferUtilJNI.get_bytes_from_tss_buffer(bufReceiver)
        } finally {
            tss_buffer_free(bufReceiver)
        }
    }

    suspend fun processSchnorrOutboundMessage(handle: Handle) {
        while (true) {
            val (result, outboundMessage) = getSchnorrOutboundMessage(handle)
            if (result != LIB_OK) {
                Timber.d("fail to get outbound message")
            }
            if (outboundMessage.isEmpty()) {
                if (isKeygenDone()) {
                    Timber.d("DKLS ECDSA keygen finished")
                    return
                }
                delay(100)
                continue
            }

            val message = outboundMessage.toGoSlice()
            val encodedOutboundMessage = Base64.encode(outboundMessage)
            for (i in keygenCommittee.indices) {
                val receiverArray = getOutboundMessageReceiver(handle, message, i.toLong())
                if (receiverArray.isEmpty()) {
                    break
                }
                val receiverString = String(receiverArray, Charsets.UTF_8)
                Timber.d("sending message from $localPartyID to: $receiverString")
                messenger.send(localPartyID, receiverString, encodedOutboundMessage)
            }
        }
    }

    suspend fun pullInboundMessages(handle: Handle): Boolean {
        Timber.d("start pulling inbound messages")

        val start = System.nanoTime()
        while (true) {
            try {
                val msgs = sessionApi
                    .getTssMessages(mediatorURL, sessionID, localPartyID)

                if (msgs.isNotEmpty()) {
                    if (processInboundMessage(handle, msgs)) {
                        return true
                    }
                } else {
                    delay(1000)
                }
            } catch (e: Exception) {
                Timber.e("Failed to get messages", e)
            }

            val elapsedTime = (System.nanoTime() - start) / 1_000_000_000.0
            if (elapsedTime > 60) {
                error("timeout: failed to create vault within 60 seconds")
            }
        }

        return false
    }

    suspend fun processInboundMessage(handle: Handle, msgs: List<Message>): Boolean {
        if (msgs.isEmpty()) {
            return false
        }
        val sortedMsgs = msgs.sortedBy { it.sequenceNo }
        for (msg in sortedMsgs) {
            val key = "$sessionID-$localPartyID-${msg.hash}"
            if (cache[key] != null) {
                Timber.d("message with key: $key has been applied before")
                continue
            }
            Timber.d("Got message from: ${msg.from}, to: ${msg.to}, key: $key")
            val decryptedBody = encryption.decrypt(
                Base64.Default.decode(msg.body),
                Numeric.hexStringToByteArray(encryptionKeyHex)
            ) ?: error("fail to decrypt message body")
            val decodedMsg = Base64.Default.decode(decryptedBody)

            val decryptedBodySlice = decodedMsg.toGoSlice()

            var isFinished = intArrayOf(0)
            val result = schnorr_keygen_session_input_message(handle, decryptedBodySlice, isFinished)
            if (result != LIB_OK) {
                error("fail to apply message to schnorr, $result")
            }
            cache.put(key, Any())
            deleteMessageFromServer(msg.hash)
            if (isFinished[0] != 0) {
                return true
            }
        }
        return false
    }

    suspend fun deleteMessageFromServer(hash: String) {
        sessionApi.deleteTssMessage(mediatorURL, sessionID, localPartyID, hash, null)
    }

    suspend fun schnorrKeygenWithRetry(attempt: Int) {
        setKeygenDone(false)
        var task: Job? = null
        try {
            val decodedSetupMsg = setupMessage.toGoSlice()
            val handler = Handle()
            val localPartyIDArr = localPartyID.toByteArray()
            val localPartySlice = localPartyIDArr.toGoSlice()
            val result = schnorr_keygen_session_from_setup(decodedSetupMsg, localPartySlice, handler)
            if (result != LIB_OK) {
                error("fail to create session from setup message, error: $result")
            }
            task = CoroutineScope(Dispatchers.IO).launch {
                processSchnorrOutboundMessage(handler)
            }
            val isFinished = pullInboundMessages(handler)
            if (isFinished) {
                setKeygenDone(true)
                task.cancel()
                val keyshareHandler = Handle()
                val keyShareResult = schnorr_keygen_session_finish(handler, keyshareHandler)
                if (keyShareResult != LIB_OK) {
                    error("fail to get keyshare, $keyShareResult")
                }
                val keyshareBytes = getKeyshareBytes(keyshareHandler)
                val publicKeyEdDSA = getPublicKeyBytes(keyshareHandler)
                keyshare = DKLSKeyshare(
                    pubKey = publicKeyEdDSA.toHexString(),
                    keyshare = Base64.encode(keyshareBytes),
                    chaincode = ""
                )
                Timber.d("publicKeyEdDSA: ${publicKeyEdDSA.toHexString()}")
            }
        } catch (e: Exception) {
            Timber.d("Failed to generate key, error: ${e.localizedMessage}")
            setKeygenDone(true)
            task?.cancel()
            if (attempt < 3) {
                Timber.d("keygen/reshare retry, attempt: $attempt")
                schnorrKeygenWithRetry(attempt + 1)
            } else {
                throw e
            }
        }
    }

    fun getKeyshareBytes(handle: Handle): ByteArray {
        val buf = tss_buffer()
        return try {
            val result = schnorr_keyshare_to_bytes(handle, buf)
            if (result != LIB_OK) {
                error("fail to get keyshare from handler, $result")
            }
            BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            tss_buffer_free(buf)
        }
    }

    fun getPublicKeyBytes(handle: Handle): ByteArray {
        val buf = tss_buffer()
        return try {
            val result = schnorr_keyshare_public_key(handle, buf)
            if (result != LIB_OK) {
                error("fail to get ECDSA public key from handler, $result")
            }
            BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            tss_buffer_free(buf)
        }
    }

    private fun ByteArray.toGoSlice(): go_slice {
        val slice = go_slice()
        BufferUtilJNI.set_bytes_on_go_slice(slice, this)
        return slice
    }

}