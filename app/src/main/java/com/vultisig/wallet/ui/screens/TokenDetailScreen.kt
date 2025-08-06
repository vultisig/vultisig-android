package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.BoxWithSwipeRefresh
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VaultActionButton
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.TokenDetailUiModel
import com.vultisig.wallet.ui.models.TokenDetailViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TokenDetailScreen(
    navController: NavHostController,
    viewModel: TokenDetailViewModel = hiltViewModel<TokenDetailViewModel>(),
) {
    val uiModel by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    TokenDetailScreen(
        navController = navController,
        uiModel = uiModel,
        onRefresh = viewModel::refresh,
        onSend = viewModel::send,
        onSwap = viewModel::swap,
        onDeposit = viewModel::deposit,
    )
}

@Composable
private fun TokenDetailScreen(
    navController: NavHostController,
    uiModel: TokenDetailUiModel,
    onRefresh: () -> Unit = {},
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onDeposit: () -> Unit = {},
) {
    val appColor = Theme.colors
    val snackbarHostState = remember {
        SnackbarHostState()
    }

    BoxWithSwipeRefresh(
        onSwipe = onRefresh,
        isRefreshing = uiModel.isRefreshing,
        modifier = Modifier.fillMaxSize(),
    ) {
        Scaffold(
            contentColor = Theme.colors.oxfordBlue800,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                TopBar(
                    navController = navController,
                    centerText = uiModel.token.name,
                    startIcon = R.drawable.ic_caret_left,
                )
            },
        ) {
            Box(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
            ) {
                Column(
                    modifier = Modifier
                        .padding(all = 16.dp)
                ) {
                    UiSpacer(size = 8.dp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        VaultActionButton(
                            text = stringResource(R.string.chain_account_view_send),
                            color = appColor.turquoise600Main,
                            modifier = Modifier.weight(1f),
                            onClick = onSend,
                        )
                        if (uiModel.canSwap) {
                            VaultActionButton(
                                text = stringResource(R.string.chain_account_view_swap),
                                color = appColor.persianBlue200,
                                modifier = Modifier.weight(1f),
                                onClick = onSwap,
                            )
                        }
                        if (uiModel.canDeposit) {
                            VaultActionButton(
                                stringResource(R.string.chain_account_view_deposit),
                                appColor.mediumPurple,
                                modifier = Modifier.weight(1f),
                                onClick = onDeposit,
                            )
                        }
                    }

                    UiSpacer(size = 22.dp)

                    FormCard {
                        Column(
                            modifier = Modifier
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 12.dp,
                                )
                        ) {

                            val token = uiModel.token

                            CoinItem(
                                title = token.name,
                                balance = token.balance,
                                fiatBalance = token.fiatBalance,
                                tokenLogo = token.tokenLogo,
                                chainLogo = token.chainLogo,
                                isBalanceVisible = uiModel.isBalanceVisible,
                                mergedBalance = uiModel.token.mergeBalance,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "chain coin screen")
@Composable
private fun TokenDetailScreenPreview() {
    TokenDetailScreen(
        navController = rememberNavController(),
        uiModel = TokenDetailUiModel(
            token = ChainTokenUiModel(
                name = "USDT",
                balance = "0.000",
                fiatBalance = "$0.000000",
                tokenLogo = R.drawable.usdt,
                chainLogo = R.drawable.ethereum
            ),
            canSwap = true,
            canDeposit = true
        )
    )
}