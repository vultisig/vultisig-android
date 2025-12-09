package com.vultisig.wallet.ui.screens.v2.defi.model

import com.vultisig.wallet.ui.models.defi.ThorchainDefiPositionsViewModel

data class DefiUiModel(
    val totalAmountPrice: String = ThorchainDefiPositionsViewModel.DEFAULT_ZERO_BALANCE,
    val isTotalAmountLoading: Boolean = false,
    val isBalanceVisible: Boolean = true,
)