package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** Why the QBTC claim flow can't proceed. Mirrors iOS `QBTCClaimBlockedReason`. */
sealed interface QbtcClaimBlockedReason {
    data object KillSwitchClosed : QbtcClaimBlockedReason

    data class UnsupportedBtcAddress(val detail: String) : QbtcClaimBlockedReason

    data class UtxoFetchFailed(val message: String) : QbtcClaimBlockedReason

    data object NoUtxos : QbtcClaimBlockedReason
}

sealed interface QbtcClaimLoadResult {
    data class Available(val utxos: List<ClaimableUtxo>) : QbtcClaimLoadResult

    data class Blocked(val reason: QbtcClaimBlockedReason) : QbtcClaimLoadResult
}

/**
 * Runs the claim eligibility pipeline for a Bitcoin address: address-type guard, kill-switch check,
 * Blockchair UTXO fetch, then the chain's already-claimed cross-check. Any failure that would block
 * a real claim collapses to a [QbtcClaimLoadResult.Blocked] reason so the UI never lies about
 * what's claimable. Mirrors iOS `QBTCClaimViewModel.load`.
 */
interface LoadClaimableQbtcUtxosUseCase {
    suspend operator fun invoke(btcAddress: String): QbtcClaimLoadResult
}

internal class LoadClaimableQbtcUtxosUseCaseImpl
@Inject
constructor(
    private val chainService: QbtcClaimChainService,
    private val utxosService: QbtcClaimableUtxosService,
) : LoadClaimableQbtcUtxosUseCase {

    override suspend fun invoke(btcAddress: String): QbtcClaimLoadResult {
        // Address-type guard up front — P2TR / testnet fail before any network call.
        try {
            BtcAddressType.detect(btcAddress)
        } catch (e: UnsupportedBtcAddressException) {
            return QbtcClaimLoadResult.Blocked(
                QbtcClaimBlockedReason.UnsupportedBtcAddress(e.message.orEmpty())
            )
        }

        // Kill-switch fails CLOSED: if we can't confirm it's open, don't offer the claim.
        val disabled =
            try {
                chainService.isClaimWithProofDisabled()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return QbtcClaimLoadResult.Blocked(QbtcClaimBlockedReason.KillSwitchClosed)
            }
        if (disabled) {
            return QbtcClaimLoadResult.Blocked(QbtcClaimBlockedReason.KillSwitchClosed)
        }

        val filtered =
            try {
                val candidates = utxosService.fetchClaimableCandidates(btcAddress)
                chainService.filterClaimable(candidates)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return QbtcClaimLoadResult.Blocked(
                    QbtcClaimBlockedReason.UtxoFetchFailed(e.message.orEmpty())
                )
            }

        return if (filtered.isEmpty()) {
            QbtcClaimLoadResult.Blocked(QbtcClaimBlockedReason.NoUtxos)
        } else {
            QbtcClaimLoadResult.Available(filtered)
        }
    }
}
