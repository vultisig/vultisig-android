package com.vultisig.wallet.data.chains.helpers

/**
 * Builds THORChain swap affiliate query parameters, and is the single place the referral's cost to
 * the user is worked out — the wire params and the referral row the UI shows are both derived from
 * [referralSavingBps], so the number displayed cannot drift from the number sent (#5765).
 */
object ThorChainAffiliateHelper {

    /**
     * Returns the `affiliate` and `affiliate_bps` query parameters for a swap request.
     *
     * @param referralCode user's referral code; empty string means no referrer
     * @param discountBps VULT tier discount in basis points, subtracted from Vultisig's own leg
     */
    fun buildAffiliateParams(referralCode: String, discountBps: Int): Map<String, String> {
        return if (appliesReferral(referralCode, discountBps)) {
            val affiliateFeeRateBp =
                calculateBpsAfterDiscount(
                    baseBps = THORChainSwaps.REFERRED_AFFILIATE_FEE_RATE_BP,
                    discountBps = discountBps,
                )
            mapOf(
                "affiliate" to "$referralCode/${THORChainSwaps.AFFILIATE_FEE_ADDRESS}",
                "affiliate_bps" to "${THORChainSwaps.REFERRER_PAYOUT_BPS}/$affiliateFeeRateBp",
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

    /**
     * Whether a saved [referralCode] is actually sent at [discountBps].
     *
     * It is, unless it would make the user pay *more* than sending no code at all. Both legs are
     * clamped at zero independently, so the referrer's fixed payout stops being covered by
     * Vultisig's reduction once the tier discount has eaten Vultisig's leg — at the Ultimate tier
     * the referred total is 10 bps against an unreferred 0, and the user was being charged that
     * silently while the row was hidden. Where the code costs nothing extra it is still sent, so
     * the referrer keeps their payout whenever it is not the user paying for it.
     */
    fun appliesReferral(referralCode: String, discountBps: Int): Boolean =
        referralCode.isNotEmpty() && referralSavingBps(discountBps) >= 0

    /**
     * What sending the code saves the user, in bps, at [discountBps]. Negative means it costs them.
     *
     * This — not [THORChainSwaps.REFERRER_PAYOUT_BPS] — is the referral row's number: the referrer
     * is paid 10 while Vultisig gives up 15, so the user keeps the 5 bps difference.
     */
    fun referralSavingBps(discountBps: Int): Int =
        unreferredTotalBps(discountBps) - referredTotalBps(discountBps)

    /** Total bps the user pays with a referral code on the request: both affiliate legs summed. */
    fun referredTotalBps(discountBps: Int): Int =
        THORChainSwaps.REFERRER_PAYOUT_BPS +
            calculateBpsAfterDiscount(
                baseBps = THORChainSwaps.REFERRED_AFFILIATE_FEE_RATE_BP,
                discountBps = discountBps,
            )

    /** Total bps the user pays with no referral code on the request. */
    fun unreferredTotalBps(discountBps: Int): Int =
        calculateBpsAfterDiscount(
            baseBps = THORChainSwaps.AFFILIATE_FEE_RATE_BP,
            discountBps = discountBps,
        )

    private fun calculateBpsAfterDiscount(baseBps: Int, discountBps: Int): Int {
        return maxOf(0, baseBps - discountBps)
    }
}
