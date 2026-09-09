package com.vultisig.wallet.data.chains.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThorChainAffiliateHelperTest {

    private companion object {
        /**
         * VULT tier discounts a swap can carry, up to Diamond. Ultimate (50) is called out apart.
         */
        val TIER_DISCOUNTS = listOf(0, 5, 10, 20, 25, 35)
        const val ULTIMATE_DISCOUNT_BPS = 50
    }

    @Test
    fun `no referral code, zero discount uses default affiliate fee rate`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "", discountBps = 0)

        assertEquals(THORChainSwaps.AFFILIATE_FEE_ADDRESS, params["affiliate"])
        assertEquals(THORChainSwaps.AFFILIATE_FEE_RATE_BP.toString(), params["affiliate_bps"])
    }

    @Test
    fun `no referral code, partial discount subtracts from default fee rate`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "", discountBps = 20)

        assertEquals(THORChainSwaps.AFFILIATE_FEE_ADDRESS, params["affiliate"])
        assertEquals(
            (THORChainSwaps.AFFILIATE_FEE_RATE_BP - 20).toString(),
            params["affiliate_bps"],
        )
    }

    @Test
    fun `no referral code, partial discount near base produces small affiliate bps`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "", discountBps = 49)

        assertEquals("1", params["affiliate_bps"])
    }

    @Test
    fun `with referral code, zero discount uses nested affiliate format`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "alice", discountBps = 0)

        assertEquals("alice/${THORChainSwaps.AFFILIATE_FEE_ADDRESS}", params["affiliate"])
        assertEquals(
            "${THORChainSwaps.REFERRER_PAYOUT_BPS}/${THORChainSwaps.REFERRED_AFFILIATE_FEE_RATE_BP}",
            params["affiliate_bps"],
        )
    }

    @Test
    fun `with referral code, discount only reduces affiliate share, user share preserved`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "alice", discountBps = 20)

        val expectedAffiliateBps = THORChainSwaps.REFERRED_AFFILIATE_FEE_RATE_BP - 20
        assertEquals("alice/${THORChainSwaps.AFFILIATE_FEE_ADDRESS}", params["affiliate"])
        assertEquals(
            "${THORChainSwaps.REFERRER_PAYOUT_BPS}/$expectedAffiliateBps",
            params["affiliate_bps"],
        )
    }

    @Test
    fun `with referral code, discount exceeding referred affiliate base clamps to zero`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "alice", discountBps = 40)

        assertEquals("${THORChainSwaps.REFERRER_PAYOUT_BPS}/0", params["affiliate_bps"])
    }

    @Test
    fun `full discount without referral code zeroes the affiliate fee`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "", discountBps = 50)

        assertEquals(THORChainSwaps.AFFILIATE_FEE_ADDRESS, params["affiliate"])
        assertEquals("0", params["affiliate_bps"])
    }

    @Test
    fun `full discount drops the referral rather than charging a user who owes nothing`() {
        // Ultimate pays no affiliate fee at all, so the referrer's leg would be the user's whole
        // bill: "10/0" is charged 10 bps where "0" is charged nothing (#5765).
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "alice", discountBps = 50)

        assertEquals(THORChainSwaps.AFFILIATE_FEE_ADDRESS, params["affiliate"])
        assertEquals("0", params["affiliate_bps"])
    }

    @Test
    fun `discount beyond the referred affiliate base also drops the referral`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "alice", discountBps = 100)

        assertEquals(THORChainSwaps.AFFILIATE_FEE_ADDRESS, params["affiliate"])
        assertEquals("0", params["affiliate_bps"])
    }

    @Test
    fun `the referral is kept while it costs the user nothing extra`() {
        // At 40 bps both routes bill 10, so the code is not the user's problem and the referrer
        // keeps their payout — the split the test above pins. Only a code that would *raise* the
        // bill is dropped.
        assertEquals(10, ThorChainAffiliateHelper.unreferredTotalBps(40))
        assertEquals(10, ThorChainAffiliateHelper.referredTotalBps(40))
        assertTrue(ThorChainAffiliateHelper.appliesReferral("alice", 40))
    }

    @Test
    fun `the saving is the delta between the totals actually sent, at every tier`() {
        // Measured against thornode: an affiliate list of "10/35" is charged exactly as a flat 45,
        // so the code moves the user from 50 to 45 — 5 bps, never the referrer's 10.
        for (discount in TIER_DISCOUNTS) {
            val saving = ThorChainAffiliateHelper.referralSavingBps(discount)
            assertEquals(
                ThorChainAffiliateHelper.unreferredTotalBps(discount) -
                    ThorChainAffiliateHelper.referredTotalBps(discount),
                saving,
                "discount=$discount",
            )
            assertEquals(5, saving, "discount=$discount")
        }
    }

    @Test
    fun `the totals match the bps the request builder puts on the wire`() {
        for (discount in TIER_DISCOUNTS + listOf(ULTIMATE_DISCOUNT_BPS)) {
            val referred =
                ThorChainAffiliateHelper.buildAffiliateParams("alice", discount)["affiliate_bps"]!!
            val unreferred =
                ThorChainAffiliateHelper.buildAffiliateParams("", discount)["affiliate_bps"]!!

            assertEquals(
                ThorChainAffiliateHelper.unreferredTotalBps(discount),
                unreferred.toInt(),
                "unreferred discount=$discount",
            )
            // Whichever branch the gate took, the legs sent sum to what the user is billed.
            val expectedReferred =
                if (ThorChainAffiliateHelper.appliesReferral("alice", discount)) {
                    ThorChainAffiliateHelper.referredTotalBps(discount)
                } else ThorChainAffiliateHelper.unreferredTotalBps(discount)
            assertEquals(
                expectedReferred,
                referred.split("/").sumOf { it.toInt() },
                "referred discount=$discount",
            )
        }
    }

    @Test
    fun `the top tier is no longer charged for a saved code`() {
        assertEquals(0, ThorChainAffiliateHelper.unreferredTotalBps(ULTIMATE_DISCOUNT_BPS))
        assertEquals(-10, ThorChainAffiliateHelper.referralSavingBps(ULTIMATE_DISCOUNT_BPS))
        assertFalse(ThorChainAffiliateHelper.appliesReferral("alice", ULTIMATE_DISCOUNT_BPS))
    }

    @Test
    fun `an empty code never applies`() {
        for (discount in TIER_DISCOUNTS + listOf(ULTIMATE_DISCOUNT_BPS)) {
            assertFalse(
                ThorChainAffiliateHelper.appliesReferral("", discount),
                "discount=$discount",
            )
        }
    }

    @Test
    fun `output always contains exactly the affiliate and affiliate_bps keys`() {
        val cases = listOf("" to 0, "" to 20, "" to 50, "alice" to 0, "alice" to 25, "alice" to 50)

        for ((code, discount) in cases) {
            val params =
                ThorChainAffiliateHelper.buildAffiliateParams(
                    referralCode = code,
                    discountBps = discount,
                )
            assertEquals(setOf("affiliate", "affiliate_bps"), params.keys, "case=$code/$discount")
        }
    }
}
