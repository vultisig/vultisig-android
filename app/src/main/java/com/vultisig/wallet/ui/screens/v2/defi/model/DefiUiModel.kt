package com.vultisig.wallet.ui.screens.v2.defi.model

import com.vultisig.wallet.R

data class DefiUiModel(
    // Global screen parameters
    // Null until the total is priced in the user's currency; the banner shows its loading state
    // until then rather than a hardcoded USD zero.
    val totalAmountPrice: String? = null,
    val isTotalAmountLoading: Boolean = true,
    val isBalanceVisible: Boolean = true,
    val supportEditChains: Boolean = false,
    val bannerImage: Int = R.drawable.circle_defi_banner,
    val selectedTab: Int = R.string.defi_tab_deposited,

    // Specific data per screen
    val circleDefi: CircleDeFi = CircleDeFi(),
) {
    // Create per tab is more are supported
    data class CircleDeFi(
        val isLoading: Boolean = false,
        val isAccountOpen: Boolean = false,
        val closeWarning: Boolean = false,
        val totalDeposit: String = "0 USDC",
        val totalDepositCurrency: String? = null,
    )
}
