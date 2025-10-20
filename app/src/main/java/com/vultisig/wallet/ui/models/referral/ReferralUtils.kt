package com.vultisig.wallet.ui.models.referral

import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.referral.ReferralViewModel.Companion.MAX_LENGTH_REFERRAL_CODE
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText

private fun validateMaxLength(code: String): UiText? {
    return if (code.length > MAX_LENGTH_REFERRAL_CODE) {
        UiText.FormattedText(
            R.string.referral_code_can_be_up_to_characters,
            listOf(MAX_LENGTH_REFERRAL_CODE)
        )
    } else {
        null
    }
}

internal fun validateReferralCode(code: String): UiText? {
    if (code.isEmpty()) return R.string.referral_code_cannot_be_empty.asUiText()
    return validateMaxLength(code)
}

internal fun validateMaxReferral(code: String): UiText? {
    return validateMaxLength(code)
}