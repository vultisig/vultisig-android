package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.models.quotes.SwapKitTrackRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitTrackResponseJson
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Pins the SwapKit settlement gate:
 * - [SwapKitTrackingService.canTrack] follows the chain catalogue (EVM/Solana yes, Polkadot no).
 * - `/track` is queried with the broadcast hash + the source chain's `/track` chainId.
 * - the response maps through [SwapKitTrackingStatusMapper].
 * - a transient `/track` failure (or an untrackable chain) returns Pending so polling keeps running
 *   rather than flipping the row terminal.
 */
class SwapKitTrackingServiceTest {

    private val api: SwapKitApi = mockk()

    private fun service(): SwapKitTrackingService = SwapKitTrackingServiceImpl(api)

    @Test
    fun `canTrack reflects the chain catalogue`() {
        with(service()) {
            canTrack(Chain.Ethereum) shouldBe true
            canTrack(Chain.Solana) shouldBe true
            canTrack(Chain.Polkadot) shouldBe false
        }
    }

    @Test
    fun `checkSettlementStatus queries track with broadcast hash and source chainId`() = runTest {
        val request = slot<SwapKitTrackRequest>()
        coEvery { api.track(capture(request)) } returns
            SwapKitTrackResponseJson(trackingStatus = "completed")

        val result = service().checkSettlementStatus("0xbroadcast", Chain.Ethereum)

        result shouldBe TransactionResult.Confirmed
        request.captured.hash shouldBe "0xbroadcast"
        request.captured.chainId shouldBe "1"
    }

    @Test
    fun `checkSettlementStatus maps an in-flight destination leg to Pending`() = runTest {
        coEvery { api.track(any()) } returns
            SwapKitTrackResponseJson(status = "completed", trackingStatus = "outbound")

        service().checkSettlementStatus("0xbroadcast", Chain.Ethereum) shouldBe
            TransactionResult.Pending
    }

    @Test
    fun `transient track failure returns Pending to keep polling`() = runTest {
        coEvery { api.track(any()) } throws RuntimeException("boom")

        service().checkSettlementStatus("0xbroadcast", Chain.Ethereum) shouldBe
            TransactionResult.Pending
    }

    @Test
    fun `untrackable chain returns Pending without hitting the API`() = runTest {
        service().checkSettlementStatus("0xbroadcast", Chain.Polkadot) shouldBe
            TransactionResult.Pending
    }

    @Test
    fun `refund maps to Refunded`() = runTest {
        coEvery { api.track(any()) } returns SwapKitTrackResponseJson(trackingStatus = "refunded")

        service()
            .checkSettlementStatus("0xbroadcast", Chain.Ethereum)
            .shouldBeInstanceOf<TransactionResult.Refunded>()
    }
}
