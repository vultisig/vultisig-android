package com.vultisig.wallet.ui.models.referral

import com.vultisig.wallet.ui.models.referral.ReferralViewModel.Companion.MAX_LENGTH_REFERRAL_CODE

internal fun validateReferralCode(code: String): String? {
    if (code.isEmpty()) return "Referral code cannot be empty"
    if (code.length > MAX_LENGTH_REFERRAL_CODE) return "Referral code can be up to $MAX_LENGTH_REFERRAL_CODE characters"
    return null
}