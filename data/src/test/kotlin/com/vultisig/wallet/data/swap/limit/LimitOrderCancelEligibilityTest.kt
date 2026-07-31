package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.LimitOrderStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * Eligibility fails closed at every unknown, because what it guards against is not an error dialog
 * — it is a cancel the chain accepts, charges for, and matches nothing with.
 */
internal class LimitOrderCancelEligibilityTest {

    private val fullUsdc = "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"

    private fun order(
        status: LimitOrderStatus = LimitOrderStatus.Pending,
        sourceChain: String? = Chain.ThorChain.raw,
        sourceAmount1e8: String? = "100000000",
        tradeTarget: String? = "4000000",
        sourceAsset: String = "THOR.RUNE",
        targetAsset: String = "BTC.BTC",
        sourceAssetFull: String? = "THOR.RUNE",
        targetAssetFull: String? = "BTC.BTC",
        observedSourceAsset: String? = null,
        observedTargetAsset: String? = null,
        depositAmount: String? = null,
        observedTradeTarget: String? = null,
        cancelBroadcastHash: String? = null,
        inboundTxHash: String = "HASH",
        createdAt: Long = 0L,
        sourceAddress: String? = "thor1abc",
        // Observed resting by default: every case below is about a live order, and an order the
        // queue has never reported is blocked for its own reason (see the dedicated test).
        expiryObservedAt: Long? = 1L,
    ) =
        PendingLimitOrderEntity(
            inboundTxHash = inboundTxHash,
            vaultId = "vault",
            sourceAsset = sourceAsset,
            sourceAmount = "100000000",
            targetAsset = targetAsset,
            destAddr = "bc1qxy",
            targetPrice = "0.04",
            expiryBlocks = 14_400,
            createdAt = createdAt,
            status = status.raw,
            sourceChain = sourceChain,
            sourceDecimals = 8,
            sourceAddress = sourceAddress,
            sourceAmount1e8 = sourceAmount1e8,
            tradeTarget = tradeTarget,
            sourceAssetFull = sourceAssetFull,
            targetAssetFull = targetAssetFull,
            observedSourceAsset = observedSourceAsset,
            observedTargetAsset = observedTargetAsset,
            observedTradeTarget = observedTradeTarget,
            depositAmount = depositAmount,
            expiryObservedAt = expiryObservedAt,
            cancelBroadcastHash = cancelBroadcastHash,
        )

    @Test
    fun `a resting order with complete signed data is cancellable`() {
        val eligibility = limitOrderCancelEligibility(order())

        eligibility.shouldBeInstanceOf<LimitOrderCancelEligibility.Cancellable>()
        eligibility.memo shouldBe "m=<:100000000THOR.RUNE:4000000BTC.BTC:0"
    }

    @Test
    fun `a closed order is not cancellable`() {
        blockerOf(order(status = LimitOrderStatus.Filled)) shouldBe LimitOrderCancelBlocker.Terminal
    }

    @Test
    fun `an order with a cancel already broadcast is not cancelled twice`() {
        // The order stays non-terminal so a cancel that matched nothing remains visible, but a live
        // button would let the user pay the fee again for an identical memo.
        blockerOf(
            order(status = LimitOrderStatus.Cancelling, cancelBroadcastHash = "0xcancel")
        ) shouldBe LimitOrderCancelBlocker.CancelAlreadyBroadcast
    }

    @Test
    fun `an order placed before the cancel inputs were recorded is blocked`() {
        blockerOf(order(sourceAmount1e8 = null)) shouldBe LimitOrderCancelBlocker.MissingSignedData
        blockerOf(order(tradeTarget = null)) shouldBe LimitOrderCancelBlocker.MissingSignedData
    }

    @Test
    fun `an order that can never be polled says so instead of promising a poll`() {
        // Both columns arrived with the tracking work, so an order missing either was placed by an
        // earlier build. The queue endpoint is scoped by sender, so such an order is skipped by
        // every poll — and `PlacementNotObserved`, which is what it used to report, promises it
        // becomes cancellable "once its deposit confirms". That never happens.
        blockerOf(order(sourceChain = null)) shouldBe
            LimitOrderCancelBlocker.LegacyOrderNotTrackable
        blockerOf(order(sourceAddress = null)) shouldBe
            LimitOrderCancelBlocker.LegacyOrderNotTrackable
        blockerOf(order(sourceAddress = "")) shouldBe
            LimitOrderCancelBlocker.LegacyOrderNotTrackable
    }

    @Test
    fun `an untrackable order reports that ahead of the resting gate`() {
        // Ordering matters: a legacy order has never been observed either, so whichever check runs
        // first decides what the card says for the rest of the order's life.
        blockerOf(order(sourceAddress = null, expiryObservedAt = null)) shouldBe
            LimitOrderCancelBlocker.LegacyOrderNotTrackable
    }

    @Test
    fun `an unroutable source chain has no inbound to cancel through`() {
        blockerOf(order(sourceChain = Chain.Polygon.raw)) shouldBe
            LimitOrderCancelBlocker.UnsupportedSourceChain
    }

    @Test
    fun `an amount that disagrees with the queue blocks rather than guessing`() {
        // state.deposit IS the swap's Tx.Coins[0].Amount, i.e. half the pair the matcher's ratio is
        // computed from. One of the two is wrong and there is no way to tell which.
        blockerOf(order(depositAmount = "99999999")) shouldBe
            LimitOrderCancelBlocker.SignedDataDisagreesWithChain
        blockerOf(order(observedTradeTarget = "4000001")) shouldBe
            LimitOrderCancelBlocker.SignedDataDisagreesWithChain
    }

