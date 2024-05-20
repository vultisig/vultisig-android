package com.vultisig.wallet.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.BoxWithSwipeRefresh
import com.vultisig.wallet.ui.components.ChainAccountItem
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiPlusButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.models.VaultAccountsUiModel
import com.vultisig.wallet.ui.models.VaultAccountsViewModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
internal fun VaultAccountsScreen(
    vaultId: String,
    navHostController: NavHostController,
    viewModel: VaultAccountsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsState().value

    LaunchedEffect(key1 = viewModel) {
        viewModel.loadData(vaultId)
    }

    VaultAccountsScreen(
        navHostController = navHostController,
        state = state,
        onRefresh = viewModel::refreshData,
        onOpenVaultSettings = {
            val vault = viewModel.currentVault.value
            navHostController.navigate(Destination.VaultSettings(vault.name).route)
        },
        onJoinKeysign = {
            navHostController.navigate(
                Screen.JoinKeysign.createRoute(vaultId)
            )
        },
        onAccountClick = {
            val route = Screen.ChainCoin.createRoute(
                chainRaw = it.chainName,
                vaultId = viewModel.vault?.name ?: "",
            )
            navHostController.navigate(route)
        },
        onChooseChains = {
            navHostController.navigate(
                Screen.VaultDetail.AddChainAccount.createRoute(vaultId)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultAccountsScreen(
    navHostController: NavHostController,
    state: VaultAccountsUiModel,
    onRefresh: () -> Unit,
    onOpenVaultSettings: () -> Unit,
    onJoinKeysign: () -> Unit,
    onAccountClick: (AccountUiModel) -> Unit,
    onChooseChains: () -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onBackground

    BoxWithSwipeRefresh(
        onSwipe = onRefresh,
        isRefreshing = state.isRefreshing
    ) {
        Scaffold(topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.vaultName,
                        style = Theme.montserrat.subtitle1,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier
                            .padding(
                                start = MaterialTheme.dimens.marginMedium,
                                end = MaterialTheme.dimens.marginMedium,
                            )
                            .wrapContentHeight(align = Alignment.CenterVertically)
                    )
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Theme.colors.oxfordBlue800,
                    titleContentColor = textColor
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        navHostController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "settings", tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenVaultSettings) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_edit_square_24),
                            contentDescription = "search",
                            tint = Color.White
                        )
                    }
                }
            )
        },
            floatingActionButtonPosition = FabPosition.Center,
            floatingActionButton = {
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
            }) {
            LazyColumn(
                modifier = Modifier.padding(it),
                contentPadding = PaddingValues(
                    all = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    AnimatedContent(
                        targetState = state.totalFiatValue,
                        label = "ChainAccount FiatAmount",
                        modifier = Modifier.fillParentMaxWidth(),
                    ) { totalFiatValue ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillParentMaxWidth()
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
                }
                items(state.accounts) { account: AccountUiModel ->
                    ChainAccountItem(
                        account = account,
                        onClick = {
                            onAccountClick(account)
                        },
                    )
                }
                item {
                    UiSpacer(
                        size = 16.dp,
                    )
                    UiPlusButton(
                        title = stringResource(R.string.vault_choose_chains),
                        onClick = onChooseChains,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun VaultAccountsScreenPreview() {
    VaultAccountsScreen(
        navHostController = rememberNavController(),
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
        onOpenVaultSettings = {},
        onJoinKeysign = {},
        onAccountClick = {},
        onChooseChains = {},
    )
}
