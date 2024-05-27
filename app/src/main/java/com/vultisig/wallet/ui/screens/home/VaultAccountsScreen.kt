package com.vultisig.wallet.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.BoxWithSwipeRefresh
import com.vultisig.wallet.ui.components.ChainAccountItem
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiPlusButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.reorderable.VerticalReorderList
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.models.VaultAccountsUiModel
import com.vultisig.wallet.ui.models.VaultAccountsViewModel
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultAccountsScreen(
    vaultId: String,
    navHostController: NavHostController,
    viewModel: VaultAccountsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsState().value

    LaunchedEffect(vaultId) {
        viewModel.loadData(vaultId)
    }

    VaultAccountsScreen(
        state = state,
        onRefresh = viewModel::refreshData,
        onJoinKeysign = {
            navHostController.navigate(
                Screen.JoinKeysign.createRoute(vaultId)
            )
        },
        onAccountClick = {
            val route = Screen.ChainCoin.createRoute(
                chainRaw = it.chainName,
                vaultId = viewModel.vault?.id ?: "",
            )
            navHostController.navigate(route)
        },
        onChooseChains = {
            navHostController.navigate(
                Screen.AddChainAccount.createRoute(vaultId)
            )
        },
        onMove = viewModel::onMove,
    )
}

@Composable
private fun VaultAccountsScreen(
    state: VaultAccountsUiModel,
    onRefresh: () -> Unit,
    onJoinKeysign: () -> Unit,
    onAccountClick: (AccountUiModel) -> Unit,
    onChooseChains: () -> Unit,
    modifier: Modifier = Modifier,
    onMove: (Int, Int) -> Unit,
) {
    BoxWithSwipeRefresh(
        onSwipe = onRefresh,
        isRefreshing = state.isRefreshing,
        modifier = Modifier.fillMaxSize()
    ) {

        Box(
            modifier = modifier.fillMaxSize(),
        ) {
            VerticalReorderList(
                data = state.accounts,
                onMove = onMove,
                contentPadding = PaddingValues(all = 16.dp),
                beforeContents = listOf {
                    AnimatedContent(
                        targetState = state.totalFiatValue,
                        label = "ChainAccount FiatAmount",
                        modifier = Modifier.fillMaxWidth(),
                    ) { totalFiatValue ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (totalFiatValue != null) {
                                Text(
                                    text = totalFiatValue,
                                    style = Theme.menlo.subtitle1,
                                    color = Theme.colors.neutral100,
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                UiPlaceholderLoader(
                                    modifier = Modifier
                                        .width(48.dp),
                                )
                            }
                        }
                    }
                },
                afterContents = listOf {
                    UiSpacer(
                        size = 16.dp,
                    )
                    UiPlusButton(
                        title = stringResource(R.string.vault_choose_chains),
                        onClick = onChooseChains,
                    )
                    UiSpacer(
                        size = 64.dp,
                    )
                }
            ) { account ->
                ChainAccountItem(
                    account = account,
                    onClick = {
                        onAccountClick(account)
                    },
                )
            }

            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
            ) {
                UiIcon(
                    drawableResId = R.drawable.camera,
                    size = 40.dp,
                    contentDescription = "join keysign",
                    tint = Theme.colors.oxfordBlue600Main,
                    onClick = onJoinKeysign,
                    modifier = Modifier
                        .background(
                            color = Theme.colors.turquoise600Main,
                            shape = CircleShape,
                        )
                        .padding(all = 10.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun VaultAccountsScreenPreview() {
    VaultAccountsScreen(
        state = VaultAccountsUiModel(
            vaultName = "Vault Name",
            totalFiatValue = "$1000",
            accounts = listOf(
                AccountUiModel(
                    chainName = "Ethereum",
                    logo = R.drawable.ethereum,
                    address = "0x1234567890",
                    nativeTokenAmount = "1.0",
                    fiatAmount = "$1000",
                    assetsSize = 4,
                ),
                AccountUiModel(
                    chainName = "Bitcoin",
                    logo = R.drawable.bitcoin,
                    address = "123456789abcdef",
                    nativeTokenAmount = "1.0",
                    fiatAmount = "$1000",
                ),
            ),
        ),
        onRefresh = {},
        onJoinKeysign = {},
        onAccountClick = {},
        onChooseChains = {},
        onMove = { _, _ -> }
    )
}
