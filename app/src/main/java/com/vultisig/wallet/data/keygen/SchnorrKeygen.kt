@file:OptIn(ExperimentalEncodingApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.keygen

import com.silencelaboratories.goschnorr.BufferUtilJNI
import com.silencelaboratories.goschnorr.Handle
import com.silencelaboratories.goschnorr.go_slice
import com.silencelaboratories.goschnorr.goschnorr.schnorr_key_import_initiator_new
import com.silencelaboratories.goschnorr.goschnorr.schnorr_key_importer_new
import com.silencelaboratories.goschnorr.goschnorr.schnorr_key_migration_session_from_setup
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keygen_session_finish
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keygen_session_from_setup
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keygen_session_input_message
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keygen_session_message_receiver
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keygen_session_output_message
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keyshare_from_bytes
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keyshare_public_key
import com.silencelaboratories.goschnorr.goschnorr.schnorr_keyshare_to_bytes
import com.silencelaboratories.goschnorr.goschnorr.schnorr_qc_session_finish
import com.silencelaboratories.goschnorr.goschnorr.schnorr_qc_session_from_setup
import com.silencelaboratories.goschnorr.goschnorr.schnorr_qc_session_input_message
import com.silencelaboratories.goschnorr.goschnorr.schnorr_qc_session_message_receiver
import com.silencelaboratories.goschnorr.goschnorr.schnorr_qc_session_output_message
import com.silencelaboratories.goschnorr.goschnorr.schnorr_qc_setupmsg_new
import com.silencelaboratories.goschnorr.goschnorr.tss_buffer_free
import com.silencelaboratories.goschnorr.schnorr_lib_error
import com.silencelaboratories.goschnorr.schnorr_lib_error.LIB_OK
import com.silencelaboratories.goschnorr.tss_buffer
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.models.TssAction
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

class SchnorrKeygen(
    val localPartyId: String,
    val keygenCommittee: List<String>,
    val hexChainCode: String,
    val localUi: String,
    val vault: Vault,
    val oldCommittee: List<String>,
    val mediatorURL: String,
    val sessionID: String,
    val encryptionKeyHex: String,
    val setupMessage: ByteArray,
    val action: TssAction,
    private val isInitiatingDevice: Boolean,
    private val encryption: Encryption,
    private val sessionApi: SessionApi,
) {
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

    val cache = mutableMapOf<String, Any>()
    var keyshare: DKLSKeyshare? = null
    private var activeMessageId: String? = null

    private fun getSchnorrOutboundMessage(handle: Handle): Pair<schnorr_lib_error, ByteArray> {
        val buf = tss_buffer()
        return try {
            val result =
                when (action) {
                    TssAction.KEYGEN,
                    TssAction.Migrate,
                    TssAction.KeyImport -> schnorr_keygen_session_output_message(handle, buf)

                    TssAction.ReShare -> schnorr_qc_session_output_message(handle, buf)
                    TssAction.SingleKeygen -> error("SingleKeygen is handled separately")
                }
            if (result != LIB_OK) {
                Timber.d("fail to get outbound message: $result")
                return Pair(result, byteArrayOf())
            }

            Pair(result, BufferUtilJNI.get_bytes_from_tss_buffer(buf))
        } finally {
            tss_buffer_free(buf)
        }
    }

    private fun getOutboundMessageReceiver(
        handle: Handle,
        message: go_slice,
        idx: Long,
    ): ByteArray {
        val bufReceiver = tss_buffer()
        return try {
            val receiverResult =
                when (action) {
                    TssAction.KEYGEN,
                    TssAction.Migrate,
                    TssAction.KeyImport ->
                        schnorr_keygen_session_message_receiver(handle, message, idx, bufReceiver)

                    TssAction.ReShare ->
                        schnorr_qc_session_message_receiver(handle, message, idx, bufReceiver)
                    TssAction.SingleKeygen -> error("SingleKeygen is handled separately")
                }
            if (receiverResult != LIB_OK) {
                Timber.d("fail to get receiver message, error: $receiverResult")
                return byteArrayOf()
            }
            BufferUtilJNI.get_bytes_from_tss_buffer(bufReceiver)
        } finally {
            tss_buffer_free(bufReceiver)
        }
    }

    private fun processSchnorrOutboundMessage(handle: Handle) {
        while (true) {
            val (result, outboundMessage) = getSchnorrOutboundMessage(handle)
            if (result != LIB_OK) {
                Timber.d("fail to get outbound message")
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
                val receiverString = String(receiverArray, Charsets.UTF_8)
                Timber.d("sending message from $localPartyId to: $receiverString")
                messenger.send(localPartyId, receiverString, encodedOutboundMessage)
            }
        }
    }

    private suspend fun pullInboundMessages(handle: Handle): Boolean {
        Timber.d("start pulling inbound messages")

        val start = System.nanoTime()
        while (true) {
            try {
                val msgs =
                    sessionApi.getTssMessages(mediatorURL, sessionID, localPartyId, activeMessageId)

                if (msgs.isNotEmpty()) {
                    if (processInboundMessage(handle, msgs)) {
                        return true
                    }
                } else {
                    delay(1000)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to get messages")
                delay(1000)
            }

            val elapsedTime = (System.nanoTime() - start) / 1_000_000_000.0
            if (elapsedTime > 60) {
                error("timeout: Schnorr keygen did not finish within 60 seconds")
            }
        }
    }

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
            val decryptedBody =
                encryption.decrypt(
                    Base64.decode(msg.body),
                    Numeric.hexStringToByteArray(encryptionKeyHex),
                ) ?: error("fail to decrypt message body")
            val decodedMsg = Base64.decode(decryptedBody)

            val decryptedBodySlice = decodedMsg.toGoSlice()

            val isFinished = intArrayOf(0)
            val result =
                when (action) {
                    TssAction.KEYGEN,
                    TssAction.Migrate,
                    TssAction.KeyImport ->
                        schnorr_keygen_session_input_message(handle, decryptedBodySlice, isFinished)

                    TssAction.ReShare ->
                        schnorr_qc_session_input_message(handle, decryptedBodySlice, isFinished)
                    TssAction.SingleKeygen -> error("SingleKeygen is handled separately")
                }
            if (result != LIB_OK) {
                Timber.d("fail to apply message to schnorr, $result")
                error("fail to apply message to schnorr, $result")
            }
            cache[key] = Any()
            deleteMessageFromServer(msg.hash)
            processSchnorrOutboundMessage(handle)
            if (isFinished[0] != 0) {
                return true
            }
        }
        return false
    }

    private suspend fun deleteMessageFromServer(hash: String) {
        sessionApi.deleteTssMessage(mediatorURL, sessionID, localPartyId, hash, activeMessageId)
    }

    /**
     * Standard Schnorr keygen/migrate reuses the shared DKLS setup message. When those ceremonies
     * run in parallel, DKLS uploads the shared setup to the relay first and Schnorr downloads it on
     * demand instead of waiting for local in-memory setup state.
     */
    private suspend fun getSharedSetupMessage(setupMessageId: String? = null): ByteArray =
        if (setupMessage.isNotEmpty()) {
            setupMessage
        } else {
            sessionApi
                .getSetupMessage(mediatorURL, sessionID, setupMessageId)
                .let {
                    encryption.decrypt(
                        Base64.decode(it),
                        Numeric.hexStringToByteArray(encryptionKeyHex),
                    ) ?: error("fail to decrypt EdDSA keygen setup message")
                }
                .let { Base64.decode(it) }
        }

    @Throws(Exception::class)
    private fun getDklsKeyImportSetupMessage(
        hexPrivateKey: String,
        hexRootChainCode: String,
    ): Pair<ByteArray, Handle> {
        val buf = tss_buffer()
        val handler = Handle()
        try {
            val chainCodeArray = Numeric.hexStringToByteArray(hexRootChainCode)
            val chainCodeSlice = chainCodeArray.toGoSlice()
            val localUIArray = Numeric.hexStringToByteArray(hexPrivateKey)
            val localUISlice = localUIArray.toGoSlice()
            val threshold = DklsHelper.getThreshold(keygenCommittee.size)
            val byteArray = DklsHelper.arrayToBytes(keygenCommittee)
            val ids = go_slice()
            BufferUtilJNI.set_bytes_on_go_slice(ids, byteArray)
            val err =
                schnorr_key_import_initiator_new(
                    localUISlice,
                    chainCodeSlice,
                    threshold.toShort(),
                    ids,
                    buf,
                    handler,
                )
            if (err != LIB_OK) {
                error("fail to setup keygen message, schnorr error: $err")
            }
            val setupMessage = BufferUtilJNI.get_bytes_from_tss_buffer(buf)
            return Pair(setupMessage, handler)
        } finally {
            tss_buffer_free(buf)
        }
    }

    internal suspend fun schnorrKeygenWithRetry(
        attempt: Int,
        routing: KeygenRouting = KeygenRouting.from(),
    ) {
        activeMessageId = routing.exchangeMessageId
        messenger.setMessageID(activeMessageId)
        cache.clear()
        runKeygenWithRetry(
            attempt = attempt,
            retry = { nextAttempt, cause ->
                Timber.d("Failed to generate key, error: ${cause.localizedMessage}")
                Timber.d("Retry $action, attempt: $attempt")
                schnorrKeygenWithRetry(nextAttempt, routing)
            },
        ) {
            var handler = Handle()
            val localPartyIDArr = localPartyId.toByteArray()
            val localPartySlice = localPartyIDArr.toGoSlice()

            when (action) {
                TssAction.KEYGEN -> {
                    val decodedSetupMsg = getSharedSetupMessage(routing.setupMessageId).toGoSlice()
                    val result =
                        schnorr_keygen_session_from_setup(decodedSetupMsg, localPartySlice, handler)
                    if (result != LIB_OK) {
                        error("fail to create session from setup message, error: $result")
                    }
                }

                TssAction.Migrate -> {
                    val decodedSetupMsg = getSharedSetupMessage(routing.setupMessageId).toGoSlice()
                    if (this.localUi.isEmpty()) {
                        throw RuntimeException("can't migrate, local UI is empty")
                    }
                    val localUI = this.localUi
                    val publicKeyArray = Numeric.hexStringToByteArray(vault.pubKeyEDDSA)
                    val publicKeySlice = publicKeyArray.toGoSlice()
                    val chainCodeArray = Numeric.hexStringToByteArray(this.hexChainCode)
                    val chainCodeSlice = chainCodeArray.toGoSlice()
                    val localUIArray = Numeric.hexStringToByteArray(localUI)
                    val localUISlice = localUIArray.toGoSlice()

                    val result =
                        schnorr_key_migration_session_from_setup(
                            decodedSetupMsg,
                            localPartySlice,
                            publicKeySlice,
                            chainCodeSlice,
                            localUISlice,
                            handler,
                        )

                    if (result != LIB_OK) {
                        throw RuntimeException(
                            "fail to create migration session from setup message, error: $result"
                        )
                    }
                }

                TssAction.KeyImport -> {
                    // setupMessageId namespaces the setup message on the relay server so
                    // per-chain sessions (e.g. "Solana") don't collide with the root EdDSA one.
                    val eddsaHeader = routing.setupMessageId ?: "eddsa_key_import"
                    if (this.isInitiatingDevice) {
                        val (keyImportSetupMsg, keyImportHandler) =
                            getDklsKeyImportSetupMessage(this.localUi, this.hexChainCode)
                        handler = keyImportHandler
                        sessionApi.uploadSetupMessage(
                            serverUrl = mediatorURL,
                            sessionId = sessionID,
                            message =
                                Base64.encode(
                                    encryption.encrypt(
                                        Base64.encodeToByteArray(keyImportSetupMsg),
                                        Numeric.hexStringToByteArray(encryptionKeyHex),
                                    )
                                ),
                            messageId = eddsaHeader,
                        )
                    } else {
                        val keygenSetupMsg =
                            sessionApi
                                .getSetupMessage(mediatorURL, sessionID, eddsaHeader)
                                .let {
                                    encryption.decrypt(
                                        Base64.decode(it),
                                        Numeric.hexStringToByteArray(encryptionKeyHex),
                                    )!!
                                }
                                .let { Base64.decode(it) }
                        val result =
                            schnorr_key_importer_new(
                                keygenSetupMsg.toGoSlice(),
                                localPartySlice,
                                handler,
                            )
                        if (result != LIB_OK) {
                            throw RuntimeException(
                                "fail to create key import session from setup message, error: $result"
                            )
                        }
                    }
                }

                TssAction.ReShare -> error("This method shouldn't be used with $action")
                TssAction.SingleKeygen -> error("SingleKeygen is handled separately")
            }

            processSchnorrOutboundMessage(handler)
            val isFinished = pullInboundMessages(handler)
            if (isFinished) {
                val keyshareHandler = Handle()
                val keyShareResult = schnorr_keygen_session_finish(handler, keyshareHandler)
                if (keyShareResult != LIB_OK) {
                    error("Failed to get keyshare for $action, $keyShareResult")
                }
                val keyshareBytes = getKeyshareBytes(keyshareHandler)
                val publicKeyEdDSA = getPublicKeyBytes(keyshareHandler)
                keyshare =
                    DKLSKeyshare(
                        pubKey = publicKeyEdDSA.toHexString(),
                        keyshare = Base64.encode(keyshareBytes),
                        chaincode = "",
                    )
                Timber.d("publicKeyEdDSA: ${publicKeyEdDSA.toHexString()}")
                // slightly delay to give local party time to process outbound messages
                delay(500)
            }
        }
    }

    private fun processReshareCommittee(
        oldCommittee: List<String>,
        newCommittee: List<String>,
    ): Triple<List<String>, List<Byte>, List<Byte>> {
        val allParties = oldCommittee.toMutableList()
        val oldPartiesIdx = mutableListOf<Byte>()
        val newPartiesIdx = mutableListOf<Byte>()

        for (item in newCommittee) {
            if (!allParties.contains(item)) {
                allParties.add(item)
            }
        }

        for ((idx, item) in allParties.withIndex()) {
            if (oldCommittee.contains(item)) {
                oldPartiesIdx.add(idx.toByte())
            }
            if (newCommittee.contains(item)) {
                newPartiesIdx.add(idx.toByte())
            }
        }
        return Triple(allParties, newPartiesIdx, oldPartiesIdx)
    }

    private fun getKeyshareString(): String? {
        for (ks in vault.keyshares) {
            if (ks.pubKey == vault.pubKeyEDDSA) {
                return ks.keyShare
            }
        }
        return null
    }

    @Throws(Exception::class)
    private fun getKeyshareBytesFromVault(): ByteArray {
        val localKeyshare =
            getKeyshareString() ?: throw RuntimeException("fail to get local keyshare")
        return Base64.decode(localKeyshare)
    }

    @Throws(Exception::class)
    private fun getSchnorrReshareSetupMessage(keyshareHandle: Handle): ByteArray {
        val buf = tss_buffer()
        return try {
            val threshold = DklsHelper.getThreshold(keygenCommittee.size)
            val (allParties, newPartiesIdx, oldPartiesIdx) =
                processReshareCommittee(oldCommittee, keygenCommittee)
            val byteArray = DklsHelper.arrayToBytes(allParties)
            val ids = byteArray.toGoSlice()
            val newPartiesIdxSlice = newPartiesIdx.toByteArray().toGoSlice()
            val oldPartiesIdxSlice = oldPartiesIdx.toByteArray().toGoSlice()
            val result =
                schnorr_qc_setupmsg_new(
                    keyshareHandle,
                    ids,
                    oldPartiesIdxSlice,
                    threshold,
                    newPartiesIdxSlice,
                    buf,
                )
            if (result != LIB_OK) {
                throw RuntimeException("fail to get qc setup message, $result")
            }
            BufferUtilJNI.get_bytes_from_tss_buffer(buf)
        } finally {
            tss_buffer_free(buf)
        }
    }

    /**
     * Runs the Schnorr (EdDSA) reshare ceremony.
     *
     * The default [routing] keeps legacy behavior: setup is namespaced as `"eddsa"` so it doesn't
     * collide with the DKLS reshare setup that runs sequentially on the untagged namespace, and
     * exchange is untagged. In batched mode the caller supplies `p-eddsa` for both setup and
     * exchange so the server's batch reshare goroutine routes EdDSA traffic in isolation.
     *
     * Cancellation propagates through [runKeygenWithRetry] so a failure on the sibling DKLS
     * ceremony cancels this one cleanly when both run via `coroutineScope { awaitAll() }`.
     */
    @Throws(Exception::class)
    internal suspend fun schnorrReshareWithRetry(
        attempt: Int,
        routing: KeygenRouting = KeygenRouting.from(),
    ) {
        activeMessageId = routing.exchangeMessageId
        messenger.setMessageID(activeMessageId)
        cache.clear()
        // Setup defaults to "eddsa" for the legacy sequential path so it stays distinguishable
        // from the DKLS reshare setup that lives on the untagged namespace. In batched mode the
        // caller passes "p-eddsa" explicitly.
        val setupId = routing.setupMessageId ?: "eddsa"
        runKeygenWithRetry(
            attempt = attempt,
            retry = { nextAttempt, cause ->
                Timber.d("Failed to reshare EdDSA key, error: %s", cause.localizedMessage)
                Timber.d("eddsa reshare retry, attempt: %d", nextAttempt)
                schnorrReshareWithRetry(nextAttempt, routing)
            },
        ) {
            val keyshareHandle = Handle()
            if (vault.pubKeyEDDSA.isNotEmpty()) {
                val keyshareBytes = getKeyshareBytesFromVault()
                val keyshareSlice = keyshareBytes.toGoSlice()
                val result = schnorr_keyshare_from_bytes(keyshareSlice, keyshareHandle)
                if (result != LIB_OK) {
                    error("fail to get keyshare, $result")
                }
            }

            val reshareSetupMsg: ByteArray =
                if (isInitiatingDevice && attempt == 0) {
                    val msg = getSchnorrReshareSetupMessage(keyshareHandle)
                    sessionApi.uploadSetupMessage(
                        serverUrl = mediatorURL,
                        sessionId = sessionID,
                        message =
                            Base64.encode(
                                encryption.encrypt(
                                    Base64.encodeToByteArray(msg),
                                    Numeric.hexStringToByteArray(encryptionKeyHex),
                                )
                            ),
                        messageId = setupId,
                    )
                    msg
                } else {
                    // Backoff so the initiator has time to upload the setup before we poll.
                    delay(500)
                    sessionApi
                        .getSetupMessage(mediatorURL, sessionID, setupId)
                        .let {
                            encryption.decrypt(
                                Base64.decode(it),
                                Numeric.hexStringToByteArray(encryptionKeyHex),
                            ) ?: error("fail to decrypt reshare setup message")
                        }
                        .let { Base64.decode(it) }
                }

            val decodedSetupMsg = reshareSetupMsg.toGoSlice()
            val handler = Handle()
            val localPartyIDArr = localPartyId.toByteArray()
            val localPartySlice = localPartyIDArr.toGoSlice()

            val sessionResult =
                schnorr_qc_session_from_setup(
                    decodedSetupMsg,
                    localPartySlice,
                    keyshareHandle,
                    handler,
                )
            if (sessionResult != LIB_OK) {
                error("fail to create session from reshare setup message, error: $sessionResult")
            }

            processSchnorrOutboundMessage(handler)
            val isFinished = pullInboundMessages(handler)
            if (isFinished) {
                val newKeyshareHandler = Handle()
                val keyShareResult = schnorr_qc_session_finish(handler, newKeyshareHandler)
                if (keyShareResult != LIB_OK) {
                    error("fail to get new keyshare, $keyShareResult")
                }
                val keyshareBytes = getKeyshareBytes(newKeyshareHandler)
                val publicKeyEdDSA = getPublicKeyBytes(newKeyshareHandler)
                keyshare =
                    DKLSKeyshare(
                        pubKey = publicKeyEdDSA.toHexString(),
                        keyshare = Base64.encode(keyshareBytes),
                        chaincode = "",
                    )
                Timber.d("reshare EdDSA key successfully")
                delay(500)
            }
        }
    }

    private fun getKeyshareBytes(handle: Handle): ByteArray {
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

    private fun getPublicKeyBytes(handle: Handle): ByteArray {
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
