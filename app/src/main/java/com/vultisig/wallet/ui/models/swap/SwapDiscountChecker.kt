package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
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

    fun checkReferralBpsDiscount(tierType: TierType?, code: String): ReferralDiscountResult {
        val referralBpsDiscount =
            referralBpsFor(tierType)
                ?: return ReferralDiscountResult(referralBpsDiscount = null, referralCode = null)
        return ReferralDiscountResult(
            referralBpsDiscount = referralBpsDiscount,
            referralCode = code,
        )
    }
}

/**
 * Referral discount in bps for a swap at [tierType], or null when none applies: Ultimate already
 * pays no affiliate fee, so there is nothing left for a referral to take off.
 */
internal fun referralBpsFor(tierType: TierType?): Int? =
    THORChainSwaps.REFERRED_USER_FEE_RATE_BP.takeUnless { tierType == TierType.ULTIMATE }
