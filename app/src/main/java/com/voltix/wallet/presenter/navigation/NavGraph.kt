package com.voltix.wallet.presenter.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.voltix.wallet.presenter.create_new_vault.CreateNewVault
import com.voltix.wallet.presenter.device_list.DeviceList
import com.voltix.wallet.presenter.generating_key_gen.GeneratingKeyGen
import com.voltix.wallet.presenter.home.HomeScreen
import com.voltix.wallet.presenter.import_file.ImportFile
import com.voltix.wallet.presenter.keygen.KeygenQr
import com.voltix.wallet.presenter.pair.Pair
import com.voltix.wallet.presenter.keygen.Setup
import com.voltix.wallet.presenter.signing_error.SigningError
import com.voltix.wallet.presenter.welcome.WelcomeScreen

@ExperimentalAnimationApi
@Composable
fun SetupNavGraph(
    navController: NavHostController,
    startDestination: String
) {

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = Screen.Welcome.route) {
            WelcomeScreen(navController = navController)
        }
        composable(route = Screen.Home.route) {
            HomeScreen(navController)
        }


        /*----------*/
        composable(route = Screen.CreateNewVault.route) {
            CreateNewVault(navController)
        }


        composable(route = Screen.Setup.route) {
            Setup(navController)
        }


        composable(route = Screen.KeygenQr.route) {backStackEntry ->
            KeygenQr(navController)
        }


        composable(route = Screen.DeviceList.route) { navBackStackEntry ->
            val itemCount = navBackStackEntry.arguments?.getString("count")?.toInt() ?: 2
            DeviceList(navController, itemCount)
        }


        composable(route = Screen.Pair.route) {
            Pair(navController)
        }


        composable(route = Screen.GeneratingKeyGen.route) {
            GeneratingKeyGen(navController)
        }


        composable(route = Screen.SigningError.route) {
            SigningError(navController)
        }

        composable(route = Screen.ImportFile.route) { navBackStackEntry ->
            val hasFile = navBackStackEntry.arguments?.getString("has_file")?.toBoolean() ?: false
            ImportFile(navController, hasFile)
        }
    }
}