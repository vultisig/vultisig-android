package com.vultisig.wallet.ui.screens.v2.defi.circle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.screens.v2.defi.BaseDeFiPositionsScreenContent
import com.vultisig.wallet.ui.screens.v2.defi.model.DefiUiModel

@Composable
internal fun CircleDeFiPositionsScreen(
    viewModel: CircleDeFiPositionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    CircleDefiPositionScreenContent(
        state = state,
        tabs = listOf(CircleDefiTab.DEPOSITED.displayName),
        onBackClick = {

        },
        onTabSelected = viewModel::onTabSelected,
    )
}

@Composable
internal fun CircleDefiPositionScreenContent(
    state: DefiUiModel,
    tabs: List<String> = listOf(CircleDefiTab.DEPOSITED.displayName),
    onBackClick: () -> Unit,
    onTabSelected: (String) -> Unit = {},
    onEditChains: () -> Unit = {},
) {
    BaseDeFiPositionsScreenContent(
        state = state,
        tabs = tabs,
        bannerImage = R.drawable.circle_defi_banner,
        onBackClick = onBackClick,
        onTabSelected = onTabSelected,
        onEditChains = onEditChains,
        tabContent = {

        }
    )
}

internal enum class CircleDefiTab(val displayName: String) {
    DEPOSITED("Deposited"),
}

@Preview(showBackground = true)
@Composable
private fun CircleDeFiPositionsScreenPreview() {
    CircleDefiPositionScreenContent(
        state = DefiUiModel(),
        tabs = listOf(CircleDefiTab.DEPOSITED.displayName),
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
            selectedTab = CircleDefiTab.DEPOSITED.displayName,
            bannerImage = R.drawable.circle_defi_banner
        ),
        tabs = listOf(CircleDefiTab.DEPOSITED.displayName),
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
            selectedTab = CircleDefiTab.DEPOSITED.displayName,
            bannerImage = R.drawable.circle_defi_banner
        ),
        tabs = listOf(CircleDefiTab.DEPOSITED.displayName),
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
            selectedTab = CircleDefiTab.DEPOSITED.displayName,
            bannerImage = R.drawable.circle_defi_banner
        ),
        tabs = listOf(CircleDefiTab.DEPOSITED.displayName),
        onBackClick = {},
        onTabSelected = {},
        onEditChains = {}
    )
}