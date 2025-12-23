package com.vultisig.wallet.ui.screens.v2.defi.thorchain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.containers.VsContainerType
import com.vultisig.wallet.ui.components.containers.VsContainerCornerType
import com.vultisig.wallet.ui.components.containers.VsContainer
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.models.defi.BondedNodeUiModel
import com.vultisig.wallet.ui.models.defi.BondedTabUiModel
import com.vultisig.wallet.ui.models.defi.ThorchainDefiPositionsViewModel
import com.vultisig.wallet.ui.models.defi.ThorchainDefiPositionsUiModel
import com.vultisig.wallet.ui.screens.v2.defi.BalanceBanner
import com.vultisig.wallet.ui.screens.v2.defi.BondedTabContent
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.NoPositionsContainer
import com.vultisig.wallet.ui.screens.v2.defi.PositionsSelectionDialog
import com.vultisig.wallet.ui.screens.v2.defi.StakingTabContent
import com.vultisig.wallet.ui.screens.v2.defi.hasBondPositions
import com.vultisig.wallet.ui.screens.v2.defi.hasStakingPositions
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.home.components.VsTabs
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ThorchainDefiPositionsScreen(
    model: ThorchainDefiPositionsViewModel = hiltViewModel<ThorchainDefiPositionsViewModel>(),
) {
    val state by model.state.collectAsState()

    ThorchainDefiPositionScreenContent(
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
        onClickWithdraw = { model.onNavigateToFunctions(it) },
        onClickStake = { model.onNavigateToFunctions(it) },
        onClickUnstake = { model.onNavigateToFunctions(it) },
    )
}

@Composable
internal fun ThorchainDefiPositionScreenContent(
    state: ThorchainDefiPositionsUiModel = ThorchainDefiPositionsUiModel(),
    onBackClick: () -> Unit,
    onClickBondToNode: () -> Unit,
    onClickUnbond: (String) -> Unit,
    onClickBond: (String) -> Unit,
    onEditPositionClick: () -> Unit = {},
    onCancelEditPositionClick: () -> Unit = {},
    onDonePositionClick: () -> Unit = {},
    onPositionSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    onTabSelected: (String) -> Unit = {},
    onClickWithdraw: (DeFiNavActions) -> Unit = {},
    onClickStake: (DeFiNavActions) -> Unit = {},
    onClickUnstake: (DeFiNavActions) -> Unit = {},
) {
    val searchTextFieldState = remember { TextFieldState() }

    val tabs = listOf(
        DeFiTab.BONDED.displayName,
        DeFiTab.STAKED.displayName,
    )

    VsScaffold(
        onBackClick = onBackClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme.colors.backgrounds.primary),
            horizontalAlignment = CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BalanceBanner(
                title = Chain.ThorChain.raw,
                isLoading = state.isTotalAmountLoading,
                totalValue = state.totalAmountPrice,
                image = R.drawable.referral_data_banner,
                isBalanceVisible = state.isBalanceVisible,
            )

            VsTabs(
                tabs = tabs,
                onTabSelected = onTabSelected,
                selectedTab = state.selectedTab,
                content = {
                    VsContainer(
                        type = VsContainerType.SECONDARY,
                        vsContainerCornerType = VsContainerCornerType.Circular,
                        modifier = Modifier
                            .clickOnce(onClick = {})
                    ) {
                        UiIcon(
                            drawableResId = R.drawable.edit_chain,
                            size = 16.dp,
                            modifier = Modifier.padding(all = 12.dp),
                            tint = Theme.colors.primary.accent4,
                            onClick = onEditPositionClick,
                        )
                    }
                }
            )

            if (state.showPositionSelectionDialog) {
                PositionsSelectionDialog(
                    bondPositions = state.bondPositionsDialog,
                    stakePositions = state.stakingPositionsDialog,
                    selectedPositions = state.tempSelectedPositions,
                    searchTextFieldState = searchTextFieldState,
                    onPositionSelectionChange = onPositionSelectionChange,
                    onDoneClick = onDonePositionClick,
                    onCancelClick = onCancelEditPositionClick,
                )
            }

            when (state.selectedTab) {
                ThorchainDefiTab.BONDED.displayName -> {
                    if (!state.selectedPositions.hasBondPositions()) {
                        NoPositionsContainer(
                            onManagePositionsClick = onEditPositionClick
                        )
                    } else {
                        BondedTabContent(
                            bondToNodeOnClick = onClickBondToNode,
                            state = state,
                            onClickUnbond = onClickUnbond,
                            onClickBond = onClickBond,
                        )
                    }
                }

                ThorchainDefiTab.STAKING.displayName -> {
                    if (!state.selectedPositions.hasStakingPositions()) {
                        NoPositionsContainer(
                            onManagePositionsClick = onEditPositionClick
                        )
                    } else {
                        StakingTabContent(
                            state = state.staking,
                            onClickStake = onClickStake,
                            onClickUnstake = onClickUnstake,
                            onClickWithdraw = { onClickWithdraw(DeFiNavActions.WITHDRAW_RUJI) },
                            isBalanceVisible = state.isBalanceVisible,
                        )
                    }
                }

                ThorchainDefiTab.LPS.displayName -> {
                    NoPositionsContainer(
                        onManagePositionsClick = onEditPositionClick
                    )
                }
            }
            
            UiSpacer(size = 16.dp)
        }
    }
}

@Composable
@Preview(showBackground = true, name = "DeFi Positions - Empty")
private fun ThorchainDefiPositionsScreenPreviewEmpty() {
    ThorchainDefiPositionScreenContent(
        onBackClick = { },
        state = ThorchainDefiPositionsUiModel(),
        onClickBond = {},
        onClickUnbond = {},
        onClickBondToNode = {},
        onTabSelected = {}
    )
}

@Composable
@Preview(showBackground = true, name = "DeFi Positions - With Data")
private fun ThorchainDefiPositionsScreenPreviewWithData() {
    val mockNodes = listOf(
        BondedNodeUiModel(
            address = "thor1abcd...xyz",
            fullAddress = "",
            status = BondNodeState.ACTIVE,
            apy = "12.5%",
            bondedAmount = "1000 RUNE",
            nextAward = "20 RUNE",
            nextChurn = "Oct 15, 25"
        ),
        BondedNodeUiModel(
            address = "thor1efgh...123",
            fullAddress = "",
            status = BondNodeState.STANDBY,
            apy = "11.2%",
            bondedAmount = "500 RUNE",
            nextAward = "10 RUNE",
            nextChurn = "Oct 16, 25"
        ),
        BondedNodeUiModel(
            address = "thor1ijkl...456",
            fullAddress = "",
            status = BondNodeState.READY,
            apy = "10.8%",
            bondedAmount = "750 RUNE",
            nextAward = "15 RUNE",
            nextChurn = "Oct 17, 25"
        )
    )

    ThorchainDefiPositionScreenContent(
        onBackClick = { },
        state = ThorchainDefiPositionsUiModel(
            totalAmountPrice = "$3,250.00",
            selectedTab = ThorchainDefiTab.BONDED.displayName,
            bonded = BondedTabUiModel(
                isLoading = false,
                totalBondedAmount = "2250 RUNE",
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
@Preview(showBackground = true, name = "DeFi Positions - Loading")
private fun ThorchainDefiPositionsScreenPreviewLoading() {
    ThorchainDefiPositionScreenContent(
        onBackClick = { },
        state = ThorchainDefiPositionsUiModel(
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

internal enum class ThorchainDefiTab(val displayName: String) {
    BONDED("Bonded"),
    STAKING("Staked"),
    LPS("LPs");
}