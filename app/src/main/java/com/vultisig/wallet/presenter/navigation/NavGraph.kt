package com.vultisig.wallet.presenter.navigation

import android.content.Context
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.home.HomeScreen
import com.vultisig.wallet.presenter.home.VaultDetailScreen
import com.vultisig.wallet.presenter.import_file.ImportFile
import com.vultisig.wallet.presenter.keygen.CreateNewVault
import com.vultisig.wallet.presenter.keygen.JoinKeygenView
import com.vultisig.wallet.presenter.keygen.KeygenFlowView
import com.vultisig.wallet.presenter.keygen.Setup
import com.vultisig.wallet.presenter.navigation.Screen.VaultDetail.AddChainAccount
import com.vultisig.wallet.presenter.keysign.KeysignFlowView
import com.vultisig.wallet.presenter.signing_error.SigningError
import com.vultisig.wallet.presenter.welcome.WelcomeScreen
import com.vultisig.wallet.ui.screens.ChainSelectionScreen

@ExperimentalAnimationApi
@Composable
fun SetupNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    val context: Context = LocalContext.current

    NavHost(
        navController = navController, startDestination = startDestination
    ) {
        composable(route = Screen.Welcome.route) {
            WelcomeScreen(navController = navController)
        }
        composable(route = Screen.Home.route) {
            HomeScreen(navController)
        }

        composable(route = Screen.CreateNewVault.route) {
            CreateNewVault(navController)
        }
        composable(route = Screen.JoinKeygen.route) {
            val vaultDB = VaultDB(context)
            val allVaults = vaultDB.selectAll()
            JoinKeygenView(navController, Vault("New Vault ${allVaults.size + 1}"))
        }

        composable(route = Screen.Setup.route) {
            Setup(navController)
        }

        composable(route = Screen.KeygenFlow.route) {
            val vaultDB = VaultDB(context)
            val allVaults = vaultDB.selectAll()
            // TODO: later on will need to deal with reshare
            KeygenFlowView(navController, Vault("New Vault ${allVaults.size + 1}"))
        }

        composable(route = Screen.SigningError.route) {
            SigningError(navController)
        }

        composable(route = Screen.ImportFile.route) { navBackStackEntry ->
            val hasFile = navBackStackEntry.arguments?.getString("has_file")?.toBoolean() ?: false
            ImportFile(navController, hasFile)
        }
        composable(
            route = Screen.VaultDetail.route,
        ) { navBackStackEntry ->
            val vaultId = navBackStackEntry.arguments?.getString("vault_name") ?: ""
            VaultDetailScreen(
                vaultId = vaultId,
                navHostController = navController,
            )
        }
        composable(
            route = AddChainAccount.route,
            arguments = listOf(
                navArgument(AddChainAccount.ARG_VAULT_ID) { type = NavType.StringType }
            )
        ) {
            ChainSelectionScreen(
                navController = navController
            )
        }
        composable(route = Screen.KeysignFlow.route) {
            KeysignFlowView(navController)
        }
    }
}