package com.vultisig.wallet.ui.screens.v2.defi.circle

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.screens.v2.defi.BaseDeFiPositionsScreenContent
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.DeFiWarningBanner
import com.vultisig.wallet.ui.screens.v2.defi.HeaderDeFiWidget
import com.vultisig.wallet.ui.screens.v2.defi.model.DefiUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun CircleDeFiPositionsScreen(
    viewModel: CircleDeFiPositionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    CircleDefiPositionScreenContent(
        state = state,
        tabs = listOf(DeFiTab.DEPOSITED.displayName),
        onBackClick = viewModel::onBackClick,
        onTabSelected = viewModel::onTabSelected,
        onClickCloseWarning = viewModel::onClickCloseWarning,
        onDepositAccount = { },
        onCreateAccount = viewModel::onCreateAccount,
    )
}

@Composable
internal fun CircleDefiPositionScreenContent(
    state: DefiUiModel,
    tabs: List<String> = listOf(DeFiTab.DEPOSITED.displayName),
    onBackClick: () -> Unit,
    onTabSelected: (String) -> Unit = {},
    onEditChains: () -> Unit = {},
    onClickCloseWarning: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
    onDepositAccount: () -> Unit = {},
) {
    BaseDeFiPositionsScreenContent(
        state = state,
        tabs = tabs,
        bannerImage = R.drawable.circle_defi_banner,
        onBackClick = onBackClick,
        onTabSelected = onTabSelected,
        onEditChains = onEditChains,
        tabContent = {
            CircleContentDepositTab(
                state = state.circleDefi,
                isBalanceVisible = state.isBalanceVisible,
                onClickAction = {
                    if (state.circleDefi.isAccountOpen) {
                        onDepositAccount()
                    } else {
                        onCreateAccount()
                    }
                },
                onClickCloseWarning = onClickCloseWarning,
            )
        }
    )
}

@Composable
private fun CircleContentDepositTab(
    state: DefiUiModel.CircleDeFi,
    isBalanceVisible: Boolean,
    onClickAction: () -> Unit,
    onClickCloseWarning: () -> Unit,
) {
    Text(
        text = stringResource(R.string.circle_defi_description),
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.text.light,
    )

    if (!state.closeWarning) {
        DeFiWarningBanner(
            text = stringResource(R.string.circle_defi_control_info),
            onClickClose = onClickCloseWarning,
        )
    }

    HeaderDeFiWidget(
        title = stringResource(R.string.usdc_deposit_title),
        iconRes = R.drawable.usdc,
        buttonText = if (state.isAccountOpen) {
            stringResource(R.string.deposit_usdc_button)
        } else {
            stringResource(R.string.open_account_button)
        },
        onClickAction = onClickAction,
        totalAmount = state.totalDeposit,
        isLoading = state.isLoading,
        isBalanceVisible = isBalanceVisible,
    )
}

@Preview(showBackground = true)
@Composable
private fun CircleDeFiPositionsScreenPreview() {
    CircleDefiPositionScreenContent(
        state = DefiUiModel(),
        tabs = listOf(DeFiTab.DEPOSITED.displayName),
        onBackClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun CircleDefiPositionScreenContentPreview() {
    CircleDefiPositionScreenContent(
        state = DefiUiModel(
            totalAmountPrice = "$12,345.67",
            isTotalAmountLoading = false,
            isBalanceVisible = true,
            supportEditChains = true,
            selectedTab = DeFiTab.DEPOSITED.displayName,
            bannerImage = R.drawable.circle_defi_banner
        ),
        tabs = listOf(DeFiTab.DEPOSITED.displayName),
        onBackClick = {},
        onTabSelected = {},
        onEditChains = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun CircleDefiPositionScreenContentLoadingPreview() {
    CircleDefiPositionScreenContent(
        state = DefiUiModel(
            totalAmountPrice = "$0.00",
            isTotalAmountLoading = true,
            isBalanceVisible = true,
            supportEditChains = false,
            selectedTab = DeFiTab.DEPOSITED.displayName,
            bannerImage = R.drawable.circle_defi_banner
        ),
        tabs = listOf(DeFiTab.DEPOSITED.displayName),
        onBackClick = {},
        onTabSelected = {},
        onEditChains = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun CircleDefiPositionScreenContentHiddenBalancePreview() {
    CircleDefiPositionScreenContent(
        state = DefiUiModel(
            totalAmountPrice = "$99,999.99",
            isTotalAmountLoading = false,
            isBalanceVisible = false,
            supportEditChains = true,
            selectedTab = DeFiTab.DEPOSITED.displayName,
            bannerImage = R.drawable.circle_defi_banner
        ),
        tabs = listOf(DeFiTab.DEPOSITED.displayName),
        onBackClick = {},
        onTabSelected = {},
        onEditChains = {}
    )
}