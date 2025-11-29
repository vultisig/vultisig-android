@file:OptIn(ExperimentalAnimationApi::class)

package com.vultisig.wallet.app.activity

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.vultisig.wallet.ui.components.BiometryAuthScreen
import com.vultisig.wallet.ui.components.banners.OfflineBanner
import com.vultisig.wallet.ui.components.v2.snackbar.VsSnackBar
import com.vultisig.wallet.ui.navigation.SetupNavGraph
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.theme.v2.V2.colors
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
            .setKeepOnScreenCondition {
                mainViewModel.isLoading.value
            }

        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            mainViewModel.checkUpdates { appUpdateInfo ->
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    this@MainActivity,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE)
                        .setAllowAssetPackDeletion(true)
                        .build(), 0
                )
            }
        }

        val uri = intent.data
        if (uri != null) {
            mainViewModel.openUri(uri)
        }

        val systemBarStyle = SystemBarStyle.auto(
            colors.backgrounds.primary.toArgb(),
           colors.backgrounds.primary.toArgb(),
        ) { true }

        enableEdgeToEdge(
            statusBarStyle = systemBarStyle,
            navigationBarStyle = systemBarStyle,
        )

        setContent {
            OnBoardingComposeTheme {
                val screen by mainViewModel.startDestination

                val navController = rememberNavController()

                Box(
                    modifier = Modifier
                        .background(color = Theme.v2.colors.backgrounds.primary)
                        .safeDrawingPadding()
                ) {

                    Column(modifier = Modifier.fillMaxSize()) {
                        OfflineBanner(mainViewModel.isOffline.value)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            SetupNavGraph(
                                navController = navController,
                                startDestination = screen,
                            )
                        }
                    }

                    LaunchedEffect(navController) {
                        snapshotFlow { navController.currentBackStackEntry }
                            .filterNotNull()
                            .first()

                        launch {
                            mainViewModel.destination.collect {
                                navController.route(it.dst.route, it.opts)
                            }
                        }

                        launch {
                            mainViewModel.route.collect {
                                navController.route(it)
                            }
                        }
                    }

                    BiometryAuthScreen()

                    VsSnackBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        snackbarState = mainViewModel.snakeBarHostState
                    )
                }
            }
        }
    }

}