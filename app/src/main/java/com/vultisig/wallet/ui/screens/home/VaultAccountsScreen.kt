package com.vultisig.wallet.ui.screens.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ExpandedTopbarContainer
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.components.v2.scaffold.ScaffoldWithExpandableTopBar
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.models.vault.AccountUiModel
import com.vultisig.wallet.ui.models.vault.VaultAccountsUiModel
import com.vultisig.wallet.ui.models.vault.VaultAccountsViewModel
import com.vultisig.wallet.ui.screens.passcode.OnceUnlocked
import com.vultisig.wallet.ui.screens.settings.bottomsheets.notifications.NotificationsIntroBottomSheet
import com.vultisig.wallet.ui.screens.settings.bottomsheets.notifications.VaultNotificationOptInBottomSheet
import com.vultisig.wallet.ui.screens.v2.home.components.AccountList
import com.vultisig.wallet.ui.screens.v2.home.components.BOTH_CRYPTO_CONNECTION_TYPES
import com.vultisig.wallet.ui.screens.v2.home.components.Banners
import com.vultisig.wallet.ui.screens.v2.home.components.BottomNavigatorOverlay
import com.vultisig.wallet.ui.screens.v2.home.components.ChooseVaultButton
import com.vultisig.wallet.ui.screens.v2.home.components.CryptoConnectionPager
import com.vultisig.wallet.ui.screens.v2.home.components.HomeExpandedTopbarContent
import com.vultisig.wallet.ui.screens.v2.home.components.HomePageTabMenuAndSearchBar
import com.vultisig.wallet.ui.screens.v2.home.components.HomeSwipeHint
import com.vultisig.wallet.ui.screens.v2.home.components.LocalBottomNavigatorPadding
import com.vultisig.wallet.ui.screens.v2.home.components.NoChainFound
import com.vultisig.wallet.ui.screens.v2.home.components.NotEnabledContainer
import com.vultisig.wallet.ui.screens.v2.home.components.TopRow
import com.vultisig.wallet.ui.screens.v2.home.components.rememberCryptoConnectionPagerState
import com.vultisig.wallet.ui.screens.v2.home.components.rememberCryptoConnectionTabClick
import com.vultisig.wallet.ui.screens.v2.home.components.rememberPagerIndicatorPosition
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
        onCryptoConnectionTypeSettle = viewModel::setCryptoConnectionType,
        onDismissSwipeHint = viewModel::dismissSwipeHint,
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
    onCryptoConnectionTypeSettle: (CryptoConnectionType) -> Unit = {},
    onDismissSwipeHint: () -> Unit = {},
) {

    val snackbarState = rememberVsSnackbarState()
    var isTabMenu by remember { mutableStateOf(true) }

    val types = BOTH_CRYPTO_CONNECTION_TYPES
    val pagerState = rememberCryptoConnectionPagerState(types, state.cryptoConnectionType)
    val onTabClick = rememberCryptoConnectionTabClick(pagerState, types)
    val indicatorPosition = rememberPagerIndicatorPosition(pagerState)
    var swipeHintAnchorTop by remember { mutableFloatStateOf(Float.NaN) }

    Box(modifier = Modifier.fillMaxSize()) {
        BottomNavigatorOverlay(
            isNavigatorVisible = isTabMenu,
            activeType = state.cryptoConnectionType,
            onTypeClick = onTabClick,
            indicatorPosition = indicatorPosition,
            onCameraClick = openCamera,
        ) {
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
                                    value = state.headerFiatValue,
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
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            UiSpacer(size = 24.dp)
                            HomeExpandedTopbarContent(
                                balance = state.headerFiatValue,
                                isBalanceVisible = state.isBalanceValueVisible,
                                isSwapEnabled = state.isSwapEnabled,
                                onToggleBalanceVisibility = onToggleBalanceVisibility,
                                onSend = onSend,
                                onSwap = onSwap,
                                onBuy = onBuy,
                                onReceive = onReceive,
                            )
                        }
                    }
                },
                content = { innerPadding ->
                    CryptoConnectionPager(
                        state = pagerState,
                        types = types,
                        selectedType = state.cryptoConnectionType,
                        onSettle = onCryptoConnectionTypeSettle,
                        // A search is scoped to the tab it was opened on.
                        userScrollEnabled = isTabMenu,
                        // Both pages read the one view model, so holding the other one costs no
                        // loads, and it comes back at its own scroll position, carousel included.
                        keepsOtherPageComposed = true,
                        modifier =
                            Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
                    ) { type ->
                        HomeAccountsPage(
                            type = type,
                            state = state,
                            // Search opens on the selected tab only; the held page keeps its menu.
                            isTabMenu = isTabMenu || type != state.cryptoConnectionType,
                            snackbarState = snackbarState,
                            onSearchClick = { isTabMenu = false },
                            onCancelSearchClick = { isTabMenu = true },
                            onChooseChains = onChooseChains,
                            onAccountClick = onAccountClick,
                            onBannerClick = onBannerClick,
                            onBannerDismiss = onBannerDismiss,
                            onSectionHeaderPositioned = { swipeHintAnchorTop = it },
                        )
                    }
                },
            )
        }

        if (state.showSwipeHint && !swipeHintAnchorTop.isNaN()) {
            HomeSwipeHint(
                anchorTopInWindow = swipeHintAnchorTop,
                onDismiss = onDismissSwipeHint,
                onSwipe = { towardsNextTab ->
                    val current = types.indexOf(state.cryptoConnectionType)
                    val target =
                        (current + if (towardsNextTab) 1 else -1).coerceIn(0, types.lastIndex)
                    onTabClick(types[target])
                    onDismissSwipeHint()
                },
            )
        }
    }
}

