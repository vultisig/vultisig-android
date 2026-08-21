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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import tss.KeysignResponse

/**
 * Thrown when `godkls` reports that it aborted the session and banned [partyID] for protocol-level
 * misbehavior (`LIB_ABORT_PROTOCOL_AND_BAN_PARTY_1..10`). Never retried.
 */
class MaliciousPartyException(val partyID: String) :
    Exception("party $partyID was banned for malicious behavior")

// LIB_ABORT_PROTOCOL_AND_BAN_PARTY_1...10 (godkls.h): the library aborted the session and
// banned a co-signer for protocol-level misbehavior. Hardcoded (not read off the lib_error
// enum) so this check stays testable on the host JVM without loading the native godkls lib.
private val BAN_PARTY_RANGE = 100..109

/**
 * Throws [MaliciousPartyException] if [resultValue] (a `lib_error.swigValue()`) is one of
 * `LIB_ABORT_PROTOCOL_AND_BAN_PARTY_1..10`. Party N (1-based) is `keysignCommittee[N-1]`: the setup
 * message embeds party ids in exactly this array order (see [DKLSKeysign]).
 */
internal fun checkForBannedParty(resultValue: Int, keysignCommittee: List<String>) {
    if (resultValue !in BAN_PARTY_RANGE) return
    val partyIndex = resultValue - BAN_PARTY_RANGE.first
    val partyID = keysignCommittee.getOrNull(partyIndex) ?: "#${partyIndex + 1}"
    throw MaliciousPartyException(partyID)
}

/**
 * Performs DKLS keysigning for one or more messages.
 *
 * @param onWaitingForPeers Invoked when no inbound messages have arrived for ~10 s; receives the
 *   silent peer IDs.
 * @param onPeersResumed Invoked when messages resume after [onWaitingForPeers] was called.
 */
