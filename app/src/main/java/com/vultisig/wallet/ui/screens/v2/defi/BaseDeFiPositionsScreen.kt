package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.screens.v2.defi.model.DefiUiModel
import com.vultisig.wallet.ui.screens.v2.home.components.VsTabs
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun BaseDeFiPositionsScreenContent(
    state: DefiUiModel,
    tabs: List<String>,
    bannerImage: Int = R.drawable.referral_data_banner,
    onBackClick: () -> Unit,
    onTabSelected: (String) -> Unit = {},
    onEditChains: () -> Unit = {},
    tabContent: @Composable () -> Unit = {},
) {
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
                isLoading = state.isTotalAmountLoading,
                totalValue = state.totalAmountPrice,
                image = bannerImage,
                isBalanceVisible = state.isBalanceVisible,
            )

            VsTabs(
                tabs = tabs,
                onTabSelected = onTabSelected,
                selectedTab = state.selectedTab,
                content = {
                    if (state.supportEditChains) {
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
                                onClick = onEditChains,
                            )
                        }
                    }
                }
            )

            if (state.containsTabDescription) {
                Text(
                    text = stringResource(R.string.circle_defi_description),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.light,
                )
            }

            tabContent()
        }
    }
}

enum class DeFiTab(val displayName: String) {
    DEPOSITED("Deposited"),
    STAKED("Staked"),
    BONDED("Bonded"),
    LP("LP"),
}

@Preview(showBackground = true)
@Composable
private fun BaseDeFiPositionsScreenContentPreview() {
        BaseDeFiPositionsScreenContent(
            state = DefiUiModel(
                totalAmountPrice = "$12,345.67",
                isTotalAmountLoading = false,
                isBalanceVisible = true,
                supportEditChains = true,
                selectedTab = DeFiTab.DEPOSITED.displayName
            ),
            tabs = listOf(DeFiTab.DEPOSITED.displayName, DeFiTab.STAKED.displayName),
            onBackClick = {},
            onTabSelected = {},
            onEditChains = {},
            tabContent = {}
        )
}

@Preview(showBackground = true)
@Composable
private fun BaseDeFiPositionsScreenContentLoadingPreview() {
        BaseDeFiPositionsScreenContent(
            state = DefiUiModel(
                totalAmountPrice = "$0.00",
                isTotalAmountLoading = true,
                isBalanceVisible = true,
                supportEditChains = false,
                selectedTab = DeFiTab.DEPOSITED.displayName
            ),
            tabs = listOf(DeFiTab.DEPOSITED.displayName),
            onBackClick = {},
            onTabSelected = {},
            onEditChains = {},
            tabContent = {}
        )
}

@Preview(showBackground = true)
@Composable
private fun BaseDeFiPositionsScreenContentHiddenBalancePreview() {
        BaseDeFiPositionsScreenContent(
            state = DefiUiModel(
                totalAmountPrice = "$99,999.99",
                isTotalAmountLoading = false,
                isBalanceVisible = false,
                supportEditChains = true,
                selectedTab = DeFiTab.STAKED.displayName
            ),
            tabs = listOf(DeFiTab.DEPOSITED.displayName, DeFiTab.STAKED.displayName, DeFiTab.BONDED.displayName),
            onBackClick = {},
            onTabSelected = {},
            onEditChains = {},
            tabContent = {}
        )
}