    @Test
    fun `an observation that is present but unparseable blocks like a mismatch`() {
        // "Absent" is a poll that has not happened; "present but not understood" means the wire
        // carried something this code does not model, and signing would use unverified amounts.
        blockerOf(order(depositAmount = "not-a-number")) shouldBe
            LimitOrderCancelBlocker.SignedDataDisagreesWithChain
    }

    @Test
    fun `a resting order whose observation carried no amounts is still cancellable`() {
        // Absence is NOT disagreement: a poll that reported the order resting without echoing its
        // amounts leaves the signed values as the only source, and they are exact.
        limitOrderCancelEligibility(order(depositAmount = null, observedTradeTarget = null))
            .isCancellable shouldBe true
    }

    @Test
    fun `an order the queue has never reported cannot be cancelled yet`() {
        // Cancelling here cannot work and can do harm: THORChain has not observed the deposit, so
        // there is no resting order to match — and on a UTXO source the cancel is built from a UTXO
        // set that still lists the inputs the unconfirmed placement is spending, so the two
        // conflict.
        blockerOf(order(expiryObservedAt = null, depositAmount = null)) shouldBe
            LimitOrderCancelBlocker.PlacementNotObserved
    }

    @Test
    fun `a deposit amount alone proves the order was seen resting`() {
        limitOrderCancelEligibility(order(expiryObservedAt = null, depositAmount = "100000000"))
            .isCancellable shouldBe true
    }

    @Test
    fun `the queue's own spelling rescues a legacy order with no recorded full asset`() {
        val eligibility =
            limitOrderCancelEligibility(
                order(
                    sourceAsset = "ETH.USDC-06EB48",
                    sourceAssetFull = null,
                    observedSourceAsset = fullUsdc,
                    sourceChain = Chain.Ethereum.raw,
                )
            )

        eligibility.shouldBeInstanceOf<LimitOrderCancelEligibility.Cancellable>()
        eligibility.inputs.sourceAsset shouldBe fullUsdc
    }

    @Test
    fun `a legacy order with only an abbreviated spelling stays blocked`() {
        blockerOf(
            order(
                sourceAsset = "ETH.USDC-06EB48",
                sourceAssetFull = null,
                sourceChain = Chain.Ethereum.raw,
            )
        ) shouldBe LimitOrderCancelBlocker.MissingSignedData
    }

    @Test
    fun `an asset that disagrees with the chain blocks`() {
        // The check the amounts alone would have passed: the assets are what key the bucket too.
        blockerOf(order(observedTargetAsset = "ETH.ETH")) shouldBe
            LimitOrderCancelBlocker.SignedDataDisagreesWithChain
    }

    @Test
    fun `case alone is not a disagreement`() {
        // This app emits a secured denom lower-case and THORChain reports it upper-case.
        limitOrderCancelEligibility(
                order(
                    sourceAsset = "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                    sourceAssetFull = "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                    observedSourceAsset = "ETH-USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48",
                )
            )
            .isCancellable shouldBe true
    }

    @Test
    fun `an ERC20 target from a UTXO source cannot fit the cancel memo`() {
        blockerOf(
            order(
                sourceChain = Chain.Bitcoin.raw,
                sourceAsset = "BTC.BTC",
                sourceAssetFull = "BTC.BTC",
                targetAsset = fullUsdc,
                targetAssetFull = fullUsdc,
            )
        ) shouldBe LimitOrderCancelBlocker.MemoTooLongForSourceChain
    }

    @Test
    fun `orders sharing a ratio bucket are reported as duplicates`() {
        val target = order(inboundTxHash = "A", createdAt = 2L)
        val sameRatio =
            order(
                inboundTxHash = "B",
                createdAt = 1L,
                sourceAmount1e8 = "200000000",
                tradeTarget = "8000000",
            )
        val differentRatio = order(inboundTxHash = "C", createdAt = 3L, tradeTarget = "9000000")

        duplicateRestingLimitOrders(target, listOf(target, sameRatio, differentRatio)).map {
            it.inboundTxHash
        } shouldBe listOf("B")
    }

    @Test
    fun `a broadcast cancel is attributed to the oldest order its memo addresses`() {
        val older = order(inboundTxHash = "OLD", createdAt = 1L)
        val newer = order(inboundTxHash = "NEW", createdAt = 2L)

        // Matched on the memo, because the memo IS the addressing — and ties go to the oldest,
        // mirroring THORNode modifying the first match in the bucket.
        findOrderAddressedByCancelMemo(
                memo = "m=<:100000000THOR.RUNE:4000000BTC.BTC:0",
                orders = listOf(newer, older),
            )
            ?.inboundTxHash shouldBe "OLD"
    }

    @Test
    fun `a cancel memo that matches no stored order is attributed to none`() {
        findOrderAddressedByCancelMemo(
            memo = "m=<:1THOR.RUNE:2BTC.BTC:0",
            orders = listOf(order()),
        ) shouldBe null
    }

    private fun blockerOf(order: PendingLimitOrderEntity): LimitOrderCancelBlocker =
        (limitOrderCancelEligibility(order) as LimitOrderCancelEligibility.Blocked).blocker
}
