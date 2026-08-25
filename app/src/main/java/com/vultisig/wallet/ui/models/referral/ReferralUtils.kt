package com.vultisig.wallet.ui.models.referral

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ThorChainPoolCoin
import com.vultisig.wallet.data.models.swapAssetName
import com.vultisig.wallet.ui.models.referral.ReferralViewModel.Companion.MAX_LENGTH_REFERRAL_CODE
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText

private fun validateMaxLength(code: String): UiText? {
    return if (code.length > MAX_LENGTH_REFERRAL_CODE) {
        UiText.PluralText(
            R.plurals.referral_code_can_be_up_to_characters,
            MAX_LENGTH_REFERRAL_CODE,
            listOf(MAX_LENGTH_REFERRAL_CODE),
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

/**
 * Builds the THORName memo a referral edit is signed with:
 * `~:NAME:CHAIN:ADDRESS:OWNER:PREFERRED_ASSET`, where thornode reads the chain/address pair as the
 * alias to register and the last field as the payout asset.
 *
 * With a payout asset the alias is registered on *that* asset's chain, pointing at this vault's
 * address there — a preferred asset is only paid out to the alias its own chain carries, so setting
 * the asset without the matching alias would leave the payout nowhere to land. Without one the memo
 * keeps registering the THOR alias, which is all a plain expiry extension needs.
 */
internal fun buildEditReferralMemo(
    referralCode: String,
    thorAddress: String,
    payoutAsset: ThorChainPoolCoin?,
    payoutAssetAddress: String?,
): String {
    val code = referralCode.uppercase()
    return if (payoutAsset != null && !payoutAssetAddress.isNullOrBlank()) {
        "~:$code:${payoutAsset.coin.chain.swapAssetName()}:$payoutAssetAddress:$thorAddress:" +
            payoutAsset.asset
    } else {
        "~:$code:THOR:$thorAddress:$thorAddress"
    }
}
