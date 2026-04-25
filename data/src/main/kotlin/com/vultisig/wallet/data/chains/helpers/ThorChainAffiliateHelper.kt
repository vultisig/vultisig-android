package com.vultisig.wallet.data.chains.helpers

/** Builds THORChain swap affiliate query parameters, applying referral codes and BPS discounts. */
object ThorChainAffiliateHelper {

    /**
     * Returns the `affiliate` and `affiliate_bps` query parameters for a swap request.
     *
     * @param referralCode user's referral code; empty string means no referrer
     * @param discountBps discount in basis points to subtract from the base affiliate fee
     */
    fun buildAffiliateParams(referralCode: String, discountBps: Int): Map<String, String> {
        val affiliateParams = mutableMapOf<String, String>()

        // For ultimate clean referral, and return 0 bps affiliate
        if (discountBps >= 50) {
            affiliateParams["affiliate"] = THORChainSwaps.AFFILIATE_FEE_ADDRESS
            affiliateParams["affiliate_bps"] = "0"

            return affiliateParams
        }

        if (referralCode.isNotEmpty()) {
            val affiliateFeeRateBp =
                calculateBpsAfterDiscount(
                    baseBps = THORChainSwaps.REFERRED_AFFILIATE_FEE_RATE_BP,
                    discountBps = discountBps,
                )

            // Build nested affiliate with new thorchain structure
            val affiliates = "$referralCode/${THORChainSwaps.AFFILIATE_FEE_ADDRESS}"
            val affiliateBps = "${THORChainSwaps.REFERRED_USER_FEE_RATE_BP}/$affiliateFeeRateBp"

            affiliateParams["affiliate"] = affiliates
            affiliateParams["affiliate_bps"] = affiliateBps
        } else {
            val affiliateFeeRateBp =
                calculateBpsAfterDiscount(
                    baseBps = THORChainSwaps.AFFILIATE_FEE_RATE_BP,
                    discountBps = discountBps,
                )

            affiliateParams["affiliate"] = THORChainSwaps.AFFILIATE_FEE_ADDRESS
            affiliateParams["affiliate_bps"] = affiliateFeeRateBp.toString()
        }

        return affiliateParams
    }

    private fun calculateBpsAfterDiscount(baseBps: Int, discountBps: Int): Int {
        return maxOf(0, baseBps - discountBps)
    }
}
