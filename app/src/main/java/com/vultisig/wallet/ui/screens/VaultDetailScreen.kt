package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.BoxWithSwipeRefresh
import com.vultisig.wallet.ui.components.ChainAccountItem
import com.vultisig.wallet.ui.components.UiPlusButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.ChainAccountUiModel
import com.vultisig.wallet.ui.models.VaultDetailViewModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultDetailScreen(
    vaultId: String,
    navHostController: NavHostController,
    viewModel: VaultDetailViewModel = hiltViewModel(),
) {
    val textColor = MaterialTheme.colorScheme.onBackground

    val state = viewModel.uiState.collectAsState().value

    LaunchedEffect(key1 = viewModel) {
        viewModel.loadData(vaultId)
    }
    BoxWithSwipeRefresh(onSwipe = viewModel::refreshData, isRefreshing = viewModel.isRefreshing) {
        Scaffold(topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.vaultName,
                        style = Theme.montserrat.titleLarge,
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
                    IconButton(onClick = {
                        val vault = viewModel.currentVault.value
                        navHostController.navigate(Destination.VaultSettings(vault.name).route)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_edit_square_24),
                            contentDescription = "search",
                            tint = Color.White
                        )
                    }
                }
            )
        }, bottomBar = {},
            floatingActionButton = {
                Button(onClick = {
                    navHostController.navigate(
                        Screen.JoinKeysign.createRoute(vaultId)
                    )
                }) {
                    Image(
                        painter = painterResource(id = R.drawable.camera),
                        contentDescription = "join keysign"
                    )
                }

            }) {
            LazyColumn(
                modifier = Modifier.padding(it),
                contentPadding = PaddingValues(
                    all = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        text = state.totalFiatValue ?: "",
                        style = Theme.menlo.subtitle1,
                        color = Theme.colors.neutral100,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillParentMaxWidth(),
                    )
                }
                items(state.accounts) { account: ChainAccountUiModel ->
                    ChainAccountItem(
                        account = account
                    ) {
                        val route = Screen.ChainCoin.createRoute(
                            chainRaw = account.chainName,
                            vaultId = viewModel.vault?.name ?: "",
                        )
                        navHostController.navigate(route)
                    }
                }
                item {
                    UiSpacer(
                        size = 16.dp,
                    )
                    UiPlusButton(
                        title = stringResource(R.string.vault_choose_chains),
                        onClick = {
                            navHostController.navigate(
                                Screen.VaultDetail.AddChainAccount.createRoute(vaultId)
                            )
                        },
                    )
                }
            }
        }
    }
}
