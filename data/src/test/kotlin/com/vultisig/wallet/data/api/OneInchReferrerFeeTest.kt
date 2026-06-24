package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.swapAggregators.OneInchApiImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Verifies the affiliate fee 1inch is sent for a swap: the base 0.5% reduced by the VULT discount
 * and clamped at zero. The clamp guards against a discount larger than the base fee producing a
 * negative `fee` parameter, which 1inch rejects.
 */
class OneInchReferrerFeeTest {

    @Test
    fun `referrer fee is the base 0_5 percent when there is no discount`() {
        assertEquals(0.5, OneInchApiImpl.discountedReferrerFee(bpsDiscount = 0))
    }

    @Test
    fun `referrer fee is reduced by the VULT discount`() {
        assertEquals(0.25, OneInchApiImpl.discountedReferrerFee(bpsDiscount = 25))
    }

    @Test
    fun `referrer fee bottoms out at zero when the discount equals the base fee`() {
        assertEquals(0.0, OneInchApiImpl.discountedReferrerFee(bpsDiscount = 50))
    }

    @Test
    fun `referrer fee is clamped at zero when the discount exceeds the base fee`() {
        assertEquals(0.0, OneInchApiImpl.discountedReferrerFee(bpsDiscount = 60))
        assertEquals(0.0, OneInchApiImpl.discountedReferrerFee(bpsDiscount = 100))
    }
}
