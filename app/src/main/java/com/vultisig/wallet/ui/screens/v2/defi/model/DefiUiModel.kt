package com.vultisig.wallet.ui.screens.v2.defi.model

import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.defi.ThorchainDefiPositionsViewModel

data class DefiUiModel(
    // Global screen parameters
    val totalAmountPrice: String = ThorchainDefiPositionsViewModel.DEFAULT_ZERO_BALANCE,
    val isTotalAmountLoading: Boolean = false,
    val isBalanceVisible: Boolean = true,
    val supportEditChains: Boolean = false,

    // Banner
    val bannerImage: Int = R.drawable.circle_defi_banner,

    // tabs parameters
    val selectedTab: String = "Deposited",
    val tabDescription: Boolean = false,
    val tabWarningBanner: Boolean = false,
)