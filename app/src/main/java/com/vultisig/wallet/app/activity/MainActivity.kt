@file:OptIn(ExperimentalAnimationApi::class)

package com.vultisig.wallet.app.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.vultisig.wallet.ui.components.BiometryAuthScreen
import com.vultisig.wallet.ui.navigation.SetupNavGraph
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
internal class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels<MainViewModel>()

    @Inject
    internal lateinit var appUpdateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
            .setKeepOnScreenCondition {
                mainViewModel.isLoading.value
            }

        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        mainViewModel.checkUpdates { appUpdateInfo ->
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                this@MainActivity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE)
                    .setAllowAssetPackDeletion(true)
                    .build(), 0
            )
        }

        setContent {
            OnBoardingComposeTheme {
                val screen by mainViewModel.startDestination

                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    mainViewModel.destination.collect {
                        navController.route(it.dst.route, it.opts)
                    }
                }

                Box(
                    modifier = Modifier
                        .safeDrawingPadding()
                ) {
                    SetupNavGraph(
                        navController = navController,
                        startDestination = screen,
                    )

                    BiometryAuthScreen()

                    SnackbarHost(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        hostState = mainViewModel.snakeBarHostState
                    )
                }
            }
        }
    }

}