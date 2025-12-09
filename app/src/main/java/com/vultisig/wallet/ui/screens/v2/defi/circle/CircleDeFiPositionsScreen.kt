package com.vultisig.wallet.ui.screens.v2.defi.circle

import androidx.compose.runtime.Composable

@Composable
internal fun CircleDeFiPositionsScreen() {
    //val state by model.state.collectAsState()

}

@Composable
internal fun CircleDefiPositionScreenContent(
    tabTitles: List<String>,
    onBackClick: () -> Unit,
    onTabSelected: (String) -> Unit = {},
) {

}

internal enum class CircleDefiTab(val displayName: String) {
    DEPOSIT("Deposit"),
}