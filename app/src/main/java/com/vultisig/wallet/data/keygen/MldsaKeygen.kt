@file:OptIn(ExperimentalEncodingApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.keygen

import com.silencelaboratories.godilithium.BufferUtilJNI
import com.silencelaboratories.godilithium.Handle
import com.silencelaboratories.godilithium.MldsaSecurityLevel
import com.silencelaboratories.godilithium.go_slice
import com.silencelaboratories.godilithium.godilithium.mldsa_keygen_session_finish
import com.silencelaboratories.godilithium.godilithium.mldsa_keygen_session_free
import com.silencelaboratories.godilithium.godilithium.mldsa_keygen_session_from_setup
import com.silencelaboratories.godilithium.godilithium.mldsa_keygen_session_input_message
import com.silencelaboratories.godilithium.godilithium.mldsa_keygen_session_message_receiver
import com.silencelaboratories.godilithium.godilithium.mldsa_keygen_session_output_message
import com.silencelaboratories.godilithium.godilithium.mldsa_keygen_setupmsg_new
import com.silencelaboratories.godilithium.godilithium.mldsa_keyshare_free
import com.silencelaboratories.godilithium.godilithium.mldsa_keyshare_public_key
import com.silencelaboratories.godilithium.godilithium.mldsa_keyshare_to_bytes
import com.silencelaboratories.godilithium.godilithium.tss_buffer_free
import com.silencelaboratories.godilithium.mldsa_error
import com.silencelaboratories.godilithium.mldsa_error.LIB_OK
import com.silencelaboratories.godilithium.tss_buffer
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class MldsaKeyshare(
    val pubKey: String,
    val keyshare: String
)

class MldsaKeygen(
    val localPartyId: String,
    val keygenCommittee: List<String>,
    val mediatorURL: String,
    val sessionID: String,
    val encryptionKeyHex: String,
    val isInitiateDevice: Boolean,

    private val encryption: Encryption,
    private val sessionApi: SessionApi,
) {
    private val messenger: TssMessenger = TssMessenger(
        serverAddress = mediatorURL,
        sessionID = sessionID,
        encryptionHex = encryptionKeyHex,
        sessionApi = sessionApi,
        coroutineScope = CoroutineScope(Dispatchers.IO),
        encryption = encryption,
        isEncryptionGCM = true
    )

    private val cache = mutableMapOf<String, Any>()
    var setupMessage: ByteArray = byteArrayOf()
    var keyshare: MldsaKeyshare? = null

    @Throws(Exception::class)
    private fun getMldsaSetupMessage(): ByteArray {
        val buf = tss_buffer()
        try {
            val threshold = DklsHelper.getThreshold(keygenCommittee.size)
            val byteArray = DklsHelper.arrayToBytes(keygenCommittee)
            val ids = go_slice()
            BufferUtilJNI.set_bytes_on_go_slice(ids, byteArray)
            val err = mldsa_keygen_setupmsg_new(
                MldsaSecurityLevel.MlDsa44,
                threshold,
                null,
                ids,
                buf
            )
            if (err != LIB_OK) {
                error("fail to setup mldsa keygen message, error: $err")
            }
            setupMessage = BufferUtilJNI.get_bytes_from_tss_buffer(buf)
            return setupMessage
        } finally {
            tss_buffer_free(buf)
        }
    }

    private fun getMldsaOutboundMessage(handle: Handle): Pair<mldsa_error, ByteArray> {
        val buf = tss_buffer()
        try {
            val result = mldsa_keygen_session_output_message(handle, buf)
            if (result != LIB_OK) {
                Timber.d("fail to get outbound message: $result")
                return Pair(result, byteArrayOf())
            }
            return Pair(result, BufferUtilJNI.get_bytes_from_tss_buffer(buf))
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
            val receiverResult = mldsa_keygen_session_message_receiver(
                handle,
                message,
                idx,
                bufReceiver
            )
            if (receiverResult != LIB_OK) {
                Timber.d("fail to get receiver message, error: $receiverResult")
                return byteArrayOf()
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(bufReceiver)
        } finally {
            tss_buffer_free(bufReceiver)
        }
    }

    @Throws(Exception::class)
    private fun processMldsaOutboundMessage(handle: Handle) {
        while (true) {
            val (result, outboundMessage) = getMldsaOutboundMessage(handle)
            if (result != LIB_OK) {
                Timber.d("fail to get outbound message, $result")
            }
            if (outboundMessage.isEmpty()) {
                return
            }

            val message = outboundMessage.toGoSlice()
            val encodedOutboundMessage = Base64.encode(outboundMessage)
            for (i in keygenCommittee.indices) {
                val receiverArray = getOutboundMessageReceiver(handle, message, i.toLong())
                if (receiverArray.isEmpty()) {
                    break
                }
                val receiverString = receiverArray.toString(Charsets.UTF_8)
                Timber.d("sending message from $localPartyId to: $receiverString")
                messenger.send(localPartyId, receiverString, encodedOutboundMessage)
            }
        }
    }

    @Throws(Exception::class)
    private suspend fun pullInboundMessages(handle: Handle): Boolean {
        Timber.d("start pulling inbound messages for MLDSA keygen")

        val start = System.nanoTime()
        while (true) {
            try {
                val msgs = sessionApi.getTssMessages(mediatorURL, sessionID, localPartyId)

                if (msgs.isNotEmpty()) {
                    if (processInboundMessage(handle, msgs)) {
                        return true
                    }
                } else {
                    delay(100)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to get messages")
                delay(1000) // backoff delay
            }

            val elapsedTime = (System.nanoTime() - start) / 1_000_000_000.0
            if (elapsedTime > 60) {
                error("timeout: MLDSA keygen did not finish within 60 seconds")
            }
        }
    }

    @Throws(Exception::class)
    private suspend fun processInboundMessage(handle: Handle, msgs: List<Message>): Boolean {
        if (msgs.isEmpty()) {
            return false
        }
        val sortedMsgs = msgs.sortedBy { it.sequenceNo }
        for (msg in sortedMsgs) {
            val key = "$sessionID-$localPartyId-${msg.hash}"
            if (cache[key] != null) {
                Timber.d("message with key: $key has been applied before")
                continue
            }
            Timber.d("Got message from: ${msg.from}, to: ${msg.to}, key: $key")
            val decryptedBody = encryption.decrypt(
                Base64.decode(msg.body),
                Numeric.hexStringToByteArray(encryptionKeyHex)
            ) ?: error("fail to decrypt message body")
            val decodedMsg = Base64.decode(decryptedBody)

            val decryptedBodySlice = decodedMsg.toGoSlice()

            val isFinished = intArrayOf(0)
            val result = mldsa_keygen_session_input_message(
                handle,
                decryptedBodySlice,
                isFinished
            )

            if (result != LIB_OK) {
                error("fail to apply message to mldsa, $result")
            }
            cache[key] = Any()
            deleteMessageFromServer(msg.hash)
            processMldsaOutboundMessage(handle)

            if (isFinished[0] != 0) {
                return true
            }
        }
        return false
    }

    @Throws(Exception::class)
    private suspend fun deleteMessageFromServer(hash: String) {
        sessionApi.deleteTssMessage(mediatorURL, sessionID, localPartyId, hash, null)
    }

    @Throws(Exception::class)
    suspend fun mldsaKeygenWithRetry(attempt: Int) {
        try {
            val handler = Handle()
            try {
                val keygenSetupMsg: ByteArray
                if (isInitiateDevice && attempt == 0) {
                    keygenSetupMsg = getMldsaSetupMessage()
                    sessionApi.uploadSetupMessage(
                        serverUrl = mediatorURL,
                        sessionId = sessionID,
                        message = Base64.encode(
                            encryption.encrypt(
                                Base64.encodeToByteArray(keygenSetupMsg),
                                Numeric.hexStringToByteArray(encryptionKeyHex)
                            )
                        ),
                        messageId = "mldsa",
                    )
                } else {
                    keygenSetupMsg = sessionApi.getSetupMessage(mediatorURL, sessionID, "mldsa")
                        .let {
                            encryption.decrypt(
                                Base64.decode(it),
                                Numeric.hexStringToByteArray(encryptionKeyHex)
                            )!!
                        }.let {
                            Base64.decode(it)
                        }
                }

                setupMessage = keygenSetupMsg
                val decodedSetupMsg = keygenSetupMsg.toGoSlice()
                val localPartyIDArr = localPartyId.toByteArray()
                val localPartySlice = localPartyIDArr.toGoSlice()

                val result = mldsa_keygen_session_from_setup(
                    MldsaSecurityLevel.MlDsa44,
                    decodedSetupMsg,
                    localPartySlice,
                    handler
                )
                if (result != LIB_OK) {
                    error("fail to create mldsa session from setup message, error: $result")
                }

                processMldsaOutboundMessage(handler)
                val isFinished = pullInboundMessages(handler)
                if (isFinished) {
                    val keyshareHandler = Handle()
                    try {
                        val keyShareResult = mldsa_keygen_session_finish(handler, keyshareHandler)
                        if (keyShareResult != LIB_OK) {
                            error("fail to get mldsa keyshare, $keyShareResult")
                        }
                        val keyshareBytes = getKeyshareBytes(keyshareHandler)
                        val publicKey = getPublicKeyBytes(keyshareHandler)
                        keyshare = MldsaKeyshare(
                            pubKey = publicKey.toHexString(),
                            keyshare = Base64.encode(keyshareBytes)
                        )
                        Timber.d("MLDSA publicKey: ${publicKey.toHexString()}")
                    } finally {
                        mldsa_keyshare_free(keyshareHandler)
                        keyshareHandler.delete()
                    }
                }
            } finally {
                mldsa_keygen_session_free(handler)
                handler.delete()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e("Failed to generate MLDSA key, error: ${e.localizedMessage}")
            if (attempt < 3) {
                Timber.e("mldsa keygen retry, attempt: $attempt")
                mldsaKeygenWithRetry(attempt + 1)
            } else {
                throw e
            }
        }
    }

    private fun getKeyshareBytes(handle: Handle): ByteArray {
        val buf = tss_buffer()
        try {
            val result = mldsa_keyshare_to_bytes(handle, buf)
            if (result != LIB_OK) {
                error("fail to get mldsa keyshare from handler, $result")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            tss_buffer_free(buf)
        }
    }

    private fun getPublicKeyBytes(handle: Handle): ByteArray {
        val buf = tss_buffer()
        try {
            val result = mldsa_keyshare_public_key(handle, buf)
            if (result != LIB_OK) {
                error("fail to get MLDSA public key from handler, $result")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf)
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