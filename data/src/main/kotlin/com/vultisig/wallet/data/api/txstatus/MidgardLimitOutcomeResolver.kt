package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

/** Why a limit order left THORChain's advanced swap queue. */
enum class LimitOrderOutcome {
    /** The order executed. */
    Filled,

    /**
     * THORChain says the order was cancelled — its own account, read off the refund action's
     * reason. Independent of whether this device sent the cancel.
     */
    Cancelled,

    /** THORChain says the order's TTL elapsed. Never inferred locally. */
    Expired,

    /** The funds came back and THORChain gave no reason this code recognises. */
    Refunded,

    /**
     * Nothing conclusive yet — almost always Midgard indexing lag. The caller must leave the order
     * resting and ask again: a guess here is permanent, because nothing revisits a terminal order.
     */
    Unresolved,
}

/**
 * Resolves why a limit order closed, using Midgard's `/v2/actions?txid=` index of the order's
 * inbound deposit.
 *
 * The queue endpoint reports only that an order is gone, never why, so this is the second half of
 * the tracker. It deliberately reports [LimitOrderOutcome.Unresolved] rather than guessing on
 * anything it does not recognise — a network failure, an empty index, a reason THORChain has
 * reworded. The alternative is a terminal label invented from silence, which is exactly the false
 * certainty this feature exists to avoid.
 *
 * The `cancelled` / `expired` split comes from THORChain's own words: its refund action carries
 * `limit swap cancelled` / `limit swap expired` verbatim in `metadata.refund.reason`. A refund with
 * any other reason stays [LimitOrderOutcome.Refunded] — the observable fact, with no cause
 * attached. An order rejected at placement (halted pool, malformed memo) also refunds seconds in
 * with no TTL elapsed, so inventing a cause here would be inventing it for that user too.
 */
class MidgardLimitOutcomeResolver @Inject constructor(private val httpClient: HttpClient) {

    suspend fun resolveOutcome(inboundTxHash: String): LimitOrderOutcome =
        try {
            val response: MidgardLimitActionsResponse =
                httpClient
                    .get(THORCHAIN_MIDGARD_ACTIONS_URL) { parameter(TXID_PARAM, inboundTxHash) }
                    .bodyOrThrow()
            // Midgard indexes the swap and its refund as separate actions against the same inbound.
            // A settled swap is the stronger claim, so it wins over a refund of the unfilled
            // remainder — an order that filled 60% and refunded the rest did fill.
            response.actions.firstNotNullOfOrNull(::classify) ?: LimitOrderOutcome.Unresolved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Limit-order outcome lookup failed for %s", inboundTxHash)
            LimitOrderOutcome.Unresolved
        }

    /** Null when this action says nothing about the order's fate. */
    private fun classify(action: MidgardLimitAction): LimitOrderOutcome? =
        when (action.type) {
            ACTION_TYPE_SWAP ->
                if (action.status == ACTION_STATUS_SUCCESS) LimitOrderOutcome.Filled else null

            ACTION_TYPE_REFUND -> {
                val reason = action.metadata?.refund?.reason.orEmpty().lowercase()
                when {
                    reason.contains(REASON_CANCELLED) -> LimitOrderOutcome.Cancelled
                    reason.contains(REASON_EXPIRED) -> LimitOrderOutcome.Expired
                    else -> LimitOrderOutcome.Refunded
                }
            }

            else -> null
        }

    private companion object {
        const val THORCHAIN_MIDGARD_ACTIONS_URL =
            "https://gateway.liquify.com/chain/thorchain_midgard/v2/actions"
        const val TXID_PARAM = "txid"
        const val ACTION_TYPE_SWAP = "swap"
        const val ACTION_TYPE_REFUND = "refund"
        const val ACTION_STATUS_SUCCESS = "success"
        // Substrings, not equality: THORChain's reason is a sentence ("limit swap cancelled") and
        // the wording around these two words has moved before without changing their meaning.
        const val REASON_CANCELLED = "cancel"
        const val REASON_EXPIRED = "expire"
    }
}

@Serializable
internal data class MidgardLimitActionsResponse(
    @SerialName("actions") val actions: List<MidgardLimitAction> = emptyList()
)

@Serializable
internal data class MidgardLimitAction(
    @SerialName("type") val type: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("metadata") val metadata: MidgardLimitActionMetadata? = null,
)

@Serializable
internal data class MidgardLimitActionMetadata(
    @SerialName("refund") val refund: MidgardLimitRefund? = null
)

@Serializable
internal data class MidgardLimitRefund(@SerialName("reason") val reason: String? = null)
