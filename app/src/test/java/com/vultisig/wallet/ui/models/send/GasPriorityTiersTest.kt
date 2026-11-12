package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.models.Chain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

class GasPriorityTiersTest {

    private val gwei = BigInteger.TEN.pow(9)

    private fun gwei(value: String) =
        value.toBigDecimal().multiply(gwei.toBigDecimal()).toBigInteger()

    private fun wei(vararg values: Long) = values.map(BigInteger::valueOf)

    @Test
    fun `Ethereum takes min, median and max of the history, floored at 1 gwei`() {
        val history = listOf(gwei("0.5"), gwei("1.2"), gwei("2"), gwei("3"))

        evmPriorityFeeTiers(Chain.Ethereum, history) shouldBe
            mapOf(
                PriorityFee.LOW to gwei("1"),
                // Even count: the mean of the middle two, (1.2 + 2) / 2.
                PriorityFee.NORMAL to gwei("1.6"),
                PriorityFee.FAST to gwei("3"),
            )
    }

    @Test
    fun `an odd history takes its middle reward as Normal`() {
        val history = listOf(gwei("2"), gwei("4"), gwei("9"))

        evmPriorityFeeTiers(Chain.Sei, history)?.get(PriorityFee.NORMAL) shouldBe gwei("4")
    }

    @Test
    fun `Arbitrum, Mantle and Robinhood pay no tip at any tier`() {
        val history = listOf(gwei("1"), gwei("2"), gwei("3"))

        listOf(Chain.Arbitrum, Chain.Mantle, Chain.Robinhood).forEach { chain ->
            evmPriorityFeeTiers(chain, history) shouldBe
                PriorityFee.entries.associateWith { BigInteger.ZERO }
        }
    }

    @Test
    fun `Base, Blast and Optimism floor every tier at 20 wei`() {
        val history = wei(5, 10, 100)

        listOf(Chain.Base, Chain.Blast, Chain.Optimism).forEach { chain ->
            evmPriorityFeeTiers(chain, history) shouldBe
                mapOf(
                    PriorityFee.LOW to BigInteger.valueOf(20),
                    PriorityFee.NORMAL to BigInteger.valueOf(20),
                    PriorityFee.FAST to BigInteger.valueOf(100),
                )
        }
    }

    @Test
    fun `Polygon floors every tier at 30 gwei`() {
        val history = listOf(gwei("10"), gwei("40"), gwei("50"))

        evmPriorityFeeTiers(Chain.Polygon, history) shouldBe
            mapOf(
                PriorityFee.LOW to gwei("30"),
                PriorityFee.NORMAL to gwei("40"),
                PriorityFee.FAST to gwei("50"),
            )
    }

    @Test
    fun `Avalanche uses the history as it is`() {
        evmPriorityFeeTiers(Chain.Avalanche, wei(1, 2, 3)) shouldBe
            mapOf(
                PriorityFee.LOW to BigInteger.ONE,
                PriorityFee.NORMAL to BigInteger.TWO,
                PriorityFee.FAST to BigInteger.valueOf(3),
            )
    }

    @Test
    fun `an empty history offers no tier`() {
        evmPriorityFeeTiers(Chain.Ethereum, emptyList()).shouldBeNull()
    }

    @Test
    fun `UTXO tiers scale today's byte fee by 0_75, 1 and 2_5, truncated`() {
        utxoByteFeeTiers(BigInteger.valueOf(30)) shouldBe
            mapOf(
                PriorityFee.LOW to BigInteger.valueOf(22),
                PriorityFee.NORMAL to BigInteger.valueOf(30),
                PriorityFee.FAST to BigInteger.valueOf(75),
            )
        utxoByteFeeTiers(BigInteger.valueOf(7)) shouldBe
            mapOf(
                PriorityFee.LOW to BigInteger.valueOf(5),
                PriorityFee.NORMAL to BigInteger.valueOf(7),
                PriorityFee.FAST to BigInteger.valueOf(17),
            )
    }

    @Test
    fun `Dogecoin tiers apply to its already rescaled rate`() {
        // 500,000 sats/byte suggested → 125,000 today; iOS gives 93,750 / 125,000 / 312,500.
        utxoByteFeeTiers(BigInteger.valueOf(125_000)) shouldBe
            mapOf(
                PriorityFee.LOW to BigInteger.valueOf(93_750),
                PriorityFee.NORMAL to BigInteger.valueOf(125_000),
                PriorityFee.FAST to BigInteger.valueOf(312_500),
            )
    }

    @Test
    fun `tiers are offered on EIP-1559 EVM chains and market-rate UTXO chains only`() {
        Chain.Ethereum.hasPriorityTiers shouldBe true
        Chain.Avalanche.hasPriorityTiers shouldBe true
        Chain.Bitcoin.hasPriorityTiers shouldBe true
        Chain.Dogecoin.hasPriorityTiers shouldBe true
        Chain.BscChain.hasPriorityTiers shouldBe false
        Chain.ZkSync.hasPriorityTiers shouldBe false
        Chain.Zcash.hasPriorityTiers shouldBe false
        Chain.Cardano.hasPriorityTiers shouldBe false
        Chain.Solana.hasPriorityTiers shouldBe false
    }
}
