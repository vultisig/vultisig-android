package com.voltix.wallet.app.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.voltix.wallet.presenter.navigation.SetupNavGraph
import com.voltix.wallet.app.ui.theme.OnBoardingComposeTheme
import dagger.hilt.android.AndroidEntryPoint

@ExperimentalAnimationApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().setKeepOnScreenCondition {
            !mainViewModel.isLoading.value
        }

        setContent {
            OnBoardingComposeTheme {
                val screen by mainViewModel.startDestination
                val navController = rememberNavController()
                SetupNavGraph(navController = navController, startDestination = screen)
            }
        }
    }
}