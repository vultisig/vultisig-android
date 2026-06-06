package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.models.quotes.SwapKitTrackResponseJson
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import java.util.Locale

/**
 * Pure mapping from SwapKit's `/track` status fields to the app's [TransactionResult]. Ported from
 * iOS' `SwapKitTrackingStatusMapper`, collapsed onto Android's status model — which has no distinct
 * "swapping" state, so an in-flight destination leg is [TransactionResult.Pending] until it
 * settles.
 *
 * The fine-grained `trackingStatus` (14-value) wins over the coarse `status` (7-value) when both
 * are present: `outbound`/`swapping` mean the destination leg is still moving, which the coarse
 * field would otherwise round up. Unrecognised values fall through to [TransactionResult.Pending]
 * so a future SwapKit-side enum value keeps the row in-flight instead of flipping it terminal.
 *
 * Full `trackingStatus` → result table (see the SwapKit tx-history plan §"State mapping"):
 * ```
 *   not_started, starting, broadcasted, mempool, inbound, outbound, swapping, unknown → Pending
 *   completed                                                                         → Confirmed
 *   refunded, partially_refunded                                                      → Refunded
 *   dropped, reverted, replaced, retries_exceeded, parsing_error, failed              → Failed
 * ```
 */
internal object SwapKitTrackingStatusMapper {

    fun map(response: SwapKitTrackResponseJson): TransactionResult {
        response.trackingStatus
            ?.takeIf { it.isNotBlank() }
            ?.let {
                return mapTrackingStatus(it)
            }
        return mapCoarseStatus(response.status)
    }

    private fun mapTrackingStatus(raw: String): TransactionResult =
        when (val status = raw.lowercase(Locale.ROOT)) {
            "not_started",
            "starting",
            "broadcasted",
            "mempool",
            "inbound",
            "outbound",
            "swapping",
            "unknown" -> TransactionResult.Pending
            "completed" -> TransactionResult.Confirmed
            "refunded",
            "partially_refunded" -> TransactionResult.Refunded("SwapKit refunded: $status")
            "dropped",
            "reverted",
            "replaced",
            "retries_exceeded",
            "parsing_error",
            "failed" -> TransactionResult.Failed("SwapKit failed: $status")
            else -> TransactionResult.Pending
        }

    private fun mapCoarseStatus(status: String?): TransactionResult =
        when (status?.lowercase(Locale.ROOT)) {
            "completed" -> TransactionResult.Confirmed
            "refunded" -> TransactionResult.Refunded("SwapKit refunded")
            "failed" -> TransactionResult.Failed("SwapKit failed")
            // not_started / pending / swapping / unknown / null → destination leg still settling.
            else -> TransactionResult.Pending
        }
}
