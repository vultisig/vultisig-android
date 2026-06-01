@file:OptIn(ExperimentalStdlibApi::class, ExperimentalEncodingApi::class)

package com.vultisig.wallet.data.qbtc

import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimPeerResultPusher
import com.vultisig.wallet.data.keygen.DKLSKeysign
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.utils.compatibleDerivationPath
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import wallet.core.jni.CoinType

/**
 * The broadcast result the initiator pushes to its co-signer over the relay's setup-message
 * channel, so the peer's done screen can show the same tx hash.
 */
@Serializable
internal data class QbtcClaimResultMessage(val txHash: String, val totalSats: Long) {
    companion object {
        const val MESSAGE_ID = "qbtc-claim-result"
    }
}

/**
 * Pushes [QbtcClaimResultMessage] to the peer, AES-encrypted under the session's encryption key and
 * keyed by [QbtcClaimResultMessage.MESSAGE_ID] so it doesn't collide with the DKLS setup message.
 * Mirrors the encrypt/upload pattern in `DKLSKeysign`.
 */
internal class QbtcClaimRelayResultPusher(
    private val session: QbtcClaimKeysignSession,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val json: Json,
) : QbtcClaimPeerResultPusher {

    override suspend fun push(txHashHex: String, totalSats: Long) {
        val plain =
            json
                .encodeToString(
                    QbtcClaimResultMessage.serializer(),
                    QbtcClaimResultMessage(txHashHex, totalSats),
                )
                .toByteArray()
        val encrypted = encryption.encrypt(plain, session.encryptionKeyHex.hexToByteArray())
        sessionApi.uploadSetupMessage(
            serverUrl = session.serverUrl,
            sessionId = session.sessionId,
            message = Base64.encode(encrypted),
            messageId = QbtcClaimResultMessage.MESSAGE_ID,
        )
    }
}

/** Polls the relay for the initiator's pushed broadcast result. */
internal class QbtcClaimResultPoller
@Inject
constructor(
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val json: Json,
) {

    /**
     * Polls until the initiator's broadcast result arrives or [timeout] elapses. The initiator only
     * pushes the result after its proof POST + broadcast finishes, which can be well after the
     * shared DKLS round both devices just ran — so a single read would almost always miss it.
     * Returns null if nothing arrives in time; the peer still signed successfully.
     */
    suspend fun awaitResult(
        session: QbtcClaimKeysignSession,
        timeout: Duration = DEFAULT_TIMEOUT,
        interval: Duration = POLL_INTERVAL,
    ): QbtcClaimResultMessage? =
        withTimeoutOrNull(timeout) {
            var result = fetch(session)
            while (currentCoroutineContext().isActive && result == null) {
                delay(interval)
                result = fetch(session)
            }
            result
        }

    private suspend fun fetch(session: QbtcClaimKeysignSession): QbtcClaimResultMessage? =
        try {
            val raw =
                sessionApi.getSetupMessage(
                    session.serverUrl,
                    session.sessionId,
                    QbtcClaimResultMessage.MESSAGE_ID,
                )
            val decrypted =
                encryption.decrypt(Base64.decode(raw), session.encryptionKeyHex.hexToByteArray())
                    ?: return null
            json.decodeFromString(QbtcClaimResultMessage.serializer(), decrypted.decodeToString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "QBTC claim result poll failed")
            null
        }

    private companion object {
        val DEFAULT_TIMEOUT = 300.seconds
        val POLL_INTERVAL = 2.seconds
    }
}

/**
 * Co-signs the BTC ECDSA claim round as the non-initiating device. Registers on the relay session,
 * waits to be added to the committee, then runs DKLS with `isInitiateDevice = false` over the
 * locally-recomputed claim message hash on the Bitcoin derivation path. Mirrors iOS
 * `QBTCClaimJoinDriver`'s round.
 */
internal class QbtcClaimPeerRoundRunner
@Inject
constructor(private val sessionApi: SessionApi, private val encryption: Encryption) {

    suspend fun coSign(
        vault: Vault,
        messageHashHex: String,
        serverUrl: String,
        sessionId: String,
        encryptionKeyHex: String,
    ): QbtcClaimKeysignSession {
        val localPartyId = vault.localPartyID
        sessionApi.startSession(serverUrl, sessionId, listOf(localPartyId))

        val committee =
            withTimeout(KEYSIGN_START_TIMEOUT) {
                var current = sessionApi.checkCommittee(serverUrl, sessionId)
                while (currentCoroutineContext().isActive && localPartyId !in current) {
                    delay(POLL_INTERVAL)
                    current = sessionApi.checkCommittee(serverUrl, sessionId)
                }
                current
            }

        val dkls =
            DKLSKeysign(
                keysignCommittee = committee,
                mediatorURL = serverUrl,
                sessionID = sessionId,
                messageToSign = listOf(messageHashHex),
                vault = vault,
                encryptionKeyHex = encryptionKeyHex,
                chainPath = CoinType.BITCOIN.compatibleDerivationPath(),
                isInitiateDevice = false,
                sessionApi = sessionApi,
                encryption = encryption,
            )
        dkls.keysignWithRetry()

        return QbtcClaimKeysignSession(serverUrl, sessionId, encryptionKeyHex, committee)
    }

    private companion object {
        val KEYSIGN_START_TIMEOUT = 600.seconds
        val POLL_INTERVAL = 1.seconds
    }
}