/** One tab's page: the promo carousel (Wallet only), the section header and its chain list. */
@Composable
private fun HomeAccountsPage(
    type: CryptoConnectionType,
    state: VaultAccountsUiModel,
    isTabMenu: Boolean,
    snackbarState: VSSnackbarState,
    onSearchClick: () -> Unit,
    onCancelSearchClick: () -> Unit,
    onChooseChains: () -> Unit,
    onAccountClick: (AccountUiModel) -> Unit,
    onBannerClick: (HomeBannerType) -> Unit,
    onBannerDismiss: (HomeBannerType) -> Unit,
    onSectionHeaderPositioned: (topInWindow: Float) -> Unit,
) {
    val context = LocalContext.current
    val isWallet = type == CryptoConnectionType.Wallet
    val accounts = state.accountsOf(type)

    LazyColumn(
        modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalBottomNavigatorPadding.current),
    ) {
        if (isWallet) {
            item(key = BANNERS_KEY) {
                AnimatedVisibility(
                    visible = state.banners.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Banners(
                        modifier =
                            Modifier.padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp)
                                // A swipe that starts on the carousel stays on it, rather than
                                // paging the tabs once the carousel runs out of banners.
                                .nestedScroll(HorizontalScrollSink),
                        banners = state.banners,
                        onBannerClick = onBannerClick,
                        onBannerDismiss = onBannerDismiss,
                        context = context,
                    )
                }
            }
        }

        item(key = SECTION_HEADER_KEY) {
            HomePageTabMenuAndSearchBar(
                modifier =
                    Modifier.animateItem()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .then(
                            if (isWallet)
                                Modifier.onGloballyPositioned {
                                    onSectionHeaderPositioned(it.positionInWindow().y)
                                }
                            else Modifier
                        ),
                title =
                    stringResource(
                        if (isWallet) R.string.search_bar_portfolio else R.string.home_positions
                    ),
                onEditClick = onChooseChains,
                isTabMenu = isTabMenu,
                onSearchClick = onSearchClick,
                onCancelSearchClick = onCancelSearchClick,
                searchTextFieldState = state.searchTextFieldState,
            )
        }

        item(key = ACCOUNTS_KEY) {
            TopShineContainer(modifier = Modifier.padding(horizontal = 16.dp)) {
                when {
                    !isTabMenu && state.noChainFound(type) ->
                        NoChainFound(onChooseChains = onChooseChains)

                    // Before the accounts flow emits, empty means still deriving, not all disabled.
                    state.areAccountsLoaded(type) && accounts.isEmpty() ->
                        NotEnabledContainer(
                            title = stringResource(R.string.home_page_no_chains_enabled),
                            content = stringResource(R.string.home_page_no_chain_enabled_desc),
                            // Rendered inside the chain-list container above, so it steps down
                            // rather than matching it.
                            radius = Theme.v2.radius.md,
                        )

                    else ->
                        AccountList(
                            onAccountClick = onAccountClick,
                            snackbarState = snackbarState,
                            isBalanceVisible = state.isBalanceValueVisible,
                            accounts = accounts,
                            showAddress = isWallet,
                        )
                }
            }
        }
    }
}

/** Takes whatever horizontal scroll a child leaves over, so it never reaches the tab pager. */
private val HorizontalScrollSink =
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset = Offset(available.x, 0f)

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            Velocity(available.x, 0f)
    }

private const val BANNERS_KEY = "banners"
private const val SECTION_HEADER_KEY = "section_header"
private const val ACCOUNTS_KEY = "accounts"

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
