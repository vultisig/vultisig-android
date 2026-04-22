package com.vultisig.wallet.ui.screens.v2.defi.maya

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.vultisig.wallet.ui.models.defi.MayachainDefiPositionsUiModel
import com.vultisig.wallet.ui.models.defi.MayachainDefiPositionsViewModel
import com.vultisig.wallet.ui.models.defi.MayachainDefiUiState
import com.vultisig.wallet.ui.models.defi.ThorchainDefiPositionsUiModel
import com.vultisig.wallet.ui.screens.v2.defi.BalanceBanner
import com.vultisig.wallet.ui.screens.v2.defi.BondedTabContent
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.LpTabContent
import com.vultisig.wallet.ui.screens.v2.defi.NoPositionsContainer
import com.vultisig.wallet.ui.screens.v2.defi.PositionsSelectionDialog
import com.vultisig.wallet.ui.screens.v2.defi.StakingTabContent
import com.vultisig.wallet.ui.screens.v2.defi.hasBondPositions
import com.vultisig.wallet.ui.screens.v2.defi.hasLpPositions
import com.vultisig.wallet.ui.screens.v2.defi.hasMayaStakingPositions
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions.ADD_LP
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions.REMOVE_LP
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun MayachainDefiPositionsScreen(
    vaultId: VaultId,
    model: MayachainDefiPositionsViewModel = hiltViewModel(),
) {
    val uiState by model.state.collectAsState()

    LaunchedEffect(vaultId) { model.setData(vaultId = vaultId) }

    when (val s = uiState) {
        is MayachainDefiUiState.Loading -> Unit

        is MayachainDefiUiState.Error ->
            V2Scaffold(onBackClick = model::onBackClick) {
                Box(
                    modifier =
                        Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = s.message,
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.secondary,
                    )
                }
            }

        is MayachainDefiUiState.Success ->
            MayachainDefiPositionsScreenContent(
                state = s.data,
                onBackClick = model::onBackClick,
                onClickBondToNode = model::bondToNode,
                onClickBond = { model.onClickBond(it) },
                onClickUnbond = { model.onClickUnBond(it) },
                onTabSelected = model::onTabSelected,
                onEditPositionClick = { model.setPositionSelectionDialogVisibility(true) },
                onCancelEditPositionClick = { model.setPositionSelectionDialogVisibility(false) },
                onDonePositionClick = model::onPositionSelectionDone,
                onPositionSelectionChange = model::onPositionSelectionChange,
                onClickStake = { model.onNavigateToStake(it) },
                onClickUnstake = { model.onNavigateToStake(it) },
                onClickAddLp = { model.onNavigateToLp(it, ADD_LP) },
                onClickRemoveLp = { model.onNavigateToLp(it, REMOVE_LP) },
            )
    }
}

private val MAYA_DEFI_TABS = listOf(DeFiTab.BONDED, DeFiTab.STAKED, DeFiTab.LP)

