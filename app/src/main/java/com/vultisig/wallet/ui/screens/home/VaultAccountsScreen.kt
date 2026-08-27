package com.vultisig.wallet.ui.screens.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CryptoConnectionType
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
import com.vultisig.wallet.ui.models.VaultAccountsUiModel
import com.vultisig.wallet.ui.models.VaultAccountsViewModel
import com.vultisig.wallet.ui.screens.passcode.OnceUnlocked
import com.vultisig.wallet.ui.screens.settings.bottomsheets.notifications.NotificationsIntroBottomSheet
import com.vultisig.wallet.ui.screens.settings.bottomsheets.notifications.VaultNotificationOptInBottomSheet
import com.vultisig.wallet.ui.screens.v2.home.components.AccountList
import com.vultisig.wallet.ui.screens.v2.home.components.Banners
import com.vultisig.wallet.ui.screens.v2.home.components.CameraButton
import com.vultisig.wallet.ui.screens.v2.home.components.ChooseVaultButton
import com.vultisig.wallet.ui.screens.v2.home.components.CryptoConnectionSelect
import com.vultisig.wallet.ui.screens.v2.home.components.DefiExpandedTopbarContent
import com.vultisig.wallet.ui.screens.v2.home.components.HomePageTabMenuAndSearchBar
import com.vultisig.wallet.ui.screens.v2.home.components.NoChainFound
import com.vultisig.wallet.ui.screens.v2.home.components.NotEnabledContainer
import com.vultisig.wallet.ui.screens.v2.home.components.TopRow
import com.vultisig.wallet.ui.screens.v2.home.components.WalletExpandedTopbarContent
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.HomeBannerType
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultAccountsScreen(viewModel: VaultAccountsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onNotificationPermissionResult(granted)
        }

    // Positions and the facts the promo banners are chosen from are both changed one screen
    // deeper, so both are re-read on the way back rather than only when the tab is switched.
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        viewModel.requestNotificationPermission.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onNotificationPermissionResult(granted = true)
            }
        }
    }

    // These three raise themselves off background work rather than off a tap, so each can open its
    // window after the passcode lock has opened its own, and land above it.
    if (state.showMonthlyBackupReminder) {
        OnceUnlocked {
            MonthlyBackupReminder(
                onDismiss = viewModel::dismissBackupReminder,
                onBackup = viewModel::backupVault,
                onDoNotRemind = viewModel::doNotRemindBackup,
            )
        }
    }

    if (state.showNotificationIntroSheet) {
        OnceUnlocked {
            NotificationsIntroBottomSheet(
                onEnable = viewModel::onNotificationEnable,
                onNotNow = viewModel::onNotificationNotNow,
                onDismissRequest = viewModel::onNotificationNotNow,
            )
        }
    }

    if (state.showNotificationVaultSheet) {
        OnceUnlocked {
            VaultNotificationOptInBottomSheet(
                vaults = state.notificationIntroVaults,
                onEnableVault = viewModel::onNotificationVaultToggle,
                onDismissRequest = viewModel::onNotificationVaultSheetDismiss,
                onEnableAll = viewModel::onEnableAll,
                onDone = viewModel::onNotificationVaultSheetDone,
            )
        }
    }

    VaultAccountsScreen(
        state = state,
        onRefresh = viewModel::refreshData,
        onSend = viewModel::send,
        onSwap = viewModel::swap,
        onBuy = viewModel::buy,
        onReceive = viewModel::receive,
        openCamera = viewModel::openCamera,
        onAccountClick = viewModel::openAccount,
        onToggleBalanceVisibility = viewModel::toggleBalanceVisibility,
        onOpenHistoryClick = viewModel::openHistory,
        onOpenSettingsClick = viewModel::openSettings,
        onToggleVaultListClick = viewModel::openVaultList,
        onChooseChains = viewModel::openAddChainAccount,
        onBannerClick = viewModel::onBannerClick,
        onBannerDismiss = viewModel::onBannerDismiss,
        onCryptoConnectionTypeClick = viewModel::setCryptoConnectionType,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultAccountsScreen(
    state: VaultAccountsUiModel,
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onBuy: () -> Unit = {},
    onReceive: () -> Unit = {},
    onRefresh: () -> Unit = {},
    openCamera: () -> Unit = {},
    onToggleVaultListClick: () -> Unit = {},
    onAccountClick: (AccountUiModel) -> Unit = {},
    onToggleBalanceVisibility: () -> Unit = {},
    onOpenHistoryClick: () -> Unit = {},
    onOpenSettingsClick: () -> Unit = {},
    onChooseChains: () -> Unit = {},
    onBannerClick: (HomeBannerType) -> Unit = {},
    onBannerDismiss: (HomeBannerType) -> Unit = {},
    onCryptoConnectionTypeClick: (CryptoConnectionType) -> Unit = {},
) {

    val snackbarState = rememberVsSnackbarState()
    var isTabMenu by remember { mutableStateOf(true) }
    val isBottomBarVisible = remember { derivedStateOf { isTabMenu } }

    val isShowingSearchResult = remember { derivedStateOf { isTabMenu.not() } }

    val isWallet = state.cryptoConnectionType == CryptoConnectionType.Wallet

    val context = LocalContext.current

    ScaffoldWithExpandableTopBar(
        snackbarState = snackbarState,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        topBarCollapsedContent = {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(Theme.v2.colors.backgrounds.primary)
                            .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ChooseVaultButton(
                        vaultName = state.vaultName,
                        isFastVault = state.isFastVault,
                        onClick = onToggleVaultListClick,
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.home_portfolio_balance),
                            color = Theme.v2.colors.text.tertiary,
                            style = Theme.brockmann.body.s.medium,
                        )
                        UiSpacer(size = 2.dp)

                        LoadableValue(
                            value = state.totalFiatValue,
                            isVisible = state.isBalanceValueVisible,
                            style = Theme.satoshi.price.bodyS,
                            color = Theme.v2.colors.text.primary,
                        )
                    }
                }

                UiSpacer(size = 16.dp)

                UiHorizontalDivider(color = Theme.v2.colors.border.light)

                UiSpacer(size = 16.dp)
            }
        },
        topBarExpandedContent = {
            ExpandedTopbarContainer {
                TopRow(
                    onOpenHistoryClick = onOpenHistoryClick,
                    onOpenSettingsClick = onOpenSettingsClick,
                    onToggleVaultListClick = onToggleVaultListClick,
                    vaultName = state.vaultName,
                    isFastVault = state.isFastVault,
                )
                AnimatedContent(targetState = isWallet, transitionSpec = slideAndFadeSpec()) {
                    isWalletTabSelected ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        UiSpacer(size = 24.dp)
                        if (isWalletTabSelected) {
                            WalletExpandedTopbarContent(
                                state = state,
                                onToggleBalanceVisibility = onToggleBalanceVisibility,
                                onSend = onSend,
                                onSwap = onSwap,
                                onBuy = onBuy,
                                onReceive = onReceive,
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
        bottomBarContent =
            if (isBottomBarVisible.value) {
                {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            CryptoConnectionSelect(
                                onTypeClick = onCryptoConnectionTypeClick,
                                activeType = state.cryptoConnectionType,
                            )
                            CameraButton(onClick = openCamera)
                        }
                    }
                }
            } else {
                {}
            },
        content = { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                LazyColumn(
                    modifier =
                        Modifier.background(Theme.v2.colors.backgrounds.primary).fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 30.dp),
                ) {
                    item {
                        AnimatedVisibility(
                            visible =
                                state.banners.isNotEmpty() &&
                                    state.cryptoConnectionType == CryptoConnectionType.Wallet,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Banners(
                                modifier =
                                    Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                                banners = state.banners,
                                onBannerClick = onBannerClick,
                                onBannerDismiss = onBannerDismiss,
                                context = context,
                            )
                        }
                    }

                    item {
                        HomePageTabMenuAndSearchBar(
                            modifier =
                                Modifier.animateItem()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                            onEditClick = onChooseChains,
                            isTabMenu = isTabMenu,
                            onSearchClick = { isTabMenu = false },
                            onCancelSearchClick = { isTabMenu = true },
                            searchTextFieldState = state.searchTextFieldState,
                        )
                    }

                    item {
                        TopShineContainer(modifier = Modifier.padding(horizontal = 16.dp)) {
                            when {
                                isShowingSearchResult.value && state.noChainFound ->
                                    NoChainFound(onChooseChains = onChooseChains)

                                // Only once the accounts flow has emitted does an empty list mean
                                // the user disabled every chain; before that it just means the
                                // addresses are still being derived, which on a cold start with
                                // many chains takes seconds.
                                state.areAccountsLoaded && state.getAccounts.isEmpty() ->
                                    NotEnabledContainer(
                                        title =
                                            stringResource(R.string.home_page_no_chains_enabled),
                                        content =
                                            stringResource(
                                                R.string.home_page_no_chain_enabled_desc
                                            ),
                                        // Rendered inside the chain-list container above, so it
                                        // steps down rather than matching it.
                                        radius = Theme.v2.radius.md,
                                    )

                                else ->
                                    AccountList(
                                        onAccountClick = onAccountClick,
                                        snackbarState = snackbarState,
                                        isBalanceVisible = state.isBalanceValueVisible,
                                        accounts = state.getAccounts,
                                        showAddress = isWallet,
                                    )
                            }
                        }
                    }
                }
                if (isTabMenu) {
                    BottomFadeEffect(modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        },
    )
}

@Preview
@Composable
private fun PreviewVaultAccountsScreen() {
    VaultAccountsScreen(
        state =
            VaultAccountsUiModel(
                vaultName = "Main Vault",
                totalFiatValue = "$12,345.67",
                isBalanceValueVisible = true,
                accounts =
                    listOf(
                        AccountUiModel(
                            model =
                                Address(
                                    chain = Chain.Ethereum,
                                    address = "0xAbCd1234",
                                    accounts = emptyList(),
                                ),
                            chainName = "Ethereum",
                            logo = R.drawable.ethereum,
                            address = "0xAbCd1234",
                            nativeTokenAmount = "0.5 ETH",
                            fiatAmount = "$1,234.56",
                            assetsSize = 3,
                            nativeTokenTicker = "ETH",
                        ),
                        AccountUiModel(
                            model =
                                Address(
                                    chain = Chain.Bitcoin,
                                    address = "bc1qxyz",
                                    accounts = emptyList(),
                                ),
                            chainName = "Bitcoin",
                            logo = R.drawable.bitcoin,
                            address = "bc1qxyz",
                            nativeTokenAmount = "0.1 BTC",
                            fiatAmount = "$6,500.00",
                            assetsSize = 1,
                            nativeTokenTicker = "BTC",
                        ),
                        AccountUiModel(
                            model =
                                Address(
                                    chain = Chain.ThorChain,
                                    address = "thor1abc",
                                    accounts = emptyList(),
                                ),
                            chainName = "THORChain",
                            logo = R.drawable.rune,
                            address = "thor1abc",
                            nativeTokenAmount = "100 RUNE",
                            fiatAmount = "$400.00",
                            assetsSize = 1,
                            nativeTokenTicker = "RUNE",
                        ),
                    ),
            )
    )
}

internal object VaultAccountsScreenTags {
    const val MIGRATE = "VaultAccountsScreen.migrate"
}
