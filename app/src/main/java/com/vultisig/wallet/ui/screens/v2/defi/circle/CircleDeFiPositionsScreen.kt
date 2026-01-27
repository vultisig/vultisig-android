package com.vultisig.wallet.ui.screens.v2.defi.circle

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.screens.v2.defi.BaseDeFiPositionsScreenContent
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.DeFiWarningBanner
import com.vultisig.wallet.ui.screens.v2.defi.HeaderDeFiWidget
import com.vultisig.wallet.ui.screens.v2.defi.model.DefiUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun CircleDeFiPositionsScreen(
    vaultId: VaultId,
    viewModel: CircleDeFiPositionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setData(vaultId)
    }

    CircleDefiPositionScreenContent(
        state = state,
        tabs = listOf(DeFiTab.DEPOSITED),
        onBackClick = viewModel::onBackClick,
        onTabSelected = viewModel::onTabSelected,
        onClickCloseWarning = viewModel::onClickCloseWarning,
        onDepositAccount = viewModel::onDepositAccount,
        onCreateAccount = viewModel::onCreateAccount,
        onClickWithdraw = viewModel::onWithdrawAccount,
    )
}

@Composable
internal fun CircleDefiPositionScreenContent(
    state: DefiUiModel,
    tabs: List<DeFiTab> = listOf(DeFiTab.DEPOSITED),
    onBackClick: () -> Unit,
    onTabSelected: (DeFiTab) -> Unit = {},
    onEditChains: () -> Unit = {},
    onClickCloseWarning: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
    onDepositAccount: () -> Unit = {},
    onClickWithdraw: () -> Unit = {},
) {
    BaseDeFiPositionsScreenContent(
        state = state,
        tabs = tabs,
        bannerTitle = stringResource(R.string.circle_usdc_account),
        bannerImage = R.drawable.circle_defi_banner,
        onBackClick = onBackClick,
        onTabSelected = onTabSelected,
        onEditChains = onEditChains,
        tabContent = {
            CircleContentDepositTab(
                state = state.circleDefi,
                isBalanceVisible = state.isBalanceVisible,
                onClickDepositOrCreateAccount = {
                    if (state.circleDefi.isAccountOpen) {
                        onDepositAccount()
                    } else {
                        onCreateAccount()
                    }
                },
                onClickCloseWarning = onClickCloseWarning,
                onClickWithdraw = onClickWithdraw,
            )
        }
    )
}

@Composable
private fun CircleContentDepositTab(
    state: DefiUiModel.CircleDeFi,
    isBalanceVisible: Boolean,
    onClickDepositOrCreateAccount: () -> Unit,
    onClickWithdraw: () -> Unit,
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

    if (state.hasActiveDeposit()) {
        HeaderDeFiWidget(
            title = stringResource(R.string.usdc_deposit_title),
            iconRes = R.drawable.usdc,
            buttonFirstActionText = stringResource(R.string.withdraw),
            buttonSecondActionText = stringResource(R.string.deposit_usdc_button),
            onClickFirstAction = onClickWithdraw,
            onClickSecondAction = onClickDepositOrCreateAccount,
            totalAmount = state.totalDeposit,
            totalPrice = state.totalDepositCurrency,
            isLoading = state.isLoading,
            isBalanceVisible = isBalanceVisible,
        )
    } else {
        HeaderDeFiWidget(
            title = stringResource(R.string.usdc_deposit_title),
            iconRes = R.drawable.usdc,
            buttonText = if (state.isAccountOpen) {
                stringResource(R.string.deposit_usdc_button)
            } else {
                stringResource(R.string.open_account_button)
            },
            onClickAction = onClickDepositOrCreateAccount,
            totalAmount = state.totalDeposit,
            totalPrice = state.totalDepositCurrency,
            isLoading = state.isLoading,
            isBalanceVisible = isBalanceVisible,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CircleDeFiPositionsScreenPreview() {
    CircleDefiPositionScreenContent(
        state = DefiUiModel(),
        tabs = listOf(DeFiTab.DEPOSITED),
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
            selectedTab = DeFiTab.DEPOSITED.displayNameRes,
            bannerImage = R.drawable.circle_defi_banner
        ),
        tabs = listOf(DeFiTab.DEPOSITED),
        onBackClick = { },
        onTabSelected = { },
        onEditChains = { }
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
            selectedTab = DeFiTab.DEPOSITED.displayNameRes,
            bannerImage = R.drawable.circle_defi_banner
        ),
        tabs = listOf(DeFiTab.DEPOSITED),
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
            selectedTab = DeFiTab.DEPOSITED.displayNameRes,
            bannerImage = R.drawable.circle_defi_banner
        ),
        tabs = listOf(DeFiTab.DEPOSITED),
        onBackClick = {},
        onTabSelected = {},
        onEditChains = {}
    )
}