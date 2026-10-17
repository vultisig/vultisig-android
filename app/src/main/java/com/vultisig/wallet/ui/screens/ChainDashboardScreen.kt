package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.components.v2.topbar.V2Topbar
import com.vultisig.wallet.ui.models.vault.ChainDashboardUiModel
import com.vultisig.wallet.ui.models.vault.ChainDashboardViewModel
import com.vultisig.wallet.ui.models.vault.ChainTokenUiModel
import com.vultisig.wallet.ui.models.vault.ChainTokensUiModel
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.PositionCircle
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.PositionMaya
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.PositionTokens
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.PositionTon
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.PositionTron
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.Wallet
import com.vultisig.wallet.ui.screens.v2.chaintokens.ChainTokensScreen
import com.vultisig.wallet.ui.screens.v2.defi.circle.CircleDeFiPositionsScreen
import com.vultisig.wallet.ui.screens.v2.defi.maya.MayachainDefiPositionsScreen
import com.vultisig.wallet.ui.screens.v2.defi.thorchain.ThorchainDefiPositionsScreen
import com.vultisig.wallet.ui.screens.v2.defi.ton.TonDeFiPositionsScreen
import com.vultisig.wallet.ui.screens.v2.defi.tron.TronDeFiPositionsScreen
import com.vultisig.wallet.ui.screens.v2.home.components.BottomNavigatorOverlay
import com.vultisig.wallet.ui.screens.v2.home.components.CryptoConnectionPager
import com.vultisig.wallet.ui.models.vault.ONLY_WALLET
import com.vultisig.wallet.ui.screens.v2.home.components.rememberCryptoConnectionPagerState
import com.vultisig.wallet.ui.screens.v2.home.components.rememberCryptoConnectionTabClick
import com.vultisig.wallet.ui.screens.v2.home.components.rememberPagerIndicatorPosition

@Composable
internal fun ChainDashboardScreen(viewModel: ChainDashboardViewModel = hiltViewModel()) {
    val uiModel by viewModel.uiState.collectAsState()

    ChainDashboardScreen(
        uiModel = uiModel,
        onTypeSettle = viewModel::updateCryptoConnectionType,
        onCameraClick = viewModel::openCamera,
        onBackClick = viewModel::back,
        page = { route ->
            when (route) {
                is PositionCircle -> CircleDeFiPositionsScreen(vaultId = route.vaultId)
                is PositionTokens ->
                    ThorchainDefiPositionsScreen(vaultId = route.vaultId, initialTab = route.tab)
                is PositionMaya -> MayachainDefiPositionsScreen(vaultId = route.vaultId)
                is PositionTron -> TronDeFiPositionsScreen(vaultId = route.vaultId)
                is PositionTon -> TonDeFiPositionsScreen(vaultId = route.vaultId)
                is ChainDashboardRoute.PositionSolana ->
                    com.vultisig.wallet.ui.screens.v2.defi.solana.SolanaStakingPositionsScreen(
                        vaultId = route.vaultId
                    )
                is ChainDashboardRoute.PositionCosmosStaking ->
                    com.vultisig.wallet.ui.screens.cosmosstaking.CosmosStakingPositionsScreen(
                        vaultId = route.vaultId,
                        chainId = route.chainId,
                    )
                is Wallet ->
                    ChainTokensScreen(
                        vaultId = route.vaultId,
                        chainId = route.chainId,
                        onBackClick = viewModel::back,
                    )
            }
        },
    )
}

@Composable
private fun ChainDashboardScreen(
    uiModel: ChainDashboardUiModel,
    onTypeSettle: (CryptoConnectionType) -> Unit,
    onCameraClick: () -> Unit,
    onBackClick: () -> Unit,
    page: @Composable (ChainDashboardRoute) -> Unit = {},
) {
    val types = uiModel.availableCryptoTypes
    val pagerState = rememberCryptoConnectionPagerState(types, uiModel.cryptoConnectionType)
    val onTabClick = rememberCryptoConnectionTabClick(pagerState, types)
    val indicatorPosition = rememberPagerIndicatorPosition(pagerState)

    BottomNavigatorOverlay(
        isNavigatorVisible = uiModel.isBottomBarVisible,
        activeType = uiModel.cryptoConnectionType,
        availableCryptoTypes = types,
        indicatorPosition = indicatorPosition,
        onTypeClick = onTabClick,
        onCameraClick = onCameraClick,
    ) {
        CryptoConnectionPager(
            state = pagerState,
            types = types,
            selectedType = uiModel.cryptoConnectionType,
            onSettle = onTypeSettle,
            // The navigator hides while the token search is open; the search keeps its tab.
            userScrollEnabled = uiModel.isBottomBarVisible,
            modifier = Modifier.fillMaxSize(),
        ) { type ->
            val route = uiModel.routes.getValue(type)
            Scaffold(
                topBar = {
                    if (route !is Wallet) {
                        V2Topbar(title = null, onBackClick = onBackClick)
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
                    page(route)
                }
            }
        }
    }
}

@Preview
@Composable
private fun ChainDashboardScreenPreview() {
    ChainDashboardScreen(
        uiModel =
            ChainDashboardUiModel(
                routes = mapOf(CryptoConnectionType.Wallet to Wallet(vaultId = "sdsda", "007")),
                availableCryptoTypes = ONLY_WALLET,
            ),
        onTypeSettle = {},
        onCameraClick = {},
        onBackClick = {},
        page = {
            ChainTokensScreen(
                uiModel =
                    ChainTokensUiModel(
                        chainName = "Ethereum",
                        chainAddress = "0x1234567890abcdef",
                        totalBalance = "$1,234.56",
                        canSwap = true,
                        canBuy = false,
                        canDeposit = false,
                        tokens =
                            listOf(
                                ChainTokenUiModel(
                                    name = "Ethereum",
                                    balance = "0.5",
                                    fiatBalance = "$1,234.56",
                                )
                            ),
                    ),
                onBackClick = {},
                onRefresh = {},
                onShowSearchBar = {},
                onHideSearchBar = {},
                onSend = {},
                onSwap = {},
                onBuy = {},
                onDeposit = {},
                onReceive = {},
                onHistory = {},
                onSelectTokens = {},
                onTokenClick = {},
            )
        },
    )
}
