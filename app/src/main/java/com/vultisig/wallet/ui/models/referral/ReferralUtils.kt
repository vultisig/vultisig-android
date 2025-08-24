package com.vultisig.wallet.ui.models.referral

import com.vultisig.wallet.ui.models.referral.ReferralViewModel.Companion.MAX_LENGTH_REFERRAL_CODE

private fun validateMaxLength(code: String): String? {
    return if (code.length > MAX_LENGTH_REFERRAL_CODE) {
        "Referral code can be up to $MAX_LENGTH_REFERRAL_CODE characters"
    } else {
        null
    }
}

internal fun validateReferralCode(code: String): String? {
    if (code.isEmpty()) return "Referral code cannot be empty"
    return validateMaxLength(code)
}

internal fun validateMaxReferral(code: String): String? {
    return validateMaxLength(code)
}