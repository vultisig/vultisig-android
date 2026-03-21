package com.vultisig.wallet.app.activity.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vultisig.wallet.app.activity.ForegroundNotificationState
import com.vultisig.wallet.app.activity.MainViewModel
import com.vultisig.wallet.ui.components.BiometryAuthScreen
import com.vultisig.wallet.ui.components.banners.ForegroundNotificationBanner
import com.vultisig.wallet.ui.components.banners.OfflineBanner
import com.vultisig.wallet.ui.components.v2.snackbar.VsSnackBar
import com.vultisig.wallet.ui.navigation.SetupNavGraph
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.flow.filterNotNull
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
        Column(modifier = Modifier.fillMaxSize()) {
            OfflineBanner(mainViewModel.isOffline.value)
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
                            onTap = mainViewModel::onForegroundBannerTapped,
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SetupNavGraph(navController = navController, startDestination = startDestination)
            }
        }

        LaunchedEffect(navController) {
            snapshotFlow { navController.currentBackStackEntry }.filterNotNull().first()

            // Start collectors before signalling readiness so they are subscribed
            // (Main.immediate dispatches run immediately until first suspension).
            launch {
                mainViewModel.destination.collect { navController.route(it.dst.route, it.opts) }
            }

            launch { mainViewModel.route.collect { navController.route(it) } }

            mainViewModel.onNavigationReady()
            onNavigationReady()
        }

        BiometryAuthScreen()

        VsSnackBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            snackbarState = mainViewModel.snakeBarHostState,
        )
    }
}
