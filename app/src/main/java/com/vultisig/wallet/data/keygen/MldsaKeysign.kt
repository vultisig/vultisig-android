@file:OptIn(ExperimentalEncodingApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.keygen

import com.silencelaboratories.godilithium.BufferUtilJNI
import com.silencelaboratories.godilithium.Handle
import com.silencelaboratories.godilithium.MldsaSecurityLevel
import com.silencelaboratories.godilithium.go_slice
import com.silencelaboratories.godilithium.godilithium.mldsa_decode_message
import com.silencelaboratories.godilithium.godilithium.mldsa_keyshare_free
import com.silencelaboratories.godilithium.godilithium.mldsa_keyshare_from_bytes
import com.silencelaboratories.godilithium.godilithium.mldsa_keyshare_key_id
import com.silencelaboratories.godilithium.godilithium.mldsa_sign_session_finish
import com.silencelaboratories.godilithium.godilithium.mldsa_sign_session_free
import com.silencelaboratories.godilithium.godilithium.mldsa_sign_session_from_setup
import com.silencelaboratories.godilithium.godilithium.mldsa_sign_session_input_message
import com.silencelaboratories.godilithium.godilithium.mldsa_sign_session_message_receiver
import com.silencelaboratories.godilithium.godilithium.mldsa_sign_session_output_message
import com.silencelaboratories.godilithium.godilithium.mldsa_sign_setupmsg_new
import com.silencelaboratories.godilithium.godilithium.tss_buffer_free
import com.silencelaboratories.godilithium.mldsa_error
import com.silencelaboratories.godilithium.mldsa_error.LIB_OK
import com.silencelaboratories.godilithium.tss_buffer
import com.vultisig.wallet.data.api.KeysignVerify
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.utils.Numeric
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import timber.log.Timber
import tss.KeysignResponse

/**
 * ML-DSA-44 (Dilithium) multi-party keysign.
 *
 * Coordinates a threshold signing session between [keysignCommittee] members via the relay
 * [mediatorURL]. Each message hash in [messageToSign] is signed sequentially with automatic retry
 * on transient protocol failures.
 */
class MldsaKeysign(
    private val keysignCommittee: List<String>,
    private val mediatorURL: String,
    private val sessionID: String,
    private val messageToSign: List<String>,
    private val vault: Vault,
    private val encryptionKeyHex: String,
    private val isInitiateDevice: Boolean,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val onWaitingForPeers: ((List<String>) -> Unit)? = null,
    private val onPeersResumed: (() -> Unit)? = null,
) {
    private val localPartyID: String = vault.localPartyID
    private val publicKeyMldsa: String = vault.pubKeyMLDSA

    private var messenger: TssMessenger? = null
    /** Deduplicates already-applied inbound messages by composite key. */
    private val appliedMessages = mutableSetOf<String>()
    private val heardFromThisAttempt = mutableSetOf<String>()
    private val heardFromEver = mutableSetOf<String>()
    private var waitingNotified = false

    /** Collects signatures keyed by the signed message hex string. */
    val signatures = mutableMapOf<String, KeysignResponse>()

    /** Signs all [messageToSign] hashes sequentially, retrying each on failure. */
    suspend fun keysignWithRetry() {
        messageToSign.forEach { keysignOneMessage(attempt = 0, messageToSign = it) }
    }

    /**
     * Signs a single message hash with retry.
     *
     * The initiating device creates and uploads the setup message on the first attempt; on retries
     * (or for non-initiating devices) the existing one is downloaded. The protocol then exchanges
     * messages via the mediator until the signature is produced.
     */
    private suspend fun keysignOneMessage(attempt: Int, messageToSign: String) {
        if (attempt == 0) {
            heardFromEver.clear()
            waitingNotified = false
        }
        appliedMessages.clear()
        val msgHash = messageToSign.md5()

        messenger =
            TssMessenger(
                    mediatorURL,
                    sessionID,
                    encryptionKeyHex,
                    sessionApi,
                    CoroutineScope(Dispatchers.IO),
                    encryption,
                    true,
                )
                .also { it.setMessageID(msgHash) }

        try {
            Timber.d(
                "MLDSA keysign attempt=%d, isInitiate=%b, msgHash=%s, " +
                    "sessionID=%s, committee=%s, localParty=%s",
                attempt,
                isInitiateDevice,
                msgHash,
                sessionID,
                keysignCommittee,
                localPartyID,
            )

            val setupMsg = obtainSetupMessage(attempt, messageToSign, msgHash)
            verifySetupMessage(setupMsg, messageToSign)
            executeSigningProtocol(setupMsg, msgHash, messageToSign)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to sign message (%s)", messageToSign)
            val maxRetries = if (heardFromEver.isEmpty()) 1 else MAX_PROTOCOL_RETRIES
            if (attempt < maxRetries) {
                keysignOneMessage(attempt + 1, messageToSign)
            } else {
                throw e
            }
        }
    }

    /**
     * The initiating device creates a fresh setup message on the first attempt and uploads it to
     * the mediator. Non-initiating devices (or retries) download and decrypt the existing one.
     */
    private suspend fun obtainSetupMessage(
        attempt: Int,
        messageToSign: String,
        msgHash: String,
    ): ByteArray =
        if (isInitiateDevice && attempt == 0) {
            createAndUploadSetupMessage(messageToSign, msgHash)
        } else {
            downloadSetupMessage(msgHash)
        }

    private suspend fun createAndUploadSetupMessage(
        messageToSign: String,
        msgHash: String,
    ): ByteArray {
        val setupMsg = getMldsaKeysignSetupMessage(messageToSign)
        Timber.d("MLDSA setup message created, size=%d, uploading…", setupMsg.size)
        sessionApi.uploadSetupMessage(
            serverUrl = mediatorURL,
            sessionId = sessionID,
            message =
                Base64.encode(
                    encryption.encrypt(
                        Base64.encodeToByteArray(setupMsg),
                        Numeric.hexStringToByteArray(encryptionKeyHex),
                    )
                ),
            messageId = msgHash,
        )
        return setupMsg
    }

    private suspend fun downloadSetupMessage(msgHash: String): ByteArray {
        val encrypted = sessionApi.getSetupMessage(mediatorURL, sessionID, msgHash)
        val decrypted =
            encryption.decrypt(
                Base64.decode(encrypted),
                Numeric.hexStringToByteArray(encryptionKeyHex),
            ) ?: error("fail to decrypt MLDSA keysign setup message")
        return Base64.decode(decrypted)
    }

    private fun verifySetupMessage(setupMsg: ByteArray, expectedMessage: String) {
        val decoded = decodeMessage(setupMsg)
        check(decoded == expectedMessage) {
            "Setup message mismatch: expected $expectedMessage, got $decoded"
        }
    }

    /**
     * Runs the MPC signing rounds: loads keyshare, creates session from setup, exchanges messages
     * with peers until the protocol completes, then finalizes the signature.
     */
    private suspend fun executeSigningProtocol(
        setupMsg: ByteArray,
        msgHash: String,
        messageToSign: String,
    ) {
        val session = Handle()
        val keyshareHandle = Handle()
        try {
            mldsa_keyshare_from_bytes(getKeyshareBytes().toGoSlice(), keyshareHandle)
                .check("load keyshare")
            mldsa_sign_session_from_setup(
                    MldsaSecurityLevel.MlDsa44,
                    setupMsg.toGoSlice(),
                    localPartyID.toByteArray().toGoSlice(),
                    keyshareHandle,
                    session,
                )
                .check("create sign session")

            drainOutbound(session)
            if (pollInbound(session, msgHash)) {
                drainOutbound(session)
                // finish can fail transiently (LIB_ABORT_PROTOCOL_PARTY_*) — retry is inside
                val sig = finishSignSession(session)
                reportSignature(msgHash, messageToSign, sig)
            }
        } finally {
            mldsa_sign_session_free(session)
            session.delete()
            mldsa_keyshare_free(keyshareHandle)
            keyshareHandle.delete()
        }
    }

    private suspend fun reportSignature(msgHash: String, messageToSign: String, sig: ByteArray) {
        val resp =
            KeysignResponse().apply {
                msg = messageToSign
                derSignature = sig.toHexString()
            }
        KeysignVerify(mediatorURL, sessionID, sessionApi)
            .markLocalPartyKeysignComplete(msgHash, resp)
        signatures[messageToSign] = resp
    }

    /**
     * Extracts the final signature from the completed session.
     *
     * The native library can return transient `LIB_ABORT_PROTOCOL` errors, so the call is retried
     * up to [FINISH_MAX_RETRIES] times.
     */
    private suspend fun finishSignSession(handle: Handle): ByteArray =
        retryWithDelay(FINISH_MAX_RETRIES, FINISH_RETRY_DELAY_MS) {
            withTssBuffer { buf ->
                mldsa_sign_session_finish(handle, buf).check("finish sign session")
                BufferUtilJNI.get_bytes_from_tss_buffer(buf)
            }
        }

    /** Drains all pending outbound messages and routes them to committee peers. */
    private fun drainOutbound(handle: Handle) {
        while (true) {
            val (result, payload) = readOutboundMessage(handle)
            if (result != LIB_OK) Timber.d("outbound message error: %s", result)
            if (payload.isEmpty()) return

            val encoded = Base64.encode(payload)
            val slice = payload.toGoSlice()
            for (i in keysignCommittee.indices) {
                val receiver = getOutboundReceiver(handle, slice, i.toLong())
                if (receiver.isEmpty()) break
                val receiverId = String(receiver, Charsets.UTF_8)
                Timber.d(
                    "sending from %s to %s, length=%d",
                    localPartyID,
                    receiverId,
                    encoded.length,
                )
                messenger?.send(localPartyID, receiverId, encoded)
            }
        }
    }

    /**
     * Polls the mediator for inbound messages until the protocol completes or 60 s of silence
     * elapses. Notifies [onWaitingForPeers] after 10 s of silence and [onPeersResumed] when
     * messages resume.
     *
     * @return `true` when the signing protocol has finished successfully.
     */
    private suspend fun pollInbound(handle: Handle, messageID: String): Boolean {
        heardFromThisAttempt.clear()
        var lastMessageNano = System.nanoTime()

        while (true) {
            try {
                val msgs =
                    sessionApi.getTssMessages(mediatorURL, sessionID, localPartyID, messageID)
                if (msgs.isNotEmpty()) {
                    if (waitingNotified) {
                        waitingNotified = false
                        onPeersResumed?.invoke()
                    }
                    lastMessageNano = System.nanoTime()
                    if (applyInboundMessages(handle, msgs, messageID)) return true
                } else {
                    delay(POLL_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to get messages")
                delay(POLL_INTERVAL_MS)
            }

            val silenceSecs = (System.nanoTime() - lastMessageNano) / 1_000_000_000.0
            if (!waitingNotified && silenceSecs > 10) {
                waitingNotified = true
                val missingPeers =
                    keysignCommittee.filter { it != localPartyID && it !in heardFromThisAttempt }
                if (missingPeers.isNotEmpty()) {
                    onWaitingForPeers?.invoke(missingPeers)
                }
            }
            if (silenceSecs > 60) {
                val missingPeers =
                    keysignCommittee.filter { it != localPartyID && it !in heardFromThisAttempt }
                val msg =
                    if (missingPeers.isEmpty()) {
                        "keysign timed out: all peers responded but protocol did not complete within 60s"
                    } else {
                        "no messages from ${missingPeers.joinToString()} in 60s"
                    }
                error(msg)
            }
        }
    }

    /**
     * Decrypts and applies each inbound message to the session.
     *
     * @return `true` when the native library signals that signing is complete.
     */
    private suspend fun applyInboundMessages(
        handle: Handle,
        msgs: List<Message>,
        messageID: String,
    ): Boolean {
        for (msg in msgs.sortedBy { it.sequenceNo }) {
            val cacheKey = "$sessionID-$localPartyID-$messageID-${msg.hash}"
            if (!appliedMessages.add(cacheKey)) continue

            Timber.d("Got message from: %s, to: %s, key: %s", msg.from, msg.to, cacheKey)
            heardFromThisAttempt.add(msg.from)
            heardFromEver.add(msg.from)

            val decrypted =
                encryption.decrypt(
                    Base64.decode(msg.body),
                    Numeric.hexStringToByteArray(encryptionKeyHex),
                ) ?: error("fail to decrypt message body")

            // JNI out-param: set to non-zero when the protocol is complete
            val isFinished = intArrayOf(0)
            mldsa_sign_session_input_message(
                    handle,
                    Base64.decode(decrypted).toGoSlice(),
                    isFinished,
                )
                .check("apply inbound message")

            deleteMessageFromServer(msg.hash, messageID)
            drainOutbound(handle)

            if (isFinished[0] != 0) return true
        }
        return false
    }

    private suspend fun deleteMessageFromServer(hash: String, messageID: String) {
        sessionApi.deleteTssMessage(mediatorURL, sessionID, localPartyID, hash, messageID)
    }

    /** Finds the MLDSA keyshare matching [publicKeyMldsa] in the vault. */
    private fun getKeyshareBytes(): ByteArray {
        val keyshare =
            vault.keyshares.firstOrNull { it.pubKey == publicKeyMldsa }?.keyShare
                ?: error("no MLDSA keyshare for pubKey ${publicKeyMldsa.take(16)}…")
        return Base64.decode(keyshare)
    }

    /** Extracts the key ID from the local MLDSA keyshare (needed for setup messages). */
    private fun getMldsaKeyshareID(): ByteArray {
        val keyshareSlice = getKeyshareBytes().toGoSlice()
        val handle = Handle()
        val buf = tss_buffer()
        try {
            mldsa_keyshare_from_bytes(keyshareSlice, handle).check("load keyshare")
            mldsa_keyshare_key_id(handle, buf).check("get keyshare ID")
            return BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            mldsa_keyshare_free(handle)
            handle.delete()
            tss_buffer_free(buf)
        }
    }

    /** Builds the keysign setup message that the initiating device distributes. */
    private fun getMldsaKeysignSetupMessage(message: String): ByteArray = withTssBuffer { buf ->
        mldsa_sign_setupmsg_new(
                MldsaSecurityLevel.MlDsa44,
                getMldsaKeyshareID().toGoSlice(),
                null,
                message.hexToByteArray().toGoSlice(),
                DklsHelper.arrayToBytes(keysignCommittee).toGoSlice(),
                buf,
            )
            .check("create keysign setup message")
        BufferUtilJNI.get_bytes_from_tss_buffer(buf)
    }

    /** Extracts the hex-encoded message hash from a setup message for verification. */
    private fun decodeMessage(setupMsg: ByteArray): String = withTssBuffer { buf ->
        mldsa_decode_message(setupMsg.toGoSlice(), buf).check("decode setup message")
        BufferUtilJNI.get_bytes_from_tss_buffer(buf).toHexString()
    }

    private fun getOutboundReceiver(handle: Handle, message: go_slice, idx: Long): ByteArray =
        withTssBuffer { buf ->
            val result = mldsa_sign_session_message_receiver(handle, message, idx, buf)
            if (result != LIB_OK) byteArrayOf() else BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        }

    private fun readOutboundMessage(handle: Handle): Pair<mldsa_error, ByteArray> =
        withTssBuffer { buf ->
            val result = mldsa_sign_session_output_message(handle, buf)
            if (result != LIB_OK) result to byteArrayOf()
            else result to BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        }

    /** Allocates a [tss_buffer], runs [block], and guarantees cleanup via `finally`. */
    private inline fun <T> withTssBuffer(block: (tss_buffer) -> T): T {
        val buf = tss_buffer()
        try {
            return block(buf)
        } finally {
            tss_buffer_free(buf)
        }
    }

    /** Throws [IllegalStateException] with a descriptive message if this is not [LIB_OK]. */
    private fun mldsa_error.check(operation: String) {
        if (this != LIB_OK) error("MLDSA $operation failed: $this")
    }

    private fun ByteArray.toGoSlice(): go_slice {
        val slice = go_slice()
        BufferUtilJNI.set_bytes_on_go_slice(slice, this)
        return slice
    }

    companion object {
        internal const val FINISH_MAX_RETRIES = 3
        internal const val FINISH_RETRY_DELAY_MS = 1000L

        private const val MAX_PROTOCOL_RETRIES = 3
        private const val POLL_INTERVAL_MS = 100L
    }
}
