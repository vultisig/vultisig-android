@file:OptIn(ExperimentalEncodingApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.keygen

import com.silencelaboratories.godkls.BufferUtilJNI
import com.silencelaboratories.godkls.Handle
import com.silencelaboratories.godkls.go_slice
import com.silencelaboratories.godkls.godkls
import com.silencelaboratories.godkls.godkls.dkls_keygen_session_finish
import com.silencelaboratories.godkls.godkls.dkls_keygen_session_from_setup
import com.silencelaboratories.godkls.godkls.dkls_keygen_session_input_message
import com.silencelaboratories.godkls.godkls.dkls_keygen_session_message_receiver
import com.silencelaboratories.godkls.godkls.dkls_keygen_session_output_message
import com.silencelaboratories.godkls.godkls.dkls_keygen_setupmsg_new
import com.silencelaboratories.godkls.godkls.dkls_keyshare_chaincode
import com.silencelaboratories.godkls.godkls.dkls_keyshare_free
import com.silencelaboratories.godkls.godkls.dkls_keyshare_public_key
import com.silencelaboratories.godkls.godkls.dkls_keyshare_to_bytes
import com.silencelaboratories.godkls.lib_error
import com.silencelaboratories.godkls.lib_error.LIB_OK
import com.silencelaboratories.godkls.tss_buffer
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
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class DKLSKeyshare(
    val pubKey: String,
    val keyshare: String,
    val chaincode: String
)

class DKLSKeygen(
    val vault: Vault,
    val keygenCommittee: List<String>,
    val mediatorURL: String,
    val sessionID: String,
    val encryptionKeyHex: String,
    val isInitiateDevice: Boolean,

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
    val keyGenLock = Mutex()
    val localPartyID: String = vault.localPartyID
    val cache = mutableMapOf<String, Any>()
    var setupMessage: ByteArray = byteArrayOf()
    var keyshare: DKLSKeyshare? = null

    @Throws(Exception::class)
    private fun getDklsSetupMessage(): ByteArray {
        val buf = tss_buffer()
        try {
            val threshold = DklsHelper.getThreshold(keygenCommittee.size)
            val byteArray = DklsHelper.arrayToBytes(keygenCommittee)
            val ids = go_slice()
            BufferUtilJNI.set_bytes_on_go_slice(ids, byteArray)
            val err = dkls_keygen_setupmsg_new(threshold, null, ids, buf)
            if (err != LIB_OK) {
                error("fail to setup keygen message, dkls error: $err")
            }
            setupMessage = BufferUtilJNI.get_bytes_from_tss_buffer(buf)
            return setupMessage
        } finally {
            godkls.tss_buffer_free(buf)
        }
    }

    fun getDKLSOutboundMessage(handle: Handle): Pair<lib_error, ByteArray> {
        val buf = tss_buffer()
        try {
            val result = dkls_keygen_session_output_message(handle, buf)
            if (result != LIB_OK) {
                Timber.d("fail to get outbound message: $result")
                return Pair(result, byteArrayOf())
            }
            return Pair(result, BufferUtilJNI.get_bytes_from_tss_buffer(buf))
        } finally {
            godkls.tss_buffer_free(buf)
        }
    }

    suspend fun isKeygenDone(): Boolean {
        keyGenLock.lock()
        return try {
            keygenDoneIndicator
        } finally {
            keyGenLock.unlock()
        }
    }

    suspend fun setKeygenDone(status: Boolean) {
        keyGenLock.lock()
        try {
            keygenDoneIndicator = status
        } finally {
            keyGenLock.unlock()
        }
    }

    fun getOutboundMessageReceiver(handle: Handle, message: go_slice, idx: Long): ByteArray {
        val bufReceiver = tss_buffer()
        try {
            val receiverResult =
                dkls_keygen_session_message_receiver(handle, message, idx, bufReceiver)
            if (receiverResult != LIB_OK) {
                Timber.d("fail to get receiver message, error: $receiverResult")
                return byteArrayOf()
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(bufReceiver)
        } finally {
            godkls.tss_buffer_free(bufReceiver)
        }
    }

    @Throws(Exception::class)
    suspend fun processDKLSOutboundMessage(handle: Handle) {

        while (true) {
            val (result, outboundMessage) = getDKLSOutboundMessage(handle)
            if (result != LIB_OK) {
                Timber.d("fail to get outbound message, $result")
            }
            if (outboundMessage.isEmpty()) {
                if (isKeygenDone()) {
                    Timber.d("DKLS ECDSA keygen finished")
                    return
                }
                delay(100)
                continue
            }

            val message = go_slice()
            BufferUtilJNI.set_bytes_on_go_slice(message, outboundMessage)
            val encodedOutboundMessage = Base64.Default.encode(outboundMessage)
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

    @Throws(Exception::class)
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
                    delay(100)
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

    @Throws(Exception::class)
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

            val isFinished = intArrayOf(0)

            val result = dkls_keygen_session_input_message(handle, decryptedBodySlice, isFinished)
            if (result != LIB_OK) {
                error("fail to apply message to dkls, $result")
            }
            cache.put(key, Any())
            deleteMessageFromServer(msg.hash)

            if (isFinished[0] != 0) {
                return true
            }
        }
        return false
    }

    @Throws(Exception::class)
    suspend fun deleteMessageFromServer(hash: String) {
        sessionApi.deleteTssMessage(mediatorURL, sessionID, localPartyID, hash, null)
    }

    @Throws(Exception::class)
    suspend fun dklsKeygenWithRetry(attempt: Int) {
        setKeygenDone(false)
        var task: Job? = null
        try {
            val keygenSetupMsg: ByteArray

            if (isInitiateDevice) {
                keygenSetupMsg = getDklsSetupMessage()

                sessionApi.uploadSetupMessage(
                    serverUrl = mediatorURL,
                    sessionId = sessionID,
                    message = Base64.encode(
                        encryption.encrypt(
                            Base64.encodeToByteArray(keygenSetupMsg),
                            Numeric.hexStringToByteArray(encryptionKeyHex)
                        )
                    )
                )
            } else {
                keygenSetupMsg = sessionApi.getSetupMessage(mediatorURL, sessionID)
                    .let {
                        encryption.decrypt(
                            Base64.Default.decode(it),
                            Numeric.hexStringToByteArray(encryptionKeyHex)
                        )!!
                    }.let {
                        Base64.decode(it)
                    }
            }

            setupMessage = keygenSetupMsg

            val handler = Handle()

            val decodedSetupMsg = keygenSetupMsg.toGoSlice()
            val localPartyIDArr = localPartyID.toByteArray()
            val localPartySlice = localPartyIDArr.toGoSlice()

            val result = dkls_keygen_session_from_setup(decodedSetupMsg, localPartySlice, handler)
            if (result != LIB_OK) {
                error("fail to create session from setup message, error: $result")
            }
            task = CoroutineScope(Dispatchers.IO).launch {
                processDKLSOutboundMessage(handler)
            }
            val isFinished = pullInboundMessages(handler)
            if (isFinished) {
                setKeygenDone(true)
                task.cancel()
                val keyshareHandler = Handle()
                val keyShareResult = dkls_keygen_session_finish(handler, keyshareHandler)
                if (keyShareResult != LIB_OK) {
                    error("fail to get keyshare, $keyShareResult")
                }
                val keyshareBytes = getKeyshareBytes(keyshareHandler)
                val publicKeyECDSA = getPublicKeyBytes(keyshareHandler)
                val chainCodeBytes = getChainCode(keyshareHandler)
                keyshare = DKLSKeyshare(
                    pubKey = publicKeyECDSA.toHexString(),
                    keyshare = Base64.Default.encode(keyshareBytes),
                    chaincode = chainCodeBytes.toHexString()
                )
                Timber.d("publicKeyECDSA: ${publicKeyECDSA.toHexString()}")
                Timber.d("chaincode: ${chainCodeBytes.toHexString()}")
                dkls_keyshare_free(keyshareHandler)
            }
        } catch (e: Exception) {
            Timber.d("Failed to generate key, error: ${e.localizedMessage}")
            setKeygenDone(true)
            task?.cancel()
            if (attempt < 3) {
                Timber.d("keygen/reshare retry, attempt: $attempt")
                dklsKeygenWithRetry(attempt + 1)
            } else {
                throw e
            }
        }
    }

    fun getKeyshareBytes(handle: Handle): ByteArray =
        executeIntoBytes(
            block = { dkls_keyshare_to_bytes(handle, it) },
            errorMessage = { "fail to get keyshare from handler" }
        )

    fun getPublicKeyBytes(handle: Handle): ByteArray =
        executeIntoBytes(
            block = { dkls_keyshare_public_key(handle, it) },
            errorMessage = { "fail to get ECDSA public key from handler" }
        )

    fun getChainCode(handle: Handle): ByteArray = executeIntoBytes(
        block = { dkls_keyshare_chaincode(handle, it) },
        errorMessage = { "fail to get ECDSA chaincode from handler" }
    )

    private inline fun executeIntoBytes(
        block: (tss_buffer) -> lib_error,
        errorMessage: () -> String,
    ): ByteArray {
        val buf = tss_buffer()
        try {
            val result = block(buf)
            if (result != LIB_OK) {
                error("${errorMessage.invoke()}, $result")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            godkls.tss_buffer_free(buf)
        }
    }

    private fun ByteArray.toGoSlice(): go_slice {
        val slice = go_slice()
        BufferUtilJNI.set_bytes_on_go_slice(slice, this)
        return slice
    }

}