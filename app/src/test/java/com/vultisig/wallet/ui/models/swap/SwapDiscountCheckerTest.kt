package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.chains.helpers.ThorChainAffiliateHelper
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.BRONZE_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.DIAMOND_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.GOLD_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.NO_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.PLATINUM_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.ULTIMATE_DISCOUNT_BPS
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The referral row states what a saved code does to the user's own fee. Nothing used to pin that
 * against the bps the request builder sends, which is how the row came to show the referrer's
 * payout — twice the real saving — and how a top-tier user came to be charged for a code whose row
 * was hidden (#5765).
 */
internal class SwapDiscountCheckerTest {

    private val checker = SwapDiscountChecker()

    /** Every tier discount a swap can carry below Ultimate, which is covered on its own below. */
    private val tierDiscounts =
        listOf(
            NO_DISCOUNT_BPS,
            BRONZE_DISCOUNT_BPS,
            SILVER_DISCOUNT_BPS,
            GOLD_DISCOUNT_BPS,
            PLATINUM_DISCOUNT_BPS,
            DIAMOND_DISCOUNT_BPS,
        )

    /** Total bps thornode charges for a request, which sums the legs of a multi-affiliate list. */
    private fun sentBps(code: String, discountBps: Int): Int =
        ThorChainAffiliateHelper.buildAffiliateParams(code, discountBps)["affiliate_bps"]!!.split(
                "/"
            )
            .sumOf { it.toInt() }

    @Test
    fun `the displayed bps is the delta between the totals sent with and without a code`() {
        for (discount in tierDiscounts) {
            val saved = sentBps(code = "vulti", discountBps = discount)
            val unsaved = sentBps(code = "", discountBps = discount)

            referralBpsFor(discount) shouldBe (unsaved - saved)
        }
    }

    @Test
    fun `that delta is 5 bps at every tier through Diamond, not the referrer's 10`() {
        for (discount in tierDiscounts) {
            referralBpsFor(discount) shouldBe 5
        }
    }

    @Test
    fun `a vault with no resolved tier is treated as undiscounted`() {
        referralBpsFor(null) shouldBe referralBpsFor(NO_DISCOUNT_BPS)
    }

    @Test
    fun `the top tier shows no row because the code is not sent, not because it is hidden`() {
        // The row used to be suppressed at Ultimate while "10/0" still went on the wire, so the
        // user paid 10 bps invisibly. The code is dropped now, so there is genuinely nothing to
        // show and the fee is the same 0 an unreferred vault pays.
        sentBps(code = "vulti", discountBps = ULTIMATE_DISCOUNT_BPS) shouldBe 0
        sentBps(code = "", discountBps = ULTIMATE_DISCOUNT_BPS) shouldBe 0
        referralBpsFor(ULTIMATE_DISCOUNT_BPS) shouldBe null
    }

    @Test
    fun `a code is cached back only when it actually discounts the swap`() {
        checker.checkReferralBpsDiscount(GOLD_DISCOUNT_BPS, "vulti") shouldBe
            ReferralDiscountResult(referralBpsDiscount = 5, referralCode = "vulti")

        checker.checkReferralBpsDiscount(ULTIMATE_DISCOUNT_BPS, "vulti") shouldBe
            ReferralDiscountResult(referralBpsDiscount = null, referralCode = null)
    }
}
