package com.vultisig.wallet.ui.screens.v2.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.animation.slideAndFadeSpec
import com.vultisig.wallet.ui.components.v2.containers.ExpandedTopbarContainer
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.components.v2.scaffold.ScaffoldWithExpandableTopBar
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.components.v2.visuals.BottomFadeEffect
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.models.VaultAccountsUiModel
import com.vultisig.wallet.ui.screens.v2.home.components.AccountList
import com.vultisig.wallet.ui.screens.v2.home.components.Banners
import com.vultisig.wallet.ui.screens.v2.home.components.CameraButton
import com.vultisig.wallet.ui.screens.v2.home.components.ChooseVaultButton
import com.vultisig.wallet.ui.screens.v2.home.components.HomePageTabMenuAndSearchBar
import com.vultisig.wallet.ui.screens.v2.home.components.NoChainFound
import com.vultisig.wallet.ui.screens.v2.home.components.TopRow
import com.vultisig.wallet.ui.screens.v2.home.components.CryptoConnectionSelect
import com.vultisig.wallet.ui.screens.v2.home.components.DefiExpandedTopbarContent
import com.vultisig.wallet.ui.screens.v2.home.components.NoChainEnabled
import com.vultisig.wallet.ui.screens.v2.home.components.WalletExpandedTopbarContent
import com.vultisig.wallet.ui.theme.Theme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomePage(
    state: VaultAccountsUiModel,
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onRefresh: () -> Unit = {},
    openCamera: () -> Unit = {},
    onToggleVaultListClick: () -> Unit = {},
    onAccountClick: (AccountUiModel) -> Unit = {},
    onToggleBalanceVisibility: () -> Unit = {},
    onMigrateClick: () -> Unit = {},
    onOpenSettingsClick: () -> Unit = {},
    onChooseChains: () -> Unit = {},
    onDismissBanner: () -> Unit = {},
    onCryptoConnectionTypeClick: (CryptoConnectionType) -> Unit = {},
) {

    val snackbarState = rememberVsSnackbarState()
    var isTabMenu by remember {
        mutableStateOf(true)
    }
    val isBottomBarVisible = remember {
        derivedStateOf { isTabMenu }
    }

    val isShowingSearchResult = remember {
        derivedStateOf { isTabMenu.not() }
    }

    val isWallet = state.cryptoConnectionType == CryptoConnectionType.Wallet

    val context = LocalContext.current

    ScaffoldWithExpandableTopBar(
        snackbarState = snackbarState,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        topBarCollapsedContent = {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Theme.colors.backgrounds.primary)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChooseVaultButton(
                        vaultName = state.vaultName,
                        isFastVault = state.isFastVault,
                        onClick = onToggleVaultListClick,
                    )

                    Column(
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = stringResource(R.string.home_portfolio_balance),
                            color = Theme.colors.text.extraLight,
                            style = Theme.brockmann.body.s.medium
                        )
                        UiSpacer(
                            size = 2.dp
                        )

                        LoadableValue(
                            value = state.totalFiatValue,
                            isVisible = state.isBalanceValueVisible,
                            style = Theme.satoshi.price.bodyS,
                            color = Theme.colors.text.primary,
                        )
                    }
                }

                UiSpacer(
                    size = 16.dp
                )

                UiHorizontalDivider(
                    color = Theme.colors.borders.light,
                )
            }
        },
        topBarExpandedContent = {
            ExpandedTopbarContainer {
                TopRow(
                    onOpenSettingsClick = onOpenSettingsClick,
                    onToggleVaultListClick = onToggleVaultListClick,
                    vaultName = state.vaultName,
                    isFastVault = state.isFastVault,
                )
                AnimatedContent(
                    targetState = isWallet,
                    transitionSpec = slideAndFadeSpec(),
                ) { isWalletTabSelected ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        UiSpacer(
                            size = 24.dp
                        )
                        if (isWalletTabSelected) {
                            WalletExpandedTopbarContent(
                                state = state,
                                onToggleBalanceVisibility = onToggleBalanceVisibility,
                                onSend = onSend,
                                onSwap = onSwap,
                            )
                        } else {
                            DefiExpandedTopbarContent(
                                state = state,
                                onToggleBalanceVisibility = onToggleBalanceVisibility,
                            )
                        }
                    }
                }

            }
        },
        bottomBarContent = if (isBottomBarVisible.value) {
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
                            onTypeClick = onCryptoConnectionTypeClick,
                            activeType = state.cryptoConnectionType
                        )
                        CameraButton(
                            onClick = openCamera
                        )
                    }
                }
            }
        } else {
            {}
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .background(Theme.colors.backgrounds.primary)
                        .fillMaxSize()
                ) {
                    item {
                        AnimatedVisibility(
                            visible = state.isBannerVisible,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Banners(
                                hasMigration = state.showMigration,
                                onMigrateClick = onMigrateClick,
                                context = context,
                                onDismissBanner = onDismissBanner
                            )
                        }
                    }

                    item {
                        HomePageTabMenuAndSearchBar(
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp)
                            ,
                            onEditClick = onChooseChains,
                            isTabMenu = isTabMenu,
                            onSearchClick = {
                                isTabMenu = false
                            },
                            onCancelSearchClick = {
                                isTabMenu = true
                            },
                            searchTextFieldState = state.searchTextFieldState,
                        )
                    }

                    item {
                        TopShineContainer(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                        ) {
                            if (isShowingSearchResult.value && state.noChainFound) {
                                NoChainFound(
                                    onChooseChains = onChooseChains
                                )
                            } else {
                                if(state.accounts.isEmpty()){
                                    NoChainEnabled()
                                }
                                 else {
                                    AccountList(
                                        onAccountClick = onAccountClick,
                                        snackbarState = snackbarState,
                                        isBalanceVisible = state.isBalanceValueVisible,
                                        accounts = state.accounts,
                                    )
                                }
                            }
                        }
                    }
                }
                if (isTabMenu) {
                    BottomFadeEffect(
                        modifier = Modifier
                            .align(Alignment.BottomCenter),
                    )
                }
            }
        }
    )
}


@Preview
@Composable
private fun PreviewHomePage() {
    HomePage(
        state = VaultAccountsUiModel()
    )
}

