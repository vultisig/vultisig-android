package com.vultisig.wallet.data.swap.limit

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * The dust an L1 cancel attaches has to clear two floors enforced by different systems, quoted in
 * different unit systems. Getting it wrong is silent: Bifrost ignores an under-funded inbound, so
 * the transaction confirms on the source chain, the fee is spent, and THORChain never learns it
 * happened.
 */
internal class LimitOrderCancelDustTest {

    @Test
    fun `rescales THORChain's 1e8 threshold into an 18-decimal chain's units`() {
        // The bug an 8-decimal chain cannot show: 1000 read as native wei is 2000 wei, which
        // ConvertAmount truncates to zero. In ETH's own units it is 2e13.
        limitOrderCancelDustAmount(
            localDustFloor = BigInteger.ZERO,
            inboundDustThreshold = "1000",
            decimals = 18,
            ceiling = BigInteger.TEN.pow(15),
            chainSymbol = "ETH",
        ) shouldBe BigInteger("20000000000000")
    }

    @Test
    fun `is the identity on an 8-decimal chain`() {
        limitOrderCancelDustAmount(
            localDustFloor = BigInteger.ZERO,
            inboundDustThreshold = "10000",
            decimals = 8,
            ceiling = BigInteger.TEN.pow(8),
            chainSymbol = "BTC",
        ) shouldBe BigInteger("20000")
    }

    @Test
    fun `rounds up when the chain carries fewer decimals than THORChain`() {
        // The value is a floor that has to be cleared; truncating would land a unit short of
        // exactly
        // the threshold it exists to satisfy.
        chainSmallestUnitsFromThorchainBaseUnits(BigInteger("1000001"), decimals = 6) shouldBe
            BigInteger("10001")
    }

    @Test
    fun `takes the larger of the two floors after rescaling them into one unit system`() {
        // BTC's local floor (546 sats) beats a threshold of 100 (1e8-scaled, so also 100 sats).
        limitOrderCancelDustAmount(
            localDustFloor = BigInteger("546"),
            inboundDustThreshold = "100",
            decimals = 8,
            ceiling = BigInteger.TEN.pow(8),
            chainSymbol = "BTC",
        ) shouldBe BigInteger("1092")
    }

    @Test
    fun `refuses when THORChain published no threshold`() {
        // Fatal rather than defaulted: guessing low is silently ignored, guessing high donates more
        // of the user's funds than necessary.
        shouldThrow<LimitOrderCancelDustError.ThresholdUnavailable> {
            limitOrderCancelDustAmount(
                localDustFloor = BigInteger.ZERO,
                inboundDustThreshold = null,
                decimals = 8,
                ceiling = BigInteger.TEN.pow(8),
                chainSymbol = "BTC",
            )
        }
    }

    @Test
    fun `refuses a threshold that is not a number`() {
        shouldThrow<LimitOrderCancelDustError.MalformedThreshold> {
            limitOrderCancelDustAmount(
                localDustFloor = BigInteger.ZERO,
                inboundDustThreshold = "lots",
                decimals = 8,
                ceiling = BigInteger.TEN.pow(8),
                chainSymbol = "BTC",
            )
        }
    }

    @Test
    fun `refuses an amount THORChain could not observe at all`() {
        // Refusing beats quietly raising it: an amount landing here means the pipeline that
        // produced
        // it is wrong, and the bare observable minimum would still be under what THORChain
        // requires.
        shouldThrow<LimitOrderCancelDustError.BelowObservableMinimum> {
            limitOrderCancelDustAmount(
                localDustFloor = BigInteger.ZERO,
                inboundDustThreshold = "0",
                decimals = 18,
                ceiling = BigInteger.TEN.pow(15),
                chainSymbol = "ETH",
            )
        }
    }

    @Test
    fun `refuses an amount above what the chain could plausibly require`() {
        // The only upper bound in the file: a wrong or hostile remote value would otherwise be
        // honoured verbatim and then doubled, and nothing attached to an `m=<` is refundable.
        shouldThrow<LimitOrderCancelDustError.ExceedsCeiling> {
            limitOrderCancelDustAmount(
                localDustFloor = BigInteger.ZERO,
                inboundDustThreshold = "100000000",
                decimals = 8,
                ceiling = BigInteger("100000"),
                chainSymbol = "BTC",
            )
        }
    }

    @Test
    fun `the observable minimum is one unit below eight decimals and 1e10 on eighteen`() {
        minimumObservableInbound(decimals = 6) shouldBe BigInteger.ONE
        minimumObservableInbound(decimals = 8) shouldBe BigInteger.ONE
        minimumObservableInbound(decimals = 18) shouldBe BigInteger.TEN.pow(10)
    }
}
