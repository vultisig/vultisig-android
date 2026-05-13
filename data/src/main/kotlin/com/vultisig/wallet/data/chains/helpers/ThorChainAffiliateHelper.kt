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
        // Full discount: drop all affiliate fees and ignore the referral code.
        if (discountBps >= 50) {
            return mapOf(
                "affiliate" to THORChainSwaps.AFFILIATE_FEE_ADDRESS,
                "affiliate_bps" to "0",
            )
        }

        return if (referralCode.isNotEmpty()) {
            val affiliateFeeRateBp =
                calculateBpsAfterDiscount(
                    baseBps = THORChainSwaps.REFERRED_AFFILIATE_FEE_RATE_BP,
                    discountBps = discountBps,
                )
            mapOf(
                "affiliate" to "$referralCode/${THORChainSwaps.AFFILIATE_FEE_ADDRESS}",
                "affiliate_bps" to "${THORChainSwaps.REFERRED_USER_FEE_RATE_BP}/$affiliateFeeRateBp",
            )
        } else {
            val affiliateFeeRateBp =
                calculateBpsAfterDiscount(
                    baseBps = THORChainSwaps.AFFILIATE_FEE_RATE_BP,
                    discountBps = discountBps,
                )
            mapOf(
                "affiliate" to THORChainSwaps.AFFILIATE_FEE_ADDRESS,
                "affiliate_bps" to affiliateFeeRateBp.toString(),
            )
        }
    }

    private fun calculateBpsAfterDiscount(baseBps: Int, discountBps: Int): Int {
        return maxOf(0, baseBps - discountBps)
    }
}
