package com.vultisig.wallet.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.models.ChainDashboardUiModel
import com.vultisig.wallet.ui.models.ChainDashboardViewModel
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.ChainTokensUiModel
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.PositionCircle
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.PositionMaya
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.PositionTokens
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.PositionTron
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.Wallet
import com.vultisig.wallet.ui.screens.v2.chaintokens.ChainTokensScreen
import com.vultisig.wallet.ui.screens.v2.defi.circle.CircleDeFiPositionsScreen
import com.vultisig.wallet.ui.screens.v2.defi.maya.MayachainDefiPositionsScreen
import com.vultisig.wallet.ui.screens.v2.defi.thorchain.ThorchainDefiPositionsScreen
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
        content = {
            when (val route = uiModel.route) {
                is PositionCircle -> CircleDeFiPositionsScreen(vaultId = route.vaultId)
                is PositionTokens -> ThorchainDefiPositionsScreen(vaultId = route.vaultId)
                is PositionMaya ->
                    MayachainDefiPositionsScreen(vaultId = (uiModel.route as PositionMaya).vaultId)
                is PositionTron -> TronDeFiPositionsScreen(vaultId = route.vaultId)
                is Wallet -> ChainTokensScreen(vaultId = route.vaultId, chainId = route.chainId)
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
    content: @Composable () -> Unit = {},
) {
    Scaffold(
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
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
            content()
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
                onRefresh = {},
                onShowSearchBar = {},
                onHideSearchBar = {},
                onSend = {},
                onSwap = {},
                onBuy = {},
                onDeposit = {},
                onReceive = {},
                onSelectTokens = {},
                onTokenClick = {},
                onBackClick = {},
                onShowReviewPopUp = {},
            )
        },
    )
}
