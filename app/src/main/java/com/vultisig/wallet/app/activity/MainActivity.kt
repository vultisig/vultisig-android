@file:OptIn(ExperimentalAnimationApi::class)

package com.vultisig.wallet.app.activity

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.vultisig.wallet.app.activity.components.AnimatedSplash
import com.vultisig.wallet.app.activity.components.CheckDeeplink
import com.vultisig.wallet.app.activity.components.MainActivityContent
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.v2.V2.colors
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

                val isLoading by mainViewModel.isLoading
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    AnimatedSplash(
                        isLoading = isLoading,
                        onSplashComplete = {
                            showSplash = false
                        },
                    )
                } else {
                    var isNavigationReady by remember {
                        mutableStateOf(false)
                    }

                    if (isNavigationReady) {
                        CheckDeeplink(mainViewModel::openUri)
                    }

                    CheckUpdates()

                    MainActivityContent(
                        navController = navController,
                        mainViewModel = mainViewModel,
                        startDestination = screen,
                        onNavigationReady = {
                            isNavigationReady = true
                        },
                    )
                }
            }
        }

    }
    @Composable
    private fun CheckUpdates() {
        val lifecycle = LocalLifecycleOwner.current.lifecycle

        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.startUpdateEvent.collect {
                    startImmediateUpdate()
                }
            }
        }
    }

    private fun startImmediateUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            try {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    this,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE)
                        .setAllowAssetPackDeletion(true)
                        .build(),
                    0
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to start update flow")
            }
        }
    }
}