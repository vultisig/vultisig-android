package com.vultisig.wallet.ui.screens.v2.defi.model

import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.defi.ThorchainDefiPositionsViewModel

data class DefiUiModel(
    // Global screen parameters
    val totalAmountPrice: String = ThorchainDefiPositionsViewModel.DEFAULT_ZERO_BALANCE,
    val isTotalAmountLoading: Boolean = false,
    val isBalanceVisible: Boolean = true,
    val supportEditChains: Boolean = false,
    val bannerImage: Int = R.drawable.circle_defi_banner,
    val selectedTab: String = "Deposited",

    // Specific data per screen
    val circleDefi: CircleDeFi = CircleDeFi(),
) {
    // Create per tab is more are supported
    data class CircleDeFi(
        val isLoading: Boolean = false,
        val isAccountOpen: Boolean = false,
        val closeWarning: Boolean = false,
        val totalDeposit: String = "0 USDC",
        val totalDepositCurrency: String = "$0",
    )
}