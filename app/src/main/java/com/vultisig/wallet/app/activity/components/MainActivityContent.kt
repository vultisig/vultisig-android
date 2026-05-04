package com.vultisig.wallet.app.activity.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.ForegroundNotificationState
import com.vultisig.wallet.app.activity.MainViewModel
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.BiometryAuthScreen
import com.vultisig.wallet.ui.components.banners.ForegroundNotificationBanner
import com.vultisig.wallet.ui.components.banners.OfflineBanner
import com.vultisig.wallet.ui.components.v2.snackbar.VsSnackBar
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.models.VaultAccountsUiModel
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.SetupNavGraph
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.screens.home.VaultAccountsScreen
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun MainActivityContent(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    startDestination: Any,
    onNavigationReady: () -> Unit,
) {
    val foregroundNotification by mainViewModel.foregroundNotification.collectAsStateWithLifecycle()
    var lastNotification by remember { mutableStateOf<ForegroundNotificationState?>(null) }
    if (foregroundNotification != null) lastNotification = foregroundNotification

    MainActivityContent(
        foregroundNotification = foregroundNotification,
        lastNotification = lastNotification,
        isOffline = mainViewModel.isOffline.collectAsStateWithLifecycle().value,
        onBannerTap = mainViewModel::onForegroundBannerTapped,
        navContent = {
            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.first()

                // Start collectors before signalling readiness so they are subscribed
                // (Main.immediate dispatches run immediately until first suspension).
                launch {
                    mainViewModel.destination.collect { navController.route(it.dst.route, it.opts) }
                }

                launch { mainViewModel.route.collect { navController.route(it) } }

                launch {
                    navController.currentBackStackEntryFlow.collect { entry ->
                        val destination = entry.destination
                        if (
                            destination.hasRoute<Route.Keysign.Join>() ||
                                destination.hasRoute<Route.Keygen.Join>()
                        ) {
                            mainViewModel.clearForegroundNotification()
                        }
                    }
                }

                mainViewModel.onNavigationReady()
                onNavigationReady()
            }

            SetupNavGraph(navController = navController, startDestination = startDestination)
        },
        overlayContent = {
            BiometryAuthScreen()

            VsSnackBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                snackbarState = mainViewModel.snakeBarHostState,
            )
        },
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun MainActivityContent(
    foregroundNotification: ForegroundNotificationState?,
    lastNotification: ForegroundNotificationState?,
    isOffline: Boolean,
    onBannerTap: () -> Unit,
    navContent: @Composable () -> Unit,
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density)

    Box(
        modifier =
            Modifier.semantics {
                    // Expose Compose test tags through the accessibility/resource-id channel
                    // so external automation tooling can target them reliably.
                    testTagsAsResourceId = true
                }
                .background(color = Theme.v2.colors.backgrounds.primary)
                .safeDrawingPadding()
    ) {
        // navContent drawn first (behind) so the banner's transparent corners
        // reveal the screen's gradient instead of the solid outer Box background.
        Column(modifier = Modifier.fillMaxSize()) {
            OfflineBanner(isOffline)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { navContent() }
        }

        // Banner drawn as overlay on top of navContent.
        key(lastNotification?.qrCodeData) {
            AnimatedVisibility(
                visible = foregroundNotification != null,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier =
                    Modifier.fillMaxWidth().layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height - statusBarHeightPx) {
                            placeable.placeRelative(0, -statusBarHeightPx)
                        }
                    },
            ) {
                lastNotification?.let { notification ->
                    ForegroundNotificationBanner(
                        qrCodeData = notification.qrCodeData,
                        vaultName = notification.vaultName,
                        transactionSummary = notification.transactionSummary,
                        onTap = onBannerTap,
                    )
                }
            }
        }

        overlayContent()
    }
}

@Preview
@Composable
private fun MainActivityContentPreview() {
    val notification =
        ForegroundNotificationState(
            qrCodeData = "preview",
            vaultName = "Main Vault",
            transactionSummary = "Swap 10 ETH → USDC",
        )
    MainActivityContent(
        foregroundNotification = notification,
        lastNotification = notification,
        isOffline = false,
        onBannerTap = {},
        navContent = {
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
                            ),
                    )
            )
        },
    )
}
