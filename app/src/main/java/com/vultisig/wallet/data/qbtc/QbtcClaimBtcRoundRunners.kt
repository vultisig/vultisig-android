package com.vultisig.wallet.data.qbtc

import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimBtcRoundInput
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimBtcRoundResult
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimBtcRoundRunner
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.keygen.DKLSKeysign
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.data.utils.compatibleDerivationPath
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import wallet.core.jni.CoinType

/** A relay session that the QBTC claim Secure-Vault rounds run on (post-pairing). */
internal data class QbtcClaimKeysignSession(
    val serverUrl: String,
    val sessionId: String,
    val encryptionKeyHex: String,
    val committee: List<String>,
)

private const val BTC_ROUND_ERROR_PREFIX = "QBTC claim BTC round"

/** The Bitcoin derivation path the vault's ECDSA key signs the claim hash under. */
private val btcDerivationPath: String
    get() = CoinType.BITCOIN.compatibleDerivationPath()

/** Runs the DKLS round for [messageHashHex] and extracts the `(r, s)` of the signature. */
private suspend fun runDklsRound(
    vault: Vault,
    committee: List<String>,
    serverUrl: String,
    sessionId: String,
    encryptionKeyHex: String,
    messageHashHex: String,
    isInitiateDevice: Boolean,
    sessionApi: SessionApi,
    encryption: Encryption,
): QbtcClaimBtcRoundResult {
    val dkls =
        DKLSKeysign(
            keysignCommittee = committee,
            mediatorURL = serverUrl,
            sessionID = sessionId,
            messageToSign = listOf(messageHashHex),
            vault = vault,
            encryptionKeyHex = encryptionKeyHex,
            chainPath = btcDerivationPath,
            isInitiateDevice = isInitiateDevice,
            sessionApi = sessionApi,
            encryption = encryption,
        )
    dkls.keysignWithRetry()
    val signature =
        dkls.signatures[messageHashHex]
            ?: error("$BTC_ROUND_ERROR_PREFIX: missing signature for $messageHashHex")
    return QbtcClaimBtcRoundResult(rHex = signature.r, sHex = signature.s)
}

/**
 * Fast Vault initiator. Provisions a relay session, wakes the Vultisig server co-signer over the
 * relay, waits for it to register, kicks off the committee, then runs the DKLS round. Mirrors iOS
 * `QBTCClaimRoundRunner`.
 */
internal class QbtcClaimFastVaultRoundRunner
@Inject
constructor(
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val vultiSignerRepository: VultiSignerRepository,
    private val discoverParticipants: DiscoverParticipantsUseCase,
) : QbtcClaimBtcRoundRunner {

    override suspend fun run(input: QbtcClaimBtcRoundInput): QbtcClaimBtcRoundResult {
        val serverUrl = Endpoints.VULTISIG_RELAY_URL
        val sessionId = UUID.randomUUID().toString()
        val encryptionKeyHex = Utils.encryptionKeyHex
        val localPartyId = input.vault.localPartyID

        // Register before inviting the server, or the relay never queues the
        // server's messages to this device and the round hangs.
        sessionApi.startSession(serverUrl, sessionId, listOf(localPartyId))

        vultiSignerRepository.joinKeysign(
            JoinKeysignRequestJson(
                publicKeyEcdsa = input.vault.pubKeyECDSA,
                messages = listOf(input.messageHashHex),
                sessionId = sessionId,
                hexEncryptionKey = encryptionKeyHex,
                derivePath = btcDerivationPath,
                isEcdsa = true,
                password = input.fastVaultPassword,
                chain = Chain.Bitcoin.raw,
                mldsa = false,
            )
        )

        val peers =
            withTimeout(SERVER_WAIT_TIMEOUT) {
                discoverParticipants(serverUrl, sessionId, localPartyId).first { it.isNotEmpty() }
            }
        val committee = (listOf(localPartyId) + peers).distinct()
        sessionApi.startWithCommittee(serverUrl, sessionId, committee)

        return runDklsRound(
            vault = input.vault,
            committee = committee,
            serverUrl = serverUrl,
            sessionId = sessionId,
            encryptionKeyHex = encryptionKeyHex,
            messageHashHex = input.messageHashHex,
            isInitiateDevice = true,
            sessionApi = sessionApi,
            encryption = encryption,
        )
    }

    private companion object {
        val SERVER_WAIT_TIMEOUT = 60.seconds
    }
}

/**
 * Secure Vault initiator round. The QR handshake (session, peer discovery, committee kick-off) has
 * already happened on the pairing step, so this only runs the DKLS round on the established
 * [session] as the initiating device. Mirrors iOS `QBTCClaimSecureVaultRoundDriver`.
 */
internal class QbtcClaimSecureVaultRoundRunner(
    private val session: QbtcClaimKeysignSession,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
) : QbtcClaimBtcRoundRunner {

    override suspend fun run(input: QbtcClaimBtcRoundInput): QbtcClaimBtcRoundResult =
        runDklsRound(
            vault = input.vault,
            committee = session.committee,
            serverUrl = session.serverUrl,
            sessionId = session.sessionId,
            encryptionKeyHex = session.encryptionKeyHex,
            messageHashHex = input.messageHashHex,
            isInitiateDevice = true,
            sessionApi = sessionApi,
            encryption = encryption,
        )
}
