package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** Phase of the claim run, surfaced to the UI. Mirrors iOS `QBTCClaimPhase`. */
sealed interface QbtcClaimPhase {
    data object Idle : QbtcClaimPhase

    data object SigningBtc : QbtcClaimPhase

    data object GeneratingProofAndBroadcasting : QbtcClaimPhase

    data class Done(val result: QbtcClaimRunResult) : QbtcClaimPhase

    data class Failed(val errorKind: QbtcClaimError) : QbtcClaimPhase
}

data class QbtcClaimRunResult(val txHashHex: String, val totalSatsClaimed: Long)

/** Distinguishable failure reasons so the UI can show specific copy. */
enum class QbtcClaimError {
    INVALID_BTC_PUBLIC_KEY,
    PROOF_HASH_MISMATCH,
    BROADCAST_UNAVAILABLE,
    PAIRING_TIMEOUT,
    GENERIC,
}

/** What the orchestrator hands to the BTC ECDSA round runner. */
data class QbtcClaimBtcRoundInput(
    val vault: Vault,
    val btcCoin: Coin,
    val messageHashHex: String,
    /** Blank for a Secure Vault; the password for a Fast Vault server co-sign. */
    val fastVaultPassword: String,
)

/** `(r, s)` of the BTC ECDSA signature, hex-encoded, ready for the proof service. */
data class QbtcClaimBtcRoundResult(val rHex: String, val sHex: String)

/** Runs the single BTC ECDSA TSS round over the claim message hash. */
interface QbtcClaimBtcRoundRunner {
    suspend fun run(input: QbtcClaimBtcRoundInput): QbtcClaimBtcRoundResult
}

/** Best-effort push of the broadcast result to a co-signing peer (Secure Vault). */
fun interface QbtcClaimPeerResultPusher {
    suspend fun push(txHashHex: String, totalSats: Long)
}

data class QbtcClaimRunInput(
    val vault: Vault,
    val btcCoin: Coin,
    val qbtcCoin: Coin,
    val utxos: List<ClaimableUtxo>,
    val fastVaultPassword: String = "",
)

/**
 * Drives the post-qbtc#158 claim flow: compute hashes → one BTC ECDSA round → `POST /prove
 * {broadcast:true}` (the proof service signs `MsgClaimWithProof` with its own MLDSA-44 key and
 * broadcasts it) → done. The client never builds or broadcasts a cosmos tx. Collaborators are
 * injected so the orchestration is unit-testable with fakes. Mirrors iOS `QBTCClaimOrchestrator`.
 */
class QbtcClaimOrchestrator(
    private val proofService: QbtcProofService,
    private val btcRoundRunner: QbtcClaimBtcRoundRunner,
    private val peerResultPusher: QbtcClaimPeerResultPusher? = null,
    private val chainId: String = QbtcClaimConfig.CHAIN_ID,
) {
    private val _phase = MutableStateFlow<QbtcClaimPhase>(QbtcClaimPhase.Idle)
    val phase: StateFlow<QbtcClaimPhase> = _phase.asStateFlow()

    fun reset() {
        _phase.value = QbtcClaimPhase.Idle
    }

    suspend fun run(input: QbtcClaimRunInput) {
        try {
            runInternal(input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: QbtcClaimException) {
            Timber.e(e, "QBTC claim failed: %s", e.kind)
            _phase.value = QbtcClaimPhase.Failed(e.kind)
        } catch (e: Exception) {
            Timber.e(e, "QBTC claim failed")
            _phase.value = QbtcClaimPhase.Failed(QbtcClaimError.GENERIC)
        }
    }

    private suspend fun runInternal(input: QbtcClaimRunInput) {
        val compressedPubkey =
            runCatching { input.btcCoin.hexPublicKey.hexToByteArrayOrNull() }.getOrNull()
                ?: throw QbtcClaimException(QbtcClaimError.INVALID_BTC_PUBLIC_KEY)

        // Cheap, fail-fast hashing — rejects schnorr / wrong-length keys before any network.
        val hashes =
            QbtcClaimHashes.computeAll(
                btcAddress = input.btcCoin.address,
                compressedPubkey = compressedPubkey,
                qbtcAddress = input.qbtcCoin.address,
                chainId = chainId,
            )
        val messageHashHex = hashes.messageHash.toHex()

        _phase.value = QbtcClaimPhase.SigningBtc
        val btcSig =
            btcRoundRunner.run(
                QbtcClaimBtcRoundInput(
                    vault = input.vault,
                    btcCoin = input.btcCoin,
                    messageHashHex = messageHashHex,
                    fastVaultPassword = input.fastVaultPassword,
                )
            )

        _phase.value = QbtcClaimPhase.GeneratingProofAndBroadcasting
        val proof =
            proofService.generateProof(
                ClaimProofRequest.create(
                    rHex = btcSig.rHex,
                    sHex = btcSig.sHex,
                    compressedPubkeyHex = input.btcCoin.hexPublicKey,
                    utxos = input.utxos,
                    claimerAddress = input.qbtcCoin.address,
                    chainId = chainId,
                    broadcast = true,
                )
            )

        // The service recomputes and broadcasts on our behalf; a drift in the
        // echoed hashes means it acted on data we didn't authorize — fail loudly.
        if (
            !proof.messageHash.equals(messageHashHex, ignoreCase = true) ||
                !proof.addressHash.equals(hashes.addressHash.toHex(), ignoreCase = true) ||
                !proof.qbtcAddressHash.equals(hashes.qbtcAddressHash.toHex(), ignoreCase = true)
        ) {
            throw QbtcClaimException(QbtcClaimError.PROOF_HASH_MISMATCH)
        }

        val txHash = proof.txHash
        if (txHash.isNullOrEmpty()) {
            throw QbtcClaimException(QbtcClaimError.BROADCAST_UNAVAILABLE)
        }

        val totalSats = input.utxos.sumOf { it.amount }
        val uppercasedTxHash = txHash.uppercase()

        // Best-effort: a failure to notify the peer must not fail the claim.
        peerResultPusher?.let { pusher ->
            try {
                pusher.push(uppercasedTxHash, totalSats)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to push claim result to peer")
            }
        }

        _phase.value = QbtcClaimPhase.Done(QbtcClaimRunResult(uppercasedTxHash, totalSats))
    }
}

private class QbtcClaimException(val kind: QbtcClaimError) : Exception()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToByteArrayOrNull(): ByteArray? {
    if (length % 2 != 0) return null
    return runCatching { chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
}
