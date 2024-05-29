package com.vultisig.wallet.ui.navigation

import android.content.Context
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.presenter.import_file.ImportFileScreen
import com.vultisig.wallet.presenter.keygen.JoinKeygenView
import com.vultisig.wallet.presenter.keygen.KeygenFlowView
import com.vultisig.wallet.presenter.keysign.KeysignFlowView
import com.vultisig.wallet.presenter.keysign.KeysignShareViewModel
import com.vultisig.wallet.presenter.qr_address.QrAddressScreen
import com.vultisig.wallet.presenter.settings.currency_unit_setting.CurrencyUnitSettingScreen
import com.vultisig.wallet.presenter.settings.default_chains_setting.DefaultChainSetting
import com.vultisig.wallet.presenter.settings.faq_setting.FAQSettingScreen
import com.vultisig.wallet.presenter.settings.language_setting.LanguageSettingScreen
import com.vultisig.wallet.presenter.settings.settings_main.SettingsScreen
import com.vultisig.wallet.presenter.settings.vultisig_token_setting.VultisigTokenScreen
import com.vultisig.wallet.presenter.signing_error.SigningError
import com.vultisig.wallet.presenter.vault_setting.vault_detail.VaultDetailScreen
import com.vultisig.wallet.presenter.vault_setting.vault_edit.VaultRenameScreen
import com.vultisig.wallet.presenter.welcome.WelcomeScreen
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_CHAIN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_TRANSACTION_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Screen.AddChainAccount
import com.vultisig.wallet.ui.screens.ARG_QR_CODE
import com.vultisig.wallet.ui.screens.ChainSelectionScreen
import com.vultisig.wallet.ui.screens.ChainTokensScreen
import com.vultisig.wallet.ui.screens.NamingVaultScreen
import com.vultisig.wallet.ui.screens.ScanQrScreen
import com.vultisig.wallet.ui.screens.SendScreen
import com.vultisig.wallet.ui.screens.TokenSelectionScreen
import com.vultisig.wallet.ui.screens.VerifyTransactionScreen
import com.vultisig.wallet.ui.screens.home.HomeScreen
import com.vultisig.wallet.ui.screens.keygen.AddVaultScreen
import com.vultisig.wallet.ui.screens.keygen.Setup
import com.vultisig.wallet.ui.screens.keysign.JoinKeysignView
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsScreen
import com.vultisig.wallet.ui.theme.slideInFromEndEnterTransition
import com.vultisig.wallet.ui.theme.slideInFromStartEnterTransition
import com.vultisig.wallet.ui.theme.slideOutToEndExitTransition
import com.vultisig.wallet.ui.theme.slideOutToStartExitTransition