class DKLSKeysign(
    val keysignCommittee: List<String>,
    val mediatorURL: String,
    val sessionID: String,
    val messageToSign: List<String>,
    val vault: Vault,
    val encryptionKeyHex: String,
    val chainPath: String,
    val isInitiateDevice: Boolean,
    val publicKeyOverride: String? = null,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    val onWaitingForPeers: ((List<String>) -> Unit)? = null,
    val onPeersResumed: (() -> Unit)? = null,
) {
    /** The local party ID derived from the vault. */
    val localPartyID: String = vault.localPartyID
    private val publicKeyECDSA: String = publicKeyOverride ?: vault.pubKeyECDSA
    private var messenger: TssMessenger =
        TssMessenger(
            serverAddress = mediatorURL,
            sessionID = sessionID,
            encryptionHex = encryptionKeyHex,
            sessionApi = sessionApi,
            coroutineScope = CoroutineScope(Dispatchers.IO),
            encryption = encryption,
            isEncryptionGCM = true,
        )
    private val cache = mutableMapOf<String, Any>()
    private val redeletedHashes = mutableSetOf<String>()

    /** Collects signatures keyed by the signed message hex string. */
    val signatures = mutableMapOf<String, KeysignResponse>()
    private val poller =
        KeysignMessagePoller(
            sessionApi = sessionApi,
            mediatorURL = mediatorURL,
            sessionID = sessionID,
            localPartyID = localPartyID,
            keysignCommittee = keysignCommittee,
            onWaitingForPeers = onWaitingForPeers,
            onPeersResumed = onPeersResumed,
        )

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
            val keyshareSlice = keyShareBytes.toDklsGoSlice()
            val h = Handle()
            val result =
                try {
                    dkls_keyshare_from_bytes(keyshareSlice, h)
                } finally {
                    keyshareSlice.free()
                }
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
        var keyIdSlice: go_slice? = null
        var ids: go_slice? = null
        var chainPathSlice: go_slice? = null
        var msgSlice: go_slice? = null
        try {
            val keyIdArr = getDKLSKeyshareID()
            keyIdSlice = keyIdArr.toDklsGoSlice()
            val byteArray = DklsHelper.arrayToBytes(keysignCommittee)
            ids = byteArray.toDklsGoSlice()
            when (vault.libType) {
                SigningLibType.DKLS -> {
                    val chainPathArr = chainPath.replace("'", "").toByteArray(Charsets.UTF_8)
                    chainPathSlice = chainPathArr.toDklsGoSlice()
                }

                SigningLibType.KeyImport -> {
                    chainPathSlice = null
                }

                else -> {
                    error("unsupported lib type for DKLS keysign: ${vault.libType}")
                }
            }

            val decodedMsgData = message.hexToByteArray()
            msgSlice = decodedMsgData.toDklsGoSlice()
            val err = dkls_sign_setupmsg_new(keyIdSlice, chainPathSlice, msgSlice, ids, buf)
            if (err != LIB_OK) {
                error("fail to setup keysign message, dkls error: $err")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            tss_buffer_free(buf)
            keyIdSlice?.free()
            ids?.free()
            chainPathSlice?.free()
            msgSlice?.free()
        }
    }

    @Throws(Exception::class)
    private fun decodeMessage(setupMsg: ByteArray): String {
        val buf = tss_buffer()
        val setupMsgSlice = setupMsg.toDklsGoSlice()
        try {
            val result = dkls_decode_message(setupMsgSlice, buf)
            if (result != LIB_OK) {
                error("fail to extract message from setup message: $result")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf).toHexString()
        } finally {
            tss_buffer_free(buf)
            setupMsgSlice.free()
        }
    }

    private fun getOutboundMessageReceiver(
        handle: Handle,
        message: go_slice,
        idx: Long,
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
            val message = outboundMessage.toDklsGoSlice()
            try {
                val encodedOutboundMessage = Base64.encode(outboundMessage)
                for (i in keysignCommittee.indices) {
                    val receiverArray = getOutboundMessageReceiver(handle, message, i.toLong())
                    if (receiverArray.isEmpty()) {
                        break
                    }
                    val receiverString = String(receiverArray, Charsets.UTF_8)
                    println(
                        "sending message from $localPartyID to: $receiverString, content length: ${encodedOutboundMessage.length}"
                    )
                    messenger.send(localPartyID, receiverString, encodedOutboundMessage)
                }
            } finally {
                message.free()
            }
        }
    }

    private suspend fun processInboundMessage(
        handle: Handle,
        msgs: List<Message>,
        messageID: String,
    ): Boolean {
        val sortedMsgs = msgs.sortedBy { it.sequenceNo }
        for (msg in sortedMsgs) {
            val key = "$sessionID-$localPartyID-$messageID-${msg.hash}"
            if (cache[key] != null) {
                println("message with key: $key has been applied before")
                // Once per attempt: repeating a delete the relay is ignoring only parks the poll
                // loop behind the relay client's own backoff.
                if (redeletedHashes.add(msg.hash)) deleteMessageFromServer(msg.hash, messageID)
                continue
            }
            println("Got message from: ${msg.from}, to: ${msg.to}, key: $key")
            poller.recordPeerHeard(msg.from)
            val decryptedBody =
                encryption.decrypt(
                    Base64.decode(msg.body),
                    Numeric.hexStringToByteArray(encryptionKeyHex),
                ) ?: error("fail to decrypt message body")
            val decodedMsg = Base64.decode(decryptedBody)
            val decryptedBodySlice = decodedMsg.toDklsGoSlice()
            val isFinished = intArrayOf(0)
            val result =
                try {
                    dkls_sign_session_input_message(handle, decryptedBodySlice, isFinished)
                } finally {
                    decryptedBodySlice.free()
                }
            checkForBannedParty(result.swigValue(), keysignCommittee)
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
        sessionApi.deleteTssMessageQuietly(mediatorURL, sessionID, localPartyID, hash, messageID)
    }

    private suspend fun keysignOneMessageWithRetry(attempt: Int, messageToSign: String) {
        if (attempt == 0) {
            poller.resetForNewMessage()
        }
        cache.clear()
        redeletedHashes.clear()
        val msgHash = messageToSign.md5()
        val localMessenger =
            TssMessenger(
                mediatorURL,
                sessionID,
                encryptionKeyHex,
                sessionApi,
                CoroutineScope(Dispatchers.IO),
                encryption,
                true,
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
                    message =
                        Base64.encode(
                            encryption.encrypt(
                                Base64.encodeToByteArray(keysignSetupMsg),
                                Numeric.hexStringToByteArray(encryptionKeyHex),
                            )
                        ),
                    messageId = msgHash,
                )
            } else {
                keysignSetupMsg =
                    sessionApi
                        .getSetupMessage(mediatorURL, sessionID, msgHash)
                        .let {
                            encryption.decrypt(
                                Base64.decode(it),
                                Numeric.hexStringToByteArray(encryptionKeyHex),
                            )!!
                        }
                        .let { Base64.decode(it) }
            }

            val signingMsg = decodeMessage(keysignSetupMsg)
            if (signingMsg != messageToSign) {
                error("message doesn't match ($messageToSign) vs ($signingMsg)")
            }
            val decodedSetupMsg = keysignSetupMsg.toDklsGoSlice()
            val handler = Handle()
            val localPartyIDArr = localPartyID.toByteArray()
            val localPartySlice = localPartyIDArr.toDklsGoSlice()
            val keyShareBytes = getKeyshareBytes()
            val keyshareSlice = keyShareBytes.toDklsGoSlice()
            val keyshareHandle = Handle()
            val result =
                try {
                    dkls_keyshare_from_bytes(keyshareSlice, keyshareHandle)
                } finally {
                    keyshareSlice.free()
                }
            if (result != LIB_OK) {
                error("fail to create keyshare handle from bytes, $result")
            }
            val sessionResult =
                try {
                    dkls_sign_session_from_setup(
                        decodedSetupMsg,
                        localPartySlice,
                        keyshareHandle,
                        handler,
                    )
                } finally {
                    decodedSetupMsg.free()
                    localPartySlice.free()
                }
            checkForBannedParty(sessionResult.swigValue(), keysignCommittee)
            if (sessionResult != LIB_OK) {
                error("fail to create sign session from setup message, error: $sessionResult")
            }
            processDKLSOutboundMessage(handler)
            val isFinished = poller.poll(msgHash) { processInboundMessage(handler, it, msgHash) }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: MaliciousPartyException) {
            // A banned party is a protocol-level verdict from the library, not a transient
            // failure — retrying would just re-sign against a peer DKLS already banned.
            throw e
        } catch (e: Exception) {
            println("Failed to sign message ($messageToSign), error: ${e.localizedMessage}")
            val recovered =
                recoverKeysignFromRelay(
                    sessionApi = sessionApi,
                    mediatorURL = mediatorURL,
                    sessionID = sessionID,
                    msgHash = msgHash,
                    messageToSign = messageToSign,
                    signatures = signatures,
                    onRecovered = poller::clearWaitingForPeers,
                )
            if (recovered) {
                return
            }
            val maxRetries = if (poller.hasHeardFromAnyPeer) 3 else 1
            if (attempt < maxRetries) {
                keysignOneMessageWithRetry(attempt + 1, messageToSign)
            } else {
                throw e
            }
        }
    }

    @Throws(Exception::class)
    private fun dklsSignSessionFinish(handle: Handle): ByteArray {
        val buf = tss_buffer()
        try {
            val result = dkls_sign_session_finish(handle, buf)
            checkForBannedParty(result.swigValue(), keysignCommittee)
            if (result != LIB_OK) {
                error("fail to get keysign signature $result")
            }
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            tss_buffer_free(buf)
        }
    }

    /** Signs all [messageToSign] entries, retrying each on failure. */
    suspend fun keysignWithRetry() {
        for (msg in messageToSign) {
            keysignOneMessageWithRetry(0, msg)
        }
    }
}
