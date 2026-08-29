package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.nativeToken
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
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

    /**
     * Every routable chain's ceiling has to admit the dust that chain's own published threshold
     * produces.
     *
     * A ceiling set below it does not degrade gracefully — it refuses every cancel on that chain
     * outright, while the card still shows a live Cancel button. XRP, TRON and SOL fell through to
     * the 0.001 default and were 2000x, 200x and 2x under what they need.
     *
     * The thresholds are pinned from `inbound_addresses`, in THORChain's 1e8 base units, so a chain
     * added to the routable set without a sized ceiling fails here rather than in the user's hands.
     */
    @Test
    fun `every routable chain's ceiling admits the dust its own threshold produces`() {
        publishedDustThresholds.forEach { (chain, threshold) ->
            val decimals = chain.nativeToken.decimal
            withClue("${chain.raw} ceiling must admit its published dust threshold") {
                limitOrderCancelDustAmount(
                    localDustFloor = limitOrderCancelLocalDustFloor(chain),
                    inboundDustThreshold = threshold,
                    decimals = decimals,
                    ceiling =
                        limitOrderCancelDustCeiling(chain)
                            .movePointRight(decimals)
                            .toBigIntegerExact(),
                    chainSymbol = thorchainMemoAssetChainPrefix[chain] ?: chain.raw,
                ) shouldBeGreaterThan BigInteger.ZERO
            }
        }
    }

    @Test
    fun `every routable chain except THORChain has a pinned threshold to check`() {
        // THORChain-sourced cancels are a MsgDeposit carrying no coins, so no dust is attached and
        // no ceiling applies. Everything else reaches THORChain as an L1 transfer and needs one.
        (staticLimitSwapSupportedChains - Chain.ThorChain).forEach { chain ->
            withClue("${chain.raw} is routable but has no pinned dust_threshold in this test") {
                publishedDustThresholds.keys shouldContain chain
            }
        }
    }

    private companion object {
        /**
         * `dust_threshold` as `inbound_addresses` publishes it, in THORChain's 1e8 fixed point on
         * every chain regardless of that chain's own precision.
         */
        val publishedDustThresholds =
            mapOf(
                Chain.Avalanche to "100000",
                Chain.Base to "1000",
                Chain.BitcoinCash to "10000",
                Chain.BscChain to "10000",
                Chain.Bitcoin to "10000",
                Chain.Dash to "10000",
                Chain.Dogecoin to "100000000",
                Chain.Ethereum to "1000",
                Chain.GaiaChain to "1000000",
                Chain.Litecoin to "100000",
                Chain.Tron to "10000000",
                Chain.Ripple to "100000000",
                Chain.Arbitrum to "1000",
                Chain.Zcash to "10000",
                Chain.Solana to "100000",
                Chain.Noble to "1000000",
            )
    }
}
