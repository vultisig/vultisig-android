package com.vultisig.wallet.ui.screens.v2.defi.maya

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.models.defi.BondedNodeUiModel
import com.vultisig.wallet.ui.models.defi.BondedTabUiModel
import com.vultisig.wallet.ui.models.defi.MayaDefiPositionsUiModel
import com.vultisig.wallet.ui.models.defi.MayaDefiPositionsViewModel
import com.vultisig.wallet.ui.screens.v2.defi.BalanceBanner
import com.vultisig.wallet.ui.screens.v2.defi.BondedTabContent
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.NoPositionsContainer
import com.vultisig.wallet.ui.screens.v2.defi.PositionsSelectionDialog
import com.vultisig.wallet.ui.screens.v2.defi.hasBondPositions
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun MayaDeFiPositionsScreen(
    vaultId: VaultId,
    model: MayaDefiPositionsViewModel = hiltViewModel<MayaDefiPositionsViewModel>(),
) {
    val state by model.state.collectAsState()

    LaunchedEffect(Unit) {
        model.setData(vaultId = vaultId)
    }

    MayaDefiPositionScreenContent(
        state = state,
        onBackClick = model::onBackClick,
        onClickBondToNode = model::bondToNode,
        onClickUnbond = { model.onClickUnBond(it) },
        onClickBond = { model.onClickBond(it) },
        onTabSelected = model::onTabSelected,
        onEditPositionClick = { model.setPositionSelectionDialogVisibility(true) },
        onCancelEditPositionClick = { model.setPositionSelectionDialogVisibility(false) },
        onDonePositionClick = model::onPositionSelectionDone,
        onPositionSelectionChange = model::onPositionSelectionChange,
    )
}

@Composable
internal fun MayaDefiPositionScreenContent(
    state: MayaDefiPositionsUiModel = MayaDefiPositionsUiModel(),
    onBackClick: () -> Unit,
    onClickBondToNode: () -> Unit,
    onClickUnbond: (String) -> Unit,
    onClickBond: (String) -> Unit,
    onEditPositionClick: () -> Unit = {},
    onCancelEditPositionClick: () -> Unit = {},
    onDonePositionClick: () -> Unit = {},
    onPositionSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    onTabSelected: (String) -> Unit = {},
) {
    val searchTextFieldState = remember { TextFieldState() }

    val tabs = listOf(
        DeFiTab.BONDED.displayName,
    )

    V2Scaffold(
        onBackClick = onBackClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary),
            horizontalAlignment = CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BalanceBanner(
                title = Chain.MayaChain.raw,
                isLoading = state.isTotalAmountLoading,
                totalValue = state.totalAmountPrice,
                image = R.drawable.maya_defi_banner,
                isBalanceVisible = state.isBalanceVisible,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                VsTabGroup(
                    index = tabs.indexOf(state.selectedTab)
                ) {
                    tabs.forEach { tab ->
                        tab {
                            VsTab(
                                label = tab,
                                onClick = {
                                    onTabSelected(tab)
                                },
                            )
                        }
                    }
                }

                V2Container(
                    type = ContainerType.SECONDARY,
                    cornerType = CornerType.Circular,
                    modifier = Modifier
                        .clickOnce(onClick = {})
                ) {
                    UiIcon(
                        drawableResId = R.drawable.edit_chain,
                        size = 16.dp,
                        modifier = Modifier.padding(all = 12.dp),
                        tint = Theme.v2.colors.primary.accent4,
                        onClick = onEditPositionClick,
                    )
                }
            }

            if (state.showPositionSelectionDialog) {
                PositionsSelectionDialog(
                    bondPositions = state.bondPositionsDialog,
                    stakePositions = emptyList(),
                    selectedPositions = state.tempSelectedPositions,
                    searchTextFieldState = searchTextFieldState,
                    onPositionSelectionChange = onPositionSelectionChange,
                    onDoneClick = onDonePositionClick,
                    onCancelClick = onCancelEditPositionClick,
                )
            }

            when (state.selectedTab) {
                MayaDefiTab.BONDED.displayName -> {
                    if (!state.selectedPositions.hasBondPositions()) {
                        NoPositionsContainer(
                            onManagePositionsClick = onEditPositionClick
                        )
                    } else {
                        BondedTabContent(
                            bondToNodeOnClick = onClickBondToNode,
                            state = state.bonded,
                            isVisible = state.isBalanceVisible,
                            onClickUnbond = onClickUnbond,
                            onClickBond = onClickBond,
                        )
                    }
                }
            }

            UiSpacer(size = 16.dp)
        }
    }
}