@Composable
internal fun MayachainDefiPositionsScreenContent(
    state: MayachainDefiPositionsUiModel = MayachainDefiPositionsUiModel(),
    onBackClick: () -> Unit = {},
    onClickBondToNode: () -> Unit = {},
    onClickBond: (String) -> Unit = {},
    onClickUnbond: (String) -> Unit = {},
    onTabSelected: (DeFiTab) -> Unit = {},
    onEditPositionClick: () -> Unit = {},
    onCancelEditPositionClick: () -> Unit = {},
    onDonePositionClick: () -> Unit = {},
    onPositionSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    onClickStake: (DeFiNavActions) -> Unit = {},
    onClickUnstake: (DeFiNavActions) -> Unit = {},
    onClickAddLp: (String) -> Unit = {},
    onClickRemoveLp: (String) -> Unit = {},
) {
    val searchTextFieldState = remember { TextFieldState() }
    val tabs = MAYA_DEFI_TABS

    V2Scaffold(onBackClick = onBackClick) {
        Column(
            modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VsTabGroup(index = tabs.indexOfFirst { it.displayNameRes == state.selectedTab }) {
                    tabs.forEach { tab ->
                        tab {
                            VsTab(
                                label = androidx.compose.ui.res.stringResource(tab.displayNameRes),
                                onClick = { onTabSelected(tab) },
                            )
                        }
                    }
                }

                V2Container(
                    type = ContainerType.SECONDARY,
                    cornerType = CornerType.Circular,
                    modifier = Modifier.clickOnce(onClick = onEditPositionClick),
                ) {
                    UiIcon(
                        drawableResId = R.drawable.edit_chain,
                        size = 16.dp,
                        modifier = Modifier.padding(all = 12.dp),
                        tint = Theme.v2.colors.primary.accent4,
                    )
                }
            }

            if (state.showPositionSelectionDialog) {
                PositionsSelectionDialog(
                    bondPositions = state.bondPositionsDialog,
                    stakePositions = state.stakingPositionsDialog,
                    lpPositions = state.lpPositionsDialog,
                    selectedPositions = state.tempSelectedPositions,
                    searchTextFieldState = searchTextFieldState,
                    onPositionSelectionChange = onPositionSelectionChange,
                    onDoneClick = onDonePositionClick,
                    onCancelClick = onCancelEditPositionClick,
                )
            }

            when (state.selectedTab) {
                DeFiTab.BONDED.displayNameRes -> {
                    if (!state.selectedPositions.hasBondPositions()) {
                        NoPositionsContainer(onManagePositionsClick = onEditPositionClick)
                    } else {
                        BondedTabContent(
                            bondToNodeOnClick = onClickBondToNode,
                            state =
                                ThorchainDefiPositionsUiModel(
                                    bonded = state.bonded,
                                    isBalanceVisible = state.isBalanceVisible,
                                    totalAmountPrice = state.totalAmountPrice,
                                    isTotalAmountLoading = state.isTotalAmountLoading,
                                ),
                            onClickBond = onClickBond,
                            onClickUnbond = onClickUnbond,
                            coinName = "CACAO",
                            coinIconRes = R.drawable.cacao,
                        )
                    }
                }

                DeFiTab.STAKED.displayNameRes -> {
                    if (!state.selectedPositions.hasMayaStakingPositions()) {
                        NoPositionsContainer(onManagePositionsClick = onEditPositionClick)
                    } else {
                        StakingTabContent(
                            state = state.staking,
                            onClickStake = onClickStake,
                            onClickUnstake = onClickUnstake,
                            onClickWithdraw = {},
                            onClickTransfer = {},
                            isBalanceVisible = state.isBalanceVisible,
                        )
                    }
                }

                DeFiTab.LP.displayNameRes -> {
                    if (!state.selectedPositions.hasLpPositions(state.lpPositionsDialog)) {
                        NoPositionsContainer(onManagePositionsClick = onEditPositionClick)
                    } else {
                        LpTabContent(
                            state = state.lp,
                            onClickAdd = onClickAddLp,
                            onClickRemove = onClickRemoveLp,
                        )
                    }
                }
            }

            UiSpacer(size = 16.dp)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MayachainDefiPositionsScreenBondedTabPreview() {
    MayachainDefiPositionsScreenContent(
        state = MayachainDefiPositionsUiModel(selectedTab = DeFiTab.BONDED.displayNameRes)
    )
}

@Preview(showBackground = true)
@Composable
private fun MayachainDefiPositionsScreenStakedTabPreview() {
    MayachainDefiPositionsScreenContent(
        state = MayachainDefiPositionsUiModel(selectedTab = DeFiTab.STAKED.displayNameRes)
    )
}

@Preview(showBackground = true)
@Composable
private fun MayachainDefiPositionsScreenLpTabPreview() {
    MayachainDefiPositionsScreenContent(
        state = MayachainDefiPositionsUiModel(selectedTab = DeFiTab.LP.displayNameRes)
    )
}
