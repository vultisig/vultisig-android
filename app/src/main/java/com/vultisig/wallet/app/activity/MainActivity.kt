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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.vultisig.wallet.ui.components.BiometryAuthScreen
import com.vultisig.wallet.ui.navigation.SetupNavGraph
import com.vultisig.wallet.ui.navigation.route
import com.vultisig.wallet.ui.theme.Colors
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
internal class MainActivity : AppCompatActivity() {

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
        val systemBarStyle = SystemBarStyle.auto(
            Colors.Default.oxfordBlue800.toArgb(),
            Colors.Default.oxfordBlue800.toArgb(),
        ) { true }

        enableEdgeToEdge(
            statusBarStyle = systemBarStyle,
            navigationBarStyle = systemBarStyle,
        )

        setContent {
            OnBoardingComposeTheme {
                val screen by mainViewModel.startDestination

                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    mainViewModel.destination.collect {
                        navController.route(it.dst.route, it.opts)
                    }
                }

                LaunchedEffect(Unit) {
                    mainViewModel.route.collect {
                        navController.route(it)
                    }
                }

                Box(
                    modifier = Modifier
                        .background(color = Colors.Default.oxfordBlue800)
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