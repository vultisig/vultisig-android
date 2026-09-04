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

    /** A $200 source notional, so 20 bps prices at $0.40 and 10 bps at $0.20. */
    private val srcFiat = usd("200.00")

    /** BigDecimal equality is scale-sensitive, and the row's arithmetic fixes no scale. */
    private infix fun FiatValue?.shouldBeUsd(amount: String?) {
        val plain = { v: BigDecimal -> v.stripTrailingZeros().toPlainString() }
        this?.let { plain(it.value) to it.currency } shouldBe
            amount?.let { plain(BigDecimal(it)) to "USD" }
    }

    @Test
    fun `grosses the charged fee by the discounts the rows below itemize`() {
        val row =
            swapFeeRow(
                provider = SwapProvider.KYBER,
                netFee = usd("0.40"),
                listRate = "0.50%",
                srcFiat = srcFiat,
                discounts = SwapDiscountBps(vult = 20, referral = 10),
            )

        row.fee shouldBeUsd "1.00"
        row.percent shouldBe "0.50%"
        row.isListRate shouldBe true
        // Priced off the same snapshot as the fee, so gross − rows lands back on the net exactly.
        row.vultDiscount shouldBeUsd "0.40"
        row.referralDiscount shouldBeUsd "0.20"
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
                srcFiat = srcFiat,
                discounts = SwapDiscountBps(vult = 20),
            )

        row.fee shouldBeUsd "1.00"
        row.isListRate shouldBe true
    }

    @Test
    fun `never grosses SwapKit, whose itemized fee is an inbound deposit cost`() {
        val row =
            swapFeeRow(
                provider = SwapProvider.SWAPKIT,
                netFee = usd("0.60"),
                listRate = "0.50%",
                srcFiat = srcFiat,
                discounts = SwapDiscountBps(vult = 20),
            )

        row.fee shouldBeUsd "0.60"
        row.percent shouldBe null
        row.isListRate shouldBe false
        row.vultDiscount shouldBeUsd null
    }

    @Test
    fun `keeps the charged fee when a discount applies that the source cannot price`() {
        val row =
            swapFeeRow(
                provider = SwapProvider.KYBER,
                netFee = usd("0.60"),
                listRate = "0.50%",
                srcFiat = usd("0"),
                discounts = SwapDiscountBps(vult = 20),
            )

        row.fee shouldBeUsd "0.60"
        row.percent shouldBe null
        row.isListRate shouldBe false
        row.vultDiscount shouldBeUsd null
    }

    @Test
    fun `claims the list rate when no discount applies, since the charge already is it`() {
        val row =
            swapFeeRow(
                provider = SwapProvider.KYBER,
                netFee = usd("1.00"),
                listRate = "0.50%",
                srcFiat = srcFiat,
                discounts = SwapDiscountBps(vult = null, referral = 0),
            )

        row.fee shouldBeUsd "1.00"
        row.percent shouldBe "0.50%"
        row.isListRate shouldBe true
    }

    @Test
    fun `states the list rate for an undiscounted fee baked into the quoted rate`() {
        // 1inch itemizes no amount; the row renders "included in quoted rate" where one would go.
        // With nothing discounted, the rate the title claims is exactly the rate that was charged.
        val row =
            swapFeeRow(
                provider = SwapProvider.ONEINCH,
                netFee = usd("0.00"),
                listRate = "0.50%",
                srcFiat = srcFiat,
                discounts = SwapDiscountBps(),
                feeIncludedInRate = true,
            )

        row.percent shouldBe "0.50%"
        row.isListRate shouldBe true
        row.vultDiscount shouldBeUsd null
    }

    @Test
    fun `drops the rate and the discount row when a discounted fee is baked into the rate`() {
        // There is no itemized 1inch fee to gross, so a "-$0.40" row under it would subtract from
        // nothing — and "0.50%" is not the rate a Gold vault paid.
        val row =
            swapFeeRow(
                provider = SwapProvider.ONEINCH,
                netFee = usd("0.00"),
                listRate = "0.50%",
                srcFiat = srcFiat,
                discounts = SwapDiscountBps(vult = 20),
                feeIncludedInRate = true,
            )

        row.fee shouldBeUsd "0.00"
        row.percent shouldBe null
        row.isListRate shouldBe false
        row.vultDiscount shouldBeUsd null
    }

    @Test
    fun `applies the referral discount to THORChain alone`() {
        val thor =
            QuoteCandidate(SwapProvider.THORCHAIN, GOLD_DISCOUNT_BPS, referral = "vulti")
                .discountBps()
        val kyber =
            QuoteCandidate(SwapProvider.KYBER, GOLD_DISCOUNT_BPS, referral = "vulti").discountBps()

        thor shouldBe SwapDiscountBps(GOLD_DISCOUNT_BPS, THORChainSwaps.REFERRED_USER_FEE_RATE_BP)
        // Every other provider resolves no referral, so the picker row must not price one.
        kyber shouldBe SwapDiscountBps(GOLD_DISCOUNT_BPS, null)
    }

    @Test
    fun `resolves no referral discount at Ultimate, which already pays nothing`() {
        QuoteCandidate(SwapProvider.THORCHAIN, ULTIMATE_DISCOUNT_BPS, referral = "vulti")
            .discountBps() shouldBe SwapDiscountBps(ULTIMATE_DISCOUNT_BPS, null)
    }
}
