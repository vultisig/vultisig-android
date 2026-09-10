package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.chains.helpers.ThorChainAffiliateHelper
import com.vultisig.wallet.data.usecases.getTierType
import com.vultisig.wallet.ui.screens.settings.TierType
import javax.inject.Inject

internal data class VultDiscountResult(val vultBpsDiscount: Int?, val tierType: TierType?)

internal data class ReferralDiscountResult(
    val referralBpsDiscount: Int?,
    val referralCode: String?,
)

/**
 * Resolves which discounts apply to a quote, in basis points.
 *
 * It deliberately does not value them: [swapFeeRow] prices the rows off the same source snapshot it
 * grosses the fee from, so the row and the fee it is subtracted from cannot be read from two
 * different prices. Pricing them a second time here — off a price read a moment after the quote's —
 * is what let a tick between the two reads leave the panel unable to reconcile (#5803).
 */
internal class SwapDiscountChecker @Inject constructor() {

    fun checkVultBpsDiscount(vultBPSDiscount: Int?): VultDiscountResult =
        VultDiscountResult(
            vultBpsDiscount = vultBPSDiscount,
            tierType = vultBPSDiscount?.getTierType(),
        )

    fun checkReferralBpsDiscount(vultBpsDiscount: Int?, code: String): ReferralDiscountResult {
        val referralBpsDiscount =
            referralBpsFor(vultBpsDiscount)
                ?: return ReferralDiscountResult(referralBpsDiscount = null, referralCode = null)
        return ReferralDiscountResult(
            referralBpsDiscount = referralBpsDiscount,
            referralCode = code,
        )
    }
}

/**
 * What a saved referral code saves the user on a THORChain swap at [vultBpsDiscount], or null when
 * there is nothing to show.
 *
 * Taken from the request builder's own arithmetic so the row states the difference the code makes
 * to the totals actually sent, rather than the referrer's payout — which is a leg the user pays,
 * not a reduction, and read as a saving it overstated the row twofold (#5765). Null covers both
 * ends: no code sent means no row, and a code that saves nothing has no saving to itemize.
 */
internal fun referralBpsFor(vultBpsDiscount: Int?): Int? =
    ThorChainAffiliateHelper.referralSavingBps(vultBpsDiscount ?: 0).takeIf { it > 0 }