@ExperimentalAnimationApi
@Composable
internal fun SetupNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    val context: Context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = slideInFromEndEnterTransition(),
        exitTransition = slideOutToStartExitTransition(),
        popEnterTransition = slideInFromStartEnterTransition(),
        popExitTransition = slideOutToEndExitTransition(),
    ) {
        composable(route = Screen.Welcome.route) {
            WelcomeScreen(navController = navController)
        }
        composable(
            route = Destination.Home.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            HomeScreen(navController)
        }
        composable(route = Screen.JoinKeygen.route) { entry ->
            val savedStateHandle = entry.savedStateHandle
            val qrCodeResult = savedStateHandle.get<String>(ARG_QR_CODE)

            JoinKeygenView(
                navController = navController,
                qrCodeResult = qrCodeResult,
            )
        }

        composable(route = Screen.Setup.route) {
            Setup(navController)
        }

        composable(
            route = Screen.KeygenFlow.route,
            arguments = listOf(navArgument(Screen.KeygenFlow.ARG_VAULT_NAME) {
                type = NavType.StringType
                defaultValue = Screen.KeygenFlow.DEFAULT_NEW_VAULT
            })
        ) { navBackStackEntry ->
            val vaultId =
                navBackStackEntry.arguments?.getString(Screen.KeygenFlow.ARG_VAULT_NAME) ?: ""

            KeygenFlowView(navController, vaultId)
        }

        composable(route = Screen.SigningError.route) {
            SigningError(navController)
        }

        composable(route = Screen.ImportFile.route) {
            ImportFileScreen(navController)
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
        composable(
            route = Destination.VaultSettings.STATIC_ROUTE,
            arguments = listOf(
                navArgument(Destination.VaultSettings.ARG_VAULT_ID) { type = NavType.StringType }
            )
        ) {
            VaultSettingsScreen(
                navController = navController
            )
        }
        composable(
            route = Destination.Details.STATIC_ROUTE,
            arguments = listOf(
                navArgument(Destination.VaultSettings.ARG_VAULT_ID) { type = NavType.StringType }
            )
        ) {
            VaultDetailScreen(navController)
        }

        composable(
            route = Destination.Rename.STATIC_ROUTE,
            arguments = listOf(
                navArgument(Destination.VaultSettings.ARG_VAULT_ID) { type = NavType.StringType }
            )
        ) {
            VaultRenameScreen(navController)
        }


        composable(route = Screen.JoinKeysign.route,
            arguments = listOf(
                navArgument(Screen.JoinKeysign.ARG_VAULT_ID) { type = NavType.StringType }
            )
        ) { entry ->
            val savedStateHandle = entry.savedStateHandle
            val qrCodeResult = savedStateHandle.get<String>(ARG_QR_CODE)

            JoinKeysignView(
                navController = navController,
                qrCodeResult = qrCodeResult,
            )
        }

        composable(
            route = Screen.CreateNewVault.route
        ) {
            AddVaultScreen(navController)
        }
        composable(
            route = Destination.AddVault.route,
        ) {
            AddVaultScreen(navController)
        }

        composable(
            route = Destination.Keysign.staticRoute,
            arguments = Destination.transactionArgs,
        ) { entry ->
            val transactionId = entry.arguments?.getString(ARG_TRANSACTION_ID)!!

            val keysignShareViewModel: KeysignShareViewModel =
                hiltViewModel(context as MainActivity)
            keysignShareViewModel.loadTransaction(transactionId)

            KeysignFlowView(navController)
        }
        composable(
            route = Destination.ChainTokens.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_CHAIN_ID) { type = NavType.StringType }
            )
        ) {
            ChainTokensScreen(navController)
        }
        composable(
            route = Destination.SelectTokens.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_CHAIN_ID) { type = NavType.StringType }
            )
        ) {
            TokenSelectionScreen(
                navController = navController
            )
        }
        composable(
            route = Destination.Send.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_CHAIN_ID) { type = NavType.StringType },
            )
        ) {
            val savedStateHandle = navController.currentBackStackEntry
                ?.savedStateHandle
            val qrCodeResult = savedStateHandle?.get<String>(ARG_QR_CODE)
            savedStateHandle?.remove<String>(ARG_QR_CODE)

            SendScreen(
                navController = navController,
                qrCodeResult = qrCodeResult
            )
        }
        composable(
            route = Destination.ScanQr.route,
        ) {
            ScanQrScreen(navController = navController)
        }
        composable(
            route = Destination.VerifyTransaction.staticRoute,
            arguments = Destination.transactionArgs,
        ) {
            VerifyTransactionScreen(navController = navController)
        }

        composable(
            route = Destination.Settings.route,
        ) {
            SettingsScreen(navController = navController)
        }

        composable(
            route = Destination.DefaultChainSetting.route,
        ) {
            DefaultChainSetting(navController = navController)
        }

        composable(
            route = Destination.FAQSetting.route,
        ) {
            FAQSettingScreen(navController = navController)
        }

        composable(
            route = Destination.VultisigToken.route,
        ) {
            VultisigTokenScreen(navController = navController)
        }

        composable(
            route = Destination.LanguageSetting.route,
        ) {
            LanguageSettingScreen(navController = navController)
        }

        composable(
            route = Destination.CurrencyUnitSetting.route,
        ) {
            CurrencyUnitSettingScreen(navController = navController)
        }

        composable(
            route = Destination.QrAddressScreen.STATIC_ROUTE,
        ) {
            QrAddressScreen(navController = navController)
        }

        composable(
            route = Destination.NamingVault.route,
        ) {
            NamingVaultScreen(navController = navController)
        }
    }
}