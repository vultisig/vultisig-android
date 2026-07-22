package com.vultisig.wallet.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.topbar.V2Topbar
import com.vultisig.wallet.ui.models.ChainDashboardUiModel
import com.vultisig.wallet.ui.models.ChainDashboardViewModel
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.ChainTokensUiModel
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
import com.vultisig.wallet.ui.screens.v2.home.components.CameraButton
import com.vultisig.wallet.ui.screens.v2.home.components.CryptoConnectionSelect

@Composable
internal fun ChainDashboardScreen(viewModel: ChainDashboardViewModel = hiltViewModel()) {
    val uiModel by viewModel.uiState.collectAsState()

    ChainDashboardScreen(
        uiModel = uiModel,
        onTypeClick = viewModel::updateCryptoConnectionType,
        onCameraClick = viewModel::openCamera,
        onBackClick = viewModel::back,
        content = {
            when (val route = uiModel.route) {
                is PositionCircle -> CircleDeFiPositionsScreen(vaultId = route.vaultId)
                is PositionTokens -> ThorchainDefiPositionsScreen(vaultId = route.vaultId)
                is PositionMaya ->
                    MayachainDefiPositionsScreen(vaultId = (uiModel.route as PositionMaya).vaultId)
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
                null -> Unit
            }
        },
    )
}

@Composable
private fun ChainDashboardScreen(
    uiModel: ChainDashboardUiModel,
    onTypeClick: (CryptoConnectionType) -> Unit,
    onCameraClick: () -> Unit,
    onBackClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    var topBarAction by remember { mutableStateOf<ChainDashboardTopBarAction?>(null) }

    LaunchedEffect(uiModel.route?.javaClass) { topBarAction = null }

    Scaffold(
        topBar = {
            if (uiModel.route !is Wallet) {
                V2Topbar(
                    title = null,
                    onBackClick = onBackClick,
                    actions =
                        topBarAction?.let { action ->
                            {
                                VsCircleButton(
                                    onClick = action.onClick,
                                    size = VsCircleButtonSize.Small,
                                    type = VsCircleButtonType.Secondary,
                                    designType = DesignType.Shined,
                                    icon = action.icon,
                                )
                            }
                        },
                )
            }
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                if (uiModel.isBottomBarVisible) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        CryptoConnectionSelect(
                            activeType = uiModel.cryptoConnectionType,
                            availableCryptoTypes = uiModel.availableCryptoTypes,
                            onTypeClick = onTypeClick,
                        )
                        CameraButton(onClick = onCameraClick)
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier.padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                )
        ) {
            CompositionLocalProvider(
                LocalChainDashboardTopBarActionSetter provides { topBarAction = it }
            ) {
                content()
            }
        }
    }
}

@Preview
@Composable
private fun ChainDashboardScreenPreview() {
    ChainDashboardScreen(
        uiModel = ChainDashboardUiModel(route = Wallet(vaultId = "sdsda", "007")),
        onTypeClick = {},
        onCameraClick = {},
        onBackClick = {},
        content = {
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
                onShowReviewPopUp = {},
            )
        },
    )
}
