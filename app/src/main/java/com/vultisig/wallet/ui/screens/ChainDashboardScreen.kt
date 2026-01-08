package com.vultisig.wallet.ui.screens

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
import com.vultisig.wallet.ui.models.ChainDashboardViewModel
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.PositionCircle
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.PositionTokens
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute.Wallet
import com.vultisig.wallet.ui.screens.v2.chaintokens.ChainTokensScreen
import com.vultisig.wallet.ui.screens.v2.defi.circle.CircleDeFiPositionsScreen
import com.vultisig.wallet.ui.screens.v2.defi.maya.MayaDeFiPositionsScreen
import com.vultisig.wallet.ui.screens.v2.home.components.CameraButton
import com.vultisig.wallet.ui.screens.v2.home.components.CryptoConnectionSelect


@Composable
internal fun ChainDashboardScreen(
    viewModel: ChainDashboardViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = if (uiModel.isBottomBarVisible) {
            {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        CryptoConnectionSelect(
                            activeType = uiModel.cryptoConnectionType,
                            availableCryptoTypes = uiModel.availableCryptoTypes,
                            onTypeClick = viewModel::updateCryptoConnectionType,
                        )
                        CameraButton(
                            onClick = viewModel::openCamera
                        )
                    }
                }
            }
        } else {
            {}
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(
                    bottom = paddingValues.calculateBottomPadding()
                )
        ) {
            when (uiModel.route) {
                is PositionCircle -> CircleDeFiPositionsScreen(
                    vaultId = (uiModel.route as PositionCircle).vaultId
                )

                is PositionTokens -> MayaDeFiPositionsScreen(
                    vaultId = (uiModel.route as PositionTokens).vaultId
                )
                //  ThorchainDefiPositionsScreen(
                //      vaultId = (uiModel.route as PositionTokens).vaultId
                //  )

                is Wallet -> ChainTokensScreen(
                    vaultId = (uiModel.route as Wallet).vaultId,
                    chainId = (uiModel.route as Wallet).chainId
                )

                null -> Unit
            }
        }
    }
}

@Preview
@Composable
private fun ChainDashboardScreenPreview() {
    ChainDashboardScreen()
}