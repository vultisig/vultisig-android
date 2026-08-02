package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.models.Chain
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * The cancel memo's failure mode is silence: a memo THORChain accepts but that addresses a bucket
 * no order was indexed under costs a fee and cancels nothing, and looks identical to success from
 * the client. These tests pin the three things that make the difference.
 */
internal class LimitSwapCancelMemoTest {

    private val fullUsdc = "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"

    @Test
    fun `builds the three-field cancel memo with no space between amount and asset`() {
        val memo =
            LimitSwapCancelMemo.build(
                LimitSwapCancelMemo.Inputs(
                    sourceAsset = "THOR.RUNE",
                    sourceAmount1e8 = BigInteger("100000000"),
                    targetAsset = "BTC.BTC",
                    tradeTarget = BigInteger("4000000"),
                )
            )

        memo shouldBe "m=<:100000000THOR.RUNE:4000000BTC.BTC:0"
    }

    @Test
    fun `emits amounts as plain integers, never scientific notation`() {
        // The placement memo's LIM may be compressed (`544e6`); these coin amounts go through
        // cosmos.ParseCoins, which does not understand it, and the cancel would be rejected.
        val memo =
            LimitSwapCancelMemo.build(
                LimitSwapCancelMemo.Inputs(
                    sourceAsset = "BTC.BTC",
                    sourceAmount1e8 = BigInteger("544000000"),
                    targetAsset = "THOR.RUNE",
                    tradeTarget = BigInteger("1000000000000"),
                )
            )

        memo shouldBe "m=<:544000000BTC.BTC:1000000000000THOR.RUNE:0"
    }

    @Test
    fun `refuses an abbreviated token identifier`() {
        // `m=<` is the one inbound memo type THORChain does not fuzzy-match, so the placement
        // memo's
        // 6-character suffix is taken literally and keys an empty bucket.
        shouldThrow<IllegalArgumentException> {
            LimitSwapCancelMemo.build(
                LimitSwapCancelMemo.Inputs(
                    sourceAsset = "ETH.USDC-06EB48",
                    sourceAmount1e8 = BigInteger("100000000"),
                    targetAsset = "BTC.BTC",
                    tradeTarget = BigInteger("4000000"),
                )
            )
        }
    }

    @Test
    fun `refuses a zero trade target`() {
        shouldThrow<IllegalArgumentException> {
            LimitSwapCancelMemo.build(
                LimitSwapCancelMemo.Inputs(
                    sourceAsset = "THOR.RUNE",
                    sourceAmount1e8 = BigInteger("100000000"),
                    targetAsset = "BTC.BTC",
                    tradeTarget = BigInteger.ZERO,
                )
            )
        }
    }

    @Test
    fun `native and secured legs carry no identifier and are never called abbreviated`() {
        LimitSwapCancelMemo.isAbbreviated("BTC.BTC") shouldBe false
        LimitSwapCancelMemo.isAbbreviated("THOR.RUNE") shouldBe false
        // A secured denom spells the whole thing with `-`, so reading the tail after the last `-`
        // would call this truncated and make every secured-native order uncancellable.
        LimitSwapCancelMemo.isAbbreviated("btc-btc") shouldBe false
        LimitSwapCancelMemo.isAbbreviated(
            "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
        ) shouldBe false
        LimitSwapCancelMemo.isAbbreviated("ETH.USDC-06EB48") shouldBe true
    }

    @Test
    fun `recognises a cancel by its numerically zero final field`() {
        LimitSwapCancelMemo.isCancelMemo("m=<:1THOR.RUNE:2BTC.BTC:0") shouldBe true
        // THORNode's getUint reads this numerically, so a string comparison would call it a
        // retarget.
        LimitSwapCancelMemo.isCancelMemo("m=<:1THOR.RUNE:2BTC.BTC:00") shouldBe true
        // A non-zero final field RE-TARGETS the order, which is a different action entirely.
        LimitSwapCancelMemo.isCancelMemo("m=<:1THOR.RUNE:2BTC.BTC:500") shouldBe false
        // A sign cannot smuggle "-0" past an unsigned field.
        LimitSwapCancelMemo.isCancelMemo("m=<:1THOR.RUNE:2BTC.BTC:-0") shouldBe false
        LimitSwapCancelMemo.isCancelMemo("=<:BTC.BTC:dest:1/14400/0") shouldBe false
        LimitSwapCancelMemo.isCancelMemo(null) shouldBe false
    }

    @Test
    fun `an ERC20 target does not fit a UTXO source's OP_RETURN cap`() {
        val memo =
            LimitSwapCancelMemo.build(
                LimitSwapCancelMemo.Inputs(
                    sourceAsset = "BTC.BTC",
                    sourceAmount1e8 = BigInteger("100000000"),
                    targetAsset = fullUsdc,
                    tradeTarget = BigInteger("4000000"),
                )
            )

        // Nothing in a cancel memo can be shortened, so this is a yes/no gate: the order still
        // refunds automatically at expiry.
        LimitSwapCancelMemo.memoFits(memo, Chain.Bitcoin) shouldBe false
        LimitSwapCancelMemo.memoFits(memo, Chain.Ethereum) shouldBe true
    }

    @Test
    fun `bucket key collapses orders that share a ratio, not just equal amounts`() {
        // Selling 1 and selling 2 at the same price land in one bucket on-chain, so the duplicate
        // warning has to see them as colliding.
        val one =
            LimitSwapCancelMemo.bucketKey(
                LimitSwapCancelMemo.Inputs(
                    "THOR.RUNE",
                    BigInteger("100000000"),
                    "BTC.BTC",
                    BigInteger("4000000"),
                )
            )
        val two =
            LimitSwapCancelMemo.bucketKey(
                LimitSwapCancelMemo.Inputs(
                    "THOR.RUNE",
                    BigInteger("200000000"),
                    "BTC.BTC",
                    BigInteger("8000000"),
                )
            )
        one shouldBe two
    }

    @Test
    fun `bucket key normalises the ratio to eighteen characters and the assets to layer one`() {
        val key =
            LimitSwapCancelMemo.bucketKey(
                LimitSwapCancelMemo.Inputs(
                    sourceAsset = "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                    sourceAmount1e8 = BigInteger("100000000"),
                    targetAsset = "BTC.BTC",
                    tradeTarget = BigInteger("4000000"),
                )
            )

        // ratio = 1e8 * 1e8 / 4e6 = 2_500_000_000, zero-padded to THORNode's 18-character width,
        // and
        // the secured denom collapsed to its layer-1 form the way the index key spells it.
        key shouldBe
            "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48>BTC.BTC/000000002500000000/"
    }
}
