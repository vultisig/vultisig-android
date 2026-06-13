package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.ComputeQbtcClaimMessageHashUseCase
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.qbtc.QbtcClaimPeerRoundRunner
import com.vultisig.wallet.data.qbtc.QbtcClaimResultPoller
import javax.inject.Inject

/** Thrown when a QBTC claim co-sign cannot find the [chain] account it needs in this vault. */
internal class MissingQbtcClaimAccountException(chain: Chain) :
    Exception("Missing ${chain.raw} account for QBTC claim co-sign")

/** The broadcast result of a QBTC claim co-sign, or nulls while the initiator hasn't pushed it. */
internal data class QbtcClaimCosignResult(val txHash: String?, val totalSats: Long?)

/**
 * Co-signs a QBTC claim as the second device. The claim hash is recomputed locally from this
 * vault's own Bitcoin + QBTC accounts (never trusted from the QR), then DKLS runs as the
 * non-initiating device; afterwards we poll the relay for the initiator's broadcast result so the
 * done screen can show the tx hash.
 *
 * Extracted verbatim from `JoinKeysignViewModel.startQbtcClaimCosign` — the job launching, state
 * transitions, and error mapping stay in the ViewModel; this use case owns the suspend co-sign
 * work.
 */
internal class QbtcClaimCosignUseCase
@Inject
constructor(
    private val computeQbtcClaimMessageHash: ComputeQbtcClaimMessageHashUseCase,
    private val qbtcClaimPeerRoundRunner: QbtcClaimPeerRoundRunner,
    private val qbtcClaimResultPoller: QbtcClaimResultPoller,
) {
    suspend operator fun invoke(
        vault: Vault,
        serverUrl: String,
        sessionId: String,
        encryptionKeyHex: String,
    ): QbtcClaimCosignResult {
        val btc =
            vault.coins.firstOrNull { it.chain == Chain.Bitcoin }
                ?: throw MissingQbtcClaimAccountException(Chain.Bitcoin)
        val qbtc =
            vault.coins.firstOrNull { it.chain == Chain.Qbtc }
                ?: throw MissingQbtcClaimAccountException(Chain.Qbtc)
        val messageHashHex =
            computeQbtcClaimMessageHash(btc.address, btc.hexPublicKey, qbtc.address)
        val session =
            qbtcClaimPeerRoundRunner.coSign(
                vault = vault,
                messageHashHex = messageHashHex,
                serverUrl = serverUrl,
                sessionId = sessionId,
                encryptionKeyHex = encryptionKeyHex,
            )
        val result = qbtcClaimResultPoller.awaitResult(session)
        return QbtcClaimCosignResult(txHash = result?.txHash, totalSats = result?.totalSats)
    }
}
