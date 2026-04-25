package com.vultisig.wallet.data.chains.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThorChainAffiliateHelperTest {

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
    fun `no referral code, discount exceeding base clamps to zero`() {
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
            "${THORChainSwaps.REFERRED_USER_FEE_RATE_BP}/${THORChainSwaps.REFERRED_AFFILIATE_FEE_RATE_BP}",
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
            "${THORChainSwaps.REFERRED_USER_FEE_RATE_BP}/$expectedAffiliateBps",
            params["affiliate_bps"],
        )
    }

    @Test
    fun `with referral code, discount exceeding referred affiliate base clamps to zero`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "alice", discountBps = 40)

        assertEquals("${THORChainSwaps.REFERRED_USER_FEE_RATE_BP}/0", params["affiliate_bps"])
    }

    @Test
    fun `discount of 50 bps short-circuits to clean referral with zero affiliate fee`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "", discountBps = 50)

        assertEquals(THORChainSwaps.AFFILIATE_FEE_ADDRESS, params["affiliate"])
        assertEquals("0", params["affiliate_bps"])
    }

    @Test
    fun `discount of 50 bps short-circuits even when a referral code is provided`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "alice", discountBps = 50)

        // Clean-referral branch ignores the code and emits the flat (non-nested) form.
        assertEquals(THORChainSwaps.AFFILIATE_FEE_ADDRESS, params["affiliate"])
        assertEquals("0", params["affiliate_bps"])
    }

    @Test
    fun `discount above 50 bps continues to short-circuit`() {
        val params =
            ThorChainAffiliateHelper.buildAffiliateParams(referralCode = "alice", discountBps = 100)

        assertEquals(THORChainSwaps.AFFILIATE_FEE_ADDRESS, params["affiliate"])
        assertEquals("0", params["affiliate_bps"])
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
