package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

/**
 * Resolves THORChain / MayaChain inbound-tx status using Midgard `/v2/actions?txid=...`.
 *
 * The previous implementation treated any HTTP 200 from `/thorchain/tx/status/{hash}` as
 * [TransactionResult.Confirmed], but the network returns 200 for both completed and refunded
 * inbound txs — so refunded LP/swap/savers/loan/bond txs were shown as successful in history.
 * Midgard's `actions` endpoint exposes the action `type` ("refund"/"failed" vs
 * "swap"/"addLiquidity"/…) and a human-readable reason in `metadata.refund.reason` or
 * `metadata.failed.reason`, which is what we surface in the UI. `type == "failed"` (e.g. deposits
 * paused, slip-limit hit) is reported with `status == "success"` by Midgard; the network usually
 * refunds those, but the refund is a separate outbound tx and isn't observable until it appears in
 * `action.out`. We therefore only classify `type == "failed"` as [TransactionResult.Refunded] once
 * at least one outbound tx with a non-blank txID is present; otherwise we report
 * [TransactionResult.Failed] so we don't tell the user their funds are back while they're still in
 * flight (or were never refunded).
 *
 * Note the deliberate asymmetry: `type == "refund"` is reported as [TransactionResult.Refunded]
 * immediately without checking `action.out`. The network has explicitly decided to refund, and
 * that's the user-facing answer regardless of whether the outbound leg has landed yet — observing
 * the outbound is the network's bookkeeping, not new information for the user. `type == "failed"`
 * carries no such commitment, which is why it gates on the outbound being observed.
 */
class ThorMayaChainStatusProvider @Inject constructor(private val httpClient: HttpClient) :
    TransactionStatusProvider {

    private val midgardUrls =
        mapOf(
            Chain.ThorChain to THORCHAIN_MIDGARD_ACTIONS_URL,
            Chain.MayaChain to MAYACHAIN_MIDGARD_ACTIONS_URL,
        )

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        val baseUrl = midgardUrls[chain] ?: return TransactionResult.Failed("Unknown chain")
        return try {
            val response: MidgardActionsResponse =
                httpClient.get(baseUrl) { parameter(MIDGARD_TXID_PARAM, txHash) }.bodyOrThrow()
            val action = response.actions.firstOrNull() ?: return TransactionResult.Pending
            mapAction(action)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "THOR/Maya status check failed for %s on %s", txHash, chain)
            TransactionResult.Pending
        }
    }

    private fun mapAction(action: MidgardAction): TransactionResult =
        when {
            action.type == ACTION_TYPE_REFUND ->
                TransactionResult.Refunded(
                    reason = nonBlankOr(action.metadata?.refund?.reason, DEFAULT_REFUND_REASON)
                )
            action.type == ACTION_TYPE_FAILED -> {
                val failed = action.metadata?.failed
                Timber.w("Midgard reported failure: code=%s memo=%s", failed?.code, failed?.memo)
                val reason = nonBlankOr(failed?.reason, DEFAULT_FAILED_REASON)
                if (action.out.any { !it.txID.isNullOrBlank() }) TransactionResult.Refunded(reason)
                else TransactionResult.Failed(reason)
            }
            action.status == ACTION_STATUS_SUCCESS -> TransactionResult.Confirmed
            else -> TransactionResult.Pending
        }

    private fun nonBlankOr(value: String?, default: String): String =
        value?.takeUnless { it.isBlank() } ?: default

    private companion object {
        const val THORCHAIN_MIDGARD_ACTIONS_URL =
            "https://gateway.liquify.com/chain/thorchain_midgard/v2/actions"
        const val MAYACHAIN_MIDGARD_ACTIONS_URL = "https://midgard.mayachain.info/v2/actions"
        const val MIDGARD_TXID_PARAM = "txid"
        const val ACTION_TYPE_REFUND = "refund"
        const val ACTION_TYPE_FAILED = "failed"
        const val ACTION_STATUS_SUCCESS = "success"
        const val DEFAULT_REFUND_REASON = "Transaction refunded"
        const val DEFAULT_FAILED_REASON = "Transaction failed"
    }
}

@Serializable
internal data class MidgardActionsResponse(val actions: List<MidgardAction> = emptyList())

@Serializable
internal data class MidgardAction(
    @SerialName("type") val type: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("out") val out: List<MidgardTransaction> = emptyList(),
    @SerialName("metadata") val metadata: MidgardActionMetadata? = null,
)

@Serializable internal data class MidgardTransaction(@SerialName("txID") val txID: String? = null)

@Serializable
internal data class MidgardActionMetadata(
    @SerialName("refund") val refund: MidgardRefund? = null,
    @SerialName("failed") val failed: MidgardFailedMetadata? = null,
)

@Serializable internal data class MidgardRefund(@SerialName("reason") val reason: String? = null)

@Serializable
internal data class MidgardFailedMetadata(
    @SerialName("code") val code: String? = null,
    @SerialName("memo") val memo: String? = null,
    @SerialName("reason") val reason: String? = null,
)
