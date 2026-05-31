package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

/** On-chain status of a Bitcoin UTXO from the QBTC chain's point of view. */
internal sealed interface QbtcUtxoStatus {
    /** Still claimable; [entitledAmount] is the chain's remaining payout in satoshis. */
    data class Claimable(val entitledAmount: Long) : QbtcUtxoStatus

    /** Already paid out (`entitled_amount == 0`). */
    data object Claimed : QbtcUtxoStatus

    /** The chain has never registered this UTXO (404). */
    data object NotIndexed : QbtcUtxoStatus
}

/**
 * Queries the QBTC chain for claim eligibility: the `ClaimWithProofDisabled` kill-switch and
 * per-UTXO status. Mirrors iOS `QBTCChainService` and the SDK's `getClaimWithProofDisabled` /
 * `getClaimableUtxos` cross-check.
 */
internal interface QbtcClaimChainService {
    /** True when the chain has disabled the claim-with-proof feature. */
    suspend fun isClaimWithProofDisabled(): Boolean

    /**
     * Drops UTXOs the chain has already paid out or never indexed, and rewrites each survivor's
     * amount to the chain's remaining `entitled_amount`. Input order is preserved. Transient
     * per-UTXO errors fail OPEN — the UTXO is kept at its original amount rather than silently
     * dropped.
     */
    suspend fun filterClaimable(utxos: List<ClaimableUtxo>): List<ClaimableUtxo>
}

internal class QbtcClaimChainServiceImpl @Inject constructor(private val httpClient: HttpClient) :
    QbtcClaimChainService {

    override suspend fun isClaimWithProofDisabled(): Boolean {
        val response =
            httpClient
                .get("${Endpoints.QBTC_REST_URL}/qbtc/v1/params/ClaimWithProofDisabled")
                .bodyOrThrow<QbtcParamResponseJson>()
        val value =
            response.param.value.toIntOrNull()
                ?: error("Invalid ClaimWithProofDisabled value: ${response.param.value}")
        return value > 0
    }

    override suspend fun filterClaimable(utxos: List<ClaimableUtxo>): List<ClaimableUtxo> =
        coroutineScope {
            // Cap in-flight requests so a heavy wallet doesn't burst the chain endpoint
            // (which would throttle and then cascade into fail-open). Input order is preserved.
            val gate = Semaphore(MAX_CONCURRENT_STATUS_REQUESTS)
            utxos
                .map { utxo ->
                    async { utxo to gate.withPermit { fetchStatus(utxo.txid, utxo.vout) } }
                }
                .awaitAll()
                .mapNotNull { (utxo, status) ->
                    when (status) {
                        is QbtcUtxoStatus.Claimable -> utxo.copy(amount = status.entitledAmount)
                        QbtcUtxoStatus.Claimed,
                        QbtcUtxoStatus.NotIndexed -> null
                        // Transient failure: keep the UTXO at its blockchair amount.
                        null -> utxo
                    }
                }
        }

    /** Returns the UTXO status, or `null` on a transient (non-404) failure. */
    private suspend fun fetchStatus(txid: String, vout: Int): QbtcUtxoStatus? =
        try {
            val response = httpClient.get("${Endpoints.QBTC_REST_URL}/qbtc/v1/utxo/$txid/$vout")
            if (response.status == HttpStatusCode.NotFound) {
                QbtcUtxoStatus.NotIndexed
            } else {
                val entitled = response.bodyOrThrow<QbtcUtxoResponseJson>().utxo?.entitledAmount
                val amount = entitled?.toLongOrNull()
                // Only a strictly-positive remaining amount is claimable; 0 means fully paid
                // out and a negative value is invalid — treat both as non-claimable.
                if (amount == null || amount <= 0L) QbtcUtxoStatus.Claimed
                else QbtcUtxoStatus.Claimable(amount)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "QBTC UTXO status check failed for %s:%d — keeping it", txid, vout)
            null
        }

    private companion object {
        const val MAX_CONCURRENT_STATUS_REQUESTS = 8
    }
}

@Serializable private class QbtcParamResponseJson(val param: QbtcParam)

@Serializable private class QbtcParam(val key: String = "", val value: String)

@Serializable private class QbtcUtxoResponseJson(val utxo: QbtcUtxoEntry? = null)

@Serializable
private class QbtcUtxoEntry(
    val txid: String = "",
    @SerialName("entitled_amount") val entitledAmount: String? = null,
)
