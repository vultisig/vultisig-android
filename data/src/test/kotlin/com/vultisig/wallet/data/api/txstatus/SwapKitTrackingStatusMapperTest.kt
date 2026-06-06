package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.models.quotes.SwapKitTrackResponseJson
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * Pins the SwapKit `/track` status mapping (ported from iOS' SwapKitTrackingStatusMapper, collapsed
 * onto Android's [TransactionResult] model):
 * - in-flight `trackingStatus` (inbound / outbound / swapping / unknown / …) → Pending
 * - `completed` → Confirmed
 * - `refunded` / `partially_refunded` → Refunded
 * - terminal failures (dropped / reverted / replaced / retries_exceeded / parsing_error / failed) →
 *   Failed
 * - fine-grained `trackingStatus` wins over the coarse `status` when both are present
 * - unrecognised / missing values fall through to Pending so the row stays pollable
 */
class SwapKitTrackingStatusMapperTest {

    private fun response(
        status: String? = null,
        trackingStatus: String? = null,
    ): SwapKitTrackResponseJson =
        SwapKitTrackResponseJson(status = status, trackingStatus = trackingStatus)

    @Test
    fun `in-flight tracking statuses map to Pending`() {
        listOf(
                "not_started",
                "starting",
                "broadcasted",
                "mempool",
                "inbound",
                "outbound",
                "swapping",
                "unknown",
            )
            .forEach { raw ->
                SwapKitTrackingStatusMapper.map(response(trackingStatus = raw)) shouldBe
                    TransactionResult.Pending
            }
    }

    @Test
    fun `completed maps to Confirmed`() {
        SwapKitTrackingStatusMapper.map(response(trackingStatus = "completed")) shouldBe
            TransactionResult.Confirmed
    }

    @Test
    fun `refund statuses map to Refunded`() {
        listOf("refunded", "partially_refunded").forEach { raw ->
            SwapKitTrackingStatusMapper.map(response(trackingStatus = raw))
                .shouldBeInstanceOf<TransactionResult.Refunded>()
        }
    }

    @Test
    fun `terminal failures map to Failed`() {
        listOf("dropped", "reverted", "replaced", "retries_exceeded", "parsing_error", "failed")
            .forEach { raw ->
                SwapKitTrackingStatusMapper.map(response(trackingStatus = raw))
                    .shouldBeInstanceOf<TransactionResult.Failed>()
            }
    }

    @Test
    fun `casing is ignored`() {
        SwapKitTrackingStatusMapper.map(response(trackingStatus = "COMPLETED")) shouldBe
            TransactionResult.Confirmed
    }

    @Test
    fun `fine-grained trackingStatus wins over coarse status`() {
        // Coarse says completed, but the destination leg is still outbound → Pending.
        SwapKitTrackingStatusMapper.map(
            response(status = "completed", trackingStatus = "outbound")
        ) shouldBe TransactionResult.Pending
    }

    @Test
    fun `falls back to coarse status when trackingStatus is absent or blank`() {
        SwapKitTrackingStatusMapper.map(response(status = "completed")) shouldBe
            TransactionResult.Confirmed
        SwapKitTrackingStatusMapper.map(
            response(status = "completed", trackingStatus = "")
        ) shouldBe TransactionResult.Confirmed
        SwapKitTrackingStatusMapper.map(response(status = "failed"))
            .shouldBeInstanceOf<TransactionResult.Failed>()
    }

    @Test
    fun `unrecognised and empty values stay Pending`() {
        SwapKitTrackingStatusMapper.map(response(trackingStatus = "some_future_status")) shouldBe
            TransactionResult.Pending
        SwapKitTrackingStatusMapper.map(response()) shouldBe TransactionResult.Pending
    }
}
