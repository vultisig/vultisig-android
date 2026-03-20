package com.vultisig.wallet.app.activity.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
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

        val foregroundNotification by
            mainViewModel.foregroundNotification.collectAsStateWithLifecycle()

        key(foregroundNotification?.qrCodeData) {
            AnimatedVisibility(
                visible = foregroundNotification != null,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            ) {
                ForegroundNotificationBanner(
                    qrCodeData = foregroundNotification?.qrCodeData ?: "",
                    vaultName = foregroundNotification?.vaultName ?: "",
                    transactionSummary = foregroundNotification?.transactionSummary ?: "",
                    onTap = mainViewModel::onForegroundBannerTapped,
                    onDismiss = mainViewModel::onForegroundBannerDismissed,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        BiometryAuthScreen()

        VsSnackBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            snackbarState = mainViewModel.snakeBarHostState,
        )
    }
}