@Composable
@Preview(showBackground = true, name = "Maya DeFi Positions - Empty")
private fun MayaDeFiPositionsScreenPreviewEmpty() {
    MayaDefiPositionScreenContent(
        onBackClick = { },
        state = MayaDefiPositionsUiModel(),
        onClickBond = {},
        onClickUnbond = {},
        onClickBondToNode = {},
        onTabSelected = {}
    )
}

@Composable
@Preview(showBackground = true, name = "Maya DeFi Positions - With Data")
private fun MayaDeFiPositionsScreenPreviewWithData() {
    val mockNodes = listOf(
        BondedNodeUiModel(
            address = "maya1abcd...xyz",
            fullAddress = "",
            status = BondNodeState.ACTIVE,
            apy = "15.2%",
            bondedAmount = "1500 MAYA",
            nextAward = "30 MAYA",
            nextChurn = "Nov 10, 25"
        ),
        BondedNodeUiModel(
            address = "maya1efgh...123",
            fullAddress = "",
            status = BondNodeState.STANDBY,
            apy = "14.5%",
            bondedAmount = "800 MAYA",
            nextAward = "18 MAYA",
            nextChurn = "Nov 11, 25"
        ),
        BondedNodeUiModel(
            address = "maya1ijkl...456",
            fullAddress = "",
            status = BondNodeState.READY,
            apy = "13.8%",
            bondedAmount = "1200 MAYA",
            nextAward = "25 MAYA",
            nextChurn = "Nov 12, 25"
        )
    )

    MayaDefiPositionScreenContent(
        onBackClick = { },
        state = MayaDefiPositionsUiModel(
            totalAmountPrice = "$4,850.00",
            selectedTab = MayaDefiTab.BONDED.displayName,
            bonded = BondedTabUiModel(
                isLoading = false,
                totalBondedAmount = "3500 MAYA",
                nodes = mockNodes
            )
        ),
        onClickBond = {},
        onClickUnbond = {},
        onClickBondToNode = {},
        onTabSelected = {}
    )
}

@Composable
@Preview(showBackground = true, name = "Maya DeFi Positions - Loading")
private fun MayaDeFiPositionsScreenPreviewLoading() {
    MayaDefiPositionScreenContent(
        onBackClick = { },
        state = MayaDefiPositionsUiModel(
            bonded = BondedTabUiModel(
                isLoading = true
            )
        ),
        onClickBond = {},
        onClickUnbond = {},
        onClickBondToNode = {},
        onTabSelected = {}
    )
}

@Composable
@Preview(showBackground = true, name = "Maya DeFi Positions - Hidden Balance")
private fun MayaDeFiPositionsScreenPreviewHiddenBalance() {
    val mockNodes = listOf(
        BondedNodeUiModel(
            address = "maya1abcd...xyz",
            fullAddress = "",
            status = BondNodeState.ACTIVE,
            apy = "15.2%",
            bondedAmount = "1500 MAYA",
            nextAward = "30 MAYA",
            nextChurn = "Nov 10, 25"
        )
    )

    MayaDefiPositionScreenContent(
        onBackClick = { },
        state = MayaDefiPositionsUiModel(
            totalAmountPrice = "$4,850.00",
            isBalanceVisible = false,
            selectedTab = MayaDefiTab.BONDED.displayName,
            bonded = BondedTabUiModel(
                isLoading = false,
                totalBondedAmount = "3500 MAYA",
                nodes = mockNodes
            )
        ),
        onClickBond = {},
        onClickUnbond = {},
        onClickBondToNode = {},
        onTabSelected = {}
    )
}

internal enum class MayaDefiTab(val displayName: String) {
    BONDED("Bonded");
}
