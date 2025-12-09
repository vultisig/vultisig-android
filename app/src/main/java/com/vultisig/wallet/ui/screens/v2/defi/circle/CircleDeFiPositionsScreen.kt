package com.vultisig.wallet.ui.screens.v2.defi.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.screens.v2.defi.BalanceBanner
import com.vultisig.wallet.ui.screens.v2.defi.model.DefiUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun CircleDeFiPositionsScreen() {
    //val state by model.state.collectAsState()
}

@Composable
internal fun CircleDefiPositionScreenContent(
    state: DefiUiModel,
    tabTitles: List<String> = listOf(CircleDefiTab.DEPOSITED.displayName),
    onBackClick: () -> Unit,
    onTabSelected: (String) -> Unit = {},
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
                image = R.drawable.referral_data_banner,
                isBalanceVisible = state.isBalanceVisible,
            )
        }
    }
}

internal enum class CircleDefiTab(val displayName: String) {
    DEPOSITED("Deposited"),
}