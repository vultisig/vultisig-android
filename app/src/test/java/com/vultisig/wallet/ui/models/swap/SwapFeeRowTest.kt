package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.GOLD_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.ULTIMATE_DISCOUNT_BPS
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import org.junit.jupiter.api.Test

/**
 * The Swap Fee row and every screen that restates it — the swap form, the Select-route picker, the
 * verify and done screens, and the co-signer's join screen — resolve it through [swapFeeRow], so
 * one route can never advertise two different fees.
 */
internal class SwapFeeRowTest {

    private val usd = { amount: String -> FiatValue(BigDecimal(amount), "USD") }

    @Test
    fun `grosses the charged fee by the discounts the rows below itemize`() {
        val row =
            swapFeeRow(
                provider = SwapProvider.KYBER,
                netFee = usd("0.60"),
                listRate = "0.50%",
                discountBps = listOf(20, null),
                pricedDiscounts = listOf(usd("0.40"), null),
            )

        row.fee shouldBe usd("1.00")
        row.percent shouldBe "0.50%"
        row.isListRate shouldBe true
    }

    @Test
    fun `grosses without a rate string for a row that shows no rate`() {
        // The Select-route picker renders the fee alone. It still has to be the grossed one, or a
        // route would advertise one fee in the picker and a larger one in the breakdown it opens.
        val row =
            swapFeeRow(
                provider = SwapProvider.KYBER,
                netFee = usd("0.60"),
                listRate = null,
                discountBps = listOf(20),
                pricedDiscounts = listOf(usd("0.40")),
            )

        row.fee shouldBe usd("1.00")
        row.isListRate shouldBe true
    }

    @Test
    fun `never grosses SwapKit, whose itemized fee is an inbound deposit cost`() {
        val row =
            swapFeeRow(
                provider = SwapProvider.SWAPKIT,
                netFee = usd("0.60"),
                listRate = "0.50%",
                discountBps = listOf(20),
                pricedDiscounts = listOf(usd("0.40")),
            )

        row.fee shouldBe usd("0.60")
        row.percent shouldBe null
        row.isListRate shouldBe false
    }

    @Test
    fun `keeps the charged fee when a discount applies that the source cannot price`() {
        val row =
            swapFeeRow(
                provider = SwapProvider.KYBER,
                netFee = usd("0.60"),
                listRate = "0.50%",
                discountBps = listOf(20),
                pricedDiscounts = listOf(null),
            )

        row.fee shouldBe usd("0.60")
        row.percent shouldBe null
        row.isListRate shouldBe false
    }

    @Test
    fun `claims the list rate when no discount applies, since the charge already is it`() {
        val row =
            swapFeeRow(
                provider = SwapProvider.KYBER,
                netFee = usd("1.00"),
                listRate = "0.50%",
                discountBps = listOf(null, 0),
                pricedDiscounts = listOf(null, null),
            )

        row.fee shouldBe usd("1.00")
        row.percent shouldBe "0.50%"
        row.isListRate shouldBe true
    }

    @Test
    fun `applies the referral discount to THORChain alone`() {
        val thor =
            QuoteCandidate(SwapProvider.THORCHAIN, GOLD_DISCOUNT_BPS, referral = "vulti")
                .discountBps()
        val kyber =
            QuoteCandidate(SwapProvider.KYBER, GOLD_DISCOUNT_BPS, referral = "vulti").discountBps()

        thor shouldBe listOf(GOLD_DISCOUNT_BPS, THORChainSwaps.REFERRED_USER_FEE_RATE_BP)
        // Every other provider resolves no referral, so the picker row must not price one.
        kyber shouldBe listOf(GOLD_DISCOUNT_BPS, null)
    }

    @Test
    fun `resolves no referral discount at Ultimate, which already pays nothing`() {
        QuoteCandidate(SwapProvider.THORCHAIN, ULTIMATE_DISCOUNT_BPS, referral = "vulti")
            .discountBps() shouldBe listOf(ULTIMATE_DISCOUNT_BPS, null)
    }
}
