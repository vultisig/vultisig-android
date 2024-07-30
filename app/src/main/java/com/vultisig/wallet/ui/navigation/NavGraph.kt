package com.vultisig.wallet.ui.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vultisig.wallet.presenter.import_file.ImportFileScreen
import com.vultisig.wallet.presenter.keygen.JoinKeygenView
import com.vultisig.wallet.presenter.keygen.KeygenFlowView
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
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_QR
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_REQUEST_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_TOKEN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Destination.SelectToken.Companion.ARG_SWAP_SELECT
import com.vultisig.wallet.ui.navigation.Destination.SelectToken.Companion.ARG_TARGET_ARG
import com.vultisig.wallet.ui.navigation.Screen.AddChainAccount
import com.vultisig.wallet.ui.screens.ARG_QR_CODE
import com.vultisig.wallet.ui.screens.BackupPasswordScreen
import com.vultisig.wallet.ui.screens.ChainSelectionScreen
import com.vultisig.wallet.ui.screens.ChainTokensScreen
import com.vultisig.wallet.ui.screens.CustomTokenScreen
import com.vultisig.wallet.ui.screens.NamingVaultScreen
import com.vultisig.wallet.ui.screens.ScanQrAndJoin
import com.vultisig.wallet.ui.screens.ScanQrScreen
import com.vultisig.wallet.ui.screens.SelectTokenScreen
import com.vultisig.wallet.ui.screens.ShareVaultQrScreen
import com.vultisig.wallet.ui.screens.TokenDetailScreen
import com.vultisig.wallet.ui.screens.TokenSelectionScreen
import com.vultisig.wallet.ui.screens.deposit.DepositScreen
import com.vultisig.wallet.ui.screens.home.HomeScreen
import com.vultisig.wallet.ui.screens.keygen.AddVaultScreen
import com.vultisig.wallet.ui.screens.keygen.BackupSuggestionScreen
import com.vultisig.wallet.ui.screens.keygen.Setup
import com.vultisig.wallet.ui.screens.keysign.JoinKeysignView
import com.vultisig.wallet.ui.screens.send.SendScreen
import com.vultisig.wallet.ui.screens.swap.SwapScreen
import com.vultisig.wallet.ui.screens.transaction.AddAddressEntryScreen
import com.vultisig.wallet.ui.screens.transaction.AddressBookScreen
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsScreen
import com.vultisig.wallet.ui.screens.vault_settings.components.ConfirmDeleteScreen
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
        composable(
            route = Destination.JoinKeygen.staticRoute,
            arguments = listOf(
                navArgument(ARG_QR) { type = NavType.StringType }
            )
        ) { entry ->
            val qrCodeResult = entry.arguments?.getString(ARG_QR)!!

            JoinKeygenView(
                navController = navController,
                qrCodeResult = qrCodeResult,
            )
        }

        composable(route = Screen.Setup.route,
            arguments = listOf(
                navArgument(Screen.Setup.ARG_VAULT_ID) {
                    type = NavType.StringType
                    defaultValue = Destination.KeygenFlow.DEFAULT_NEW_VAULT
                }
            )) { navBackStackEntry ->
            val vaultId =
                navBackStackEntry.arguments?.getString(Screen.Setup.ARG_VAULT_ID) ?: ""
            Setup(navController, vaultId)
        }

        composable(
            route = Destination.KeygenFlow.STATIC_ROUTE,
            arguments = listOf(navArgument(Destination.KeygenFlow.ARG_VAULT_NAME) {
                type = NavType.StringType
                defaultValue = Destination.KeygenFlow.DEFAULT_NEW_VAULT
            }, navArgument(Destination.KeygenFlow.ARG_VAULT_TYPE) {
                type = NavType.IntType
                defaultValue = 0
            }
            )) { navBackStackEntry ->
            val vaultId =
                navBackStackEntry.arguments?.getString(Destination.KeygenFlow.ARG_VAULT_NAME) ?: ""
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


        composable(
            route = Destination.JoinKeysign.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_QR) { type = NavType.StringType }
            )
        ) {
            JoinKeysignView(
                navController = navController,
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
            route = Destination.ChainTokens.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_CHAIN_ID) { type = NavType.StringType }
            )
        ) {
            ChainTokensScreen(navController)
        }
        composable(
            route = Destination.TokenDetail.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_CHAIN_ID) { type = NavType.StringType },
                navArgument(ARG_TOKEN_ID) { type = NavType.StringType },
            )
        ) {
            TokenDetailScreen(navController)
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
            route = Destination.SelectToken.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_TARGET_ARG) { type = NavType.StringType },
                navArgument(ARG_SWAP_SELECT) { type = NavType.BoolType }
            )
        ) {
            SelectTokenScreen(
                navController = navController
            )
        }
        composable(
            route = Destination.Send.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_CHAIN_ID) {
                    type = NavType.StringType
                    // if chainId = null show all tokens
                    // else only tokens from chain
                    nullable = true
                },
                navArgument(ARG_TOKEN_ID) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(ARG_QR) {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { entry ->
            val savedStateHandle = entry.savedStateHandle
            val args = requireNotNull(entry.arguments)

            SendScreen(
                navController = navController,
                qrCodeResult = savedStateHandle.remove(ARG_QR_CODE) ?: args.getString(ARG_QR),
                vaultId = requireNotNull(args.getString(ARG_VAULT_ID)),
                chainId = args.getString(ARG_CHAIN_ID),
                startWithTokenId = args.getString(ARG_TOKEN_ID),
            )
        }
        composable(
            route = Destination.ScanQr.route,
        ) {
            ScanQrScreen(navController = navController)
        }

        composable(
            route = Destination.JoinThroughQr.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            ScanQrAndJoin(navController = navController)
        }

        composable(
            route = Destination.AddressBook.staticRoute,
            arguments = listOf(
                navArgument(ARG_REQUEST_ID) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(ARG_CHAIN_ID) {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            AddressBookScreen(navController = navController)
        }

        composable(
            route = Destination.AddAddressEntry.staticRoute,
        ) {
            AddAddressEntryScreen(navController = navController)
        }

        composable(
            route = Destination.Swap.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_CHAIN_ID) {
                    type = NavType.StringType
                    // if chainId = null show all tokens
                    // else only tokens from chain
                    nullable = true
                },
            )
        ) { entry ->
            val args = requireNotNull(entry.arguments)
            SwapScreen(
                navController = navController,
                vaultId = requireNotNull(args.getString(ARG_VAULT_ID)),
                chainId = args.getString(ARG_CHAIN_ID),
            )
        }

        composable(
            route = Destination.Deposit.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_CHAIN_ID) { type = NavType.StringType },
            )
        ) { entry ->
            val args = requireNotNull(entry.arguments)

            DepositScreen(
                navController = navController,
                vaultId = requireNotNull(args.getString(ARG_VAULT_ID)),
                chainId = requireNotNull(args.getString(ARG_CHAIN_ID)),
            )
        }

        composable(
            route = Destination.Settings.STATIC_ROUTE,
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
            route = Destination.NamingVault.STATIC_ROUTE,
        ) {
            NamingVaultScreen(navController = navController)
        }

        composable(
            route = Destination.ConfirmDelete.STATIC_ROUTE,
        ) {
            ConfirmDeleteScreen(navController)
        }

        composable(
            route = Destination.BackupPassword.STATIC_ROUTE,
        ) {
            BackupPasswordScreen(navController)
        }

        composable(
            route = Destination.BackupSuggestion.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            BackupSuggestionScreen()
        }
        composable(
            route = Destination.ShareVaultQr.staticRoute,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
            )
        ) {
            ShareVaultQrScreen(
                navController = navController
            )
        }

        composable(
            route = Destination.CustomToken.STATIC_ROUTE,
        ) {
            CustomTokenScreen(navController)
        }
    }
}