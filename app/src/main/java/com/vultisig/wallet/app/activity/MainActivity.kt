package com.vultisig.wallet.app.activity

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.ui.navigation.SetupNavGraph
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@ExperimentalAnimationApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels<MainViewModel>()
    val context: Context = this
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().setKeepOnScreenCondition {
            !mainViewModel.isLoading.value
        }

        setContent {
            OnBoardingComposeTheme {
                mainViewModel.setData(context)
                val screen by mainViewModel.startDestination

                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    mainViewModel.destination.collect {
                        navController.navigate(it.route)
                    }
                }

                SetupNavGraph(
                    navController = navController,
                    startDestination = screen,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()
        Timber.d("onPause: user is not active")
    }

    override fun onDestroy() {
        Timber.d("onDestroy: ")
        super.onDestroy()
    }
}