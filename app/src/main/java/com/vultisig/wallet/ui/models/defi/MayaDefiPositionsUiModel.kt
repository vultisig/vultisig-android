package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.ui.screens.v2.defi.defaultPositionsBondDialog
import com.vultisig.wallet.ui.screens.v2.defi.defaultSelectedPositionsDialog
import com.vultisig.wallet.ui.screens.v2.defi.maya.MayaDefiTab
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog

internal data class MayaDefiPositionsUiModel(
    // tabs info
    val totalAmountPrice: String = MayaDefiPositionsViewModel.DEFAULT_ZERO_BALANCE,
    val selectedTab: String = MayaDefiTab.BONDED.displayName,
    val bonded: BondedTabUiModel = BondedTabUiModel(
        totalBondedAmount = "0 ${Chain.MayaChain.coinType.symbol}"
    ),
    val isTotalAmountLoading: Boolean = false,
    val isBalanceVisible: Boolean = true,

    // position selection dialog
    val showPositionSelectionDialog: Boolean = false,
    val bondPositionsDialog: List<PositionUiModelDialog> = defaultPositionsBondDialog(),
    val selectedPositions: List<String> = defaultSelectedPositionsDialog(),
    val tempSelectedPositions: List<String> = defaultSelectedPositionsDialog(),
)
