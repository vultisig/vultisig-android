package com.vultisig.wallet.ui.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vultisig.wallet.ui.models.keygen.JoinKeygenView
import com.vultisig.wallet.ui.models.keygen.KeygenFlowView
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_ADDRESS
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_CHAIN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_DST_TOKEN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_QR
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_REQUEST_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_SRC_TOKEN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_TOKEN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_NAME
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_SETUP_TYPE
import com.vultisig.wallet.ui.navigation.Destination.Home.Companion.ARG_SHOW_VAULT_LIST
import com.vultisig.wallet.ui.navigation.Destination.SelectToken.Companion.ARG_SWAP_SELECT
import com.vultisig.wallet.ui.navigation.Destination.SelectToken.Companion.ARG_TARGET_ARG
import com.vultisig.wallet.ui.navigation.Screen.AddChainAccount
import com.vultisig.wallet.ui.screens.BackupPasswordScreen
import com.vultisig.wallet.ui.screens.ChainSelectionScreen
import com.vultisig.wallet.ui.screens.ChainTokensScreen
import com.vultisig.wallet.ui.screens.CustomTokenScreen
import com.vultisig.wallet.ui.screens.ImportFileScreen
import com.vultisig.wallet.ui.screens.NamingVaultScreen
import com.vultisig.wallet.ui.screens.QrAddressScreen
import com.vultisig.wallet.ui.screens.SelectTokenScreen
import com.vultisig.wallet.ui.screens.ShareVaultQrScreen
import com.vultisig.wallet.ui.screens.TokenDetailScreen
import com.vultisig.wallet.ui.screens.TokenSelectionScreen
import com.vultisig.wallet.ui.screens.VaultDetailScreen
import com.vultisig.wallet.ui.screens.VaultRenameScreen
import com.vultisig.wallet.ui.screens.WelcomeScreen
import com.vultisig.wallet.ui.screens.deposit.DepositScreen
import com.vultisig.wallet.ui.screens.folder.CreateFolderScreen
import com.vultisig.wallet.ui.screens.folder.FolderScreen
import com.vultisig.wallet.ui.screens.home.HomeScreen
import com.vultisig.wallet.ui.screens.keygen.AddVaultScreen
import com.vultisig.wallet.ui.screens.keygen.BackupSuggestionScreen
import com.vultisig.wallet.ui.screens.keygen.KeygenEmailScreen
import com.vultisig.wallet.ui.screens.keygen.KeygenPasswordScreen
import com.vultisig.wallet.ui.screens.keygen.SelectVaultTypeScreen
import com.vultisig.wallet.ui.screens.keysign.JoinKeysignView
import com.vultisig.wallet.ui.screens.reshare.ReshareStartScreen
import com.vultisig.wallet.ui.screens.scan.ARG_QR_CODE
import com.vultisig.wallet.ui.screens.scan.ScanQrAndJoin
import com.vultisig.wallet.ui.screens.scan.ScanQrErrorScreen
import com.vultisig.wallet.ui.screens.scan.ScanQrScreen
import com.vultisig.wallet.ui.screens.send.SendScreen
import com.vultisig.wallet.ui.screens.settings.CurrencyUnitSettingScreen
import com.vultisig.wallet.ui.screens.settings.DefaultChainSetting
import com.vultisig.wallet.ui.screens.settings.FAQSettingScreen
import com.vultisig.wallet.ui.screens.settings.LanguageSettingScreen
import com.vultisig.wallet.ui.screens.settings.RegisterVaultScreen
import com.vultisig.wallet.ui.screens.settings.SettingsScreen
import com.vultisig.wallet.ui.screens.settings.VultisigTokenScreen
import com.vultisig.wallet.ui.screens.sign.SignMessageScreen
import com.vultisig.wallet.ui.screens.swap.SwapScreen
import com.vultisig.wallet.ui.screens.transaction.AddAddressEntryScreen
import com.vultisig.wallet.ui.screens.transaction.AddressBookScreen
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsScreen
import com.vultisig.wallet.ui.screens.vault_settings.components.biometrics.BiometricsEnableScreen
import com.vultisig.wallet.ui.screens.vault_settings.components.delete.ConfirmDeleteScreen
import com.vultisig.wallet.ui.theme.slideInFromEndEnterTransition
import com.vultisig.wallet.ui.theme.slideInFromStartEnterTransition
import com.vultisig.wallet.ui.theme.slideOutToEndExitTransition
import com.vultisig.wallet.ui.theme.slideOutToStartExitTransition

@Suppress("ReplaceNotNullAssertionWithElvisReturn")
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
        composable(route = Destination.Welcome.route) {
            WelcomeScreen(navController = navController)
        }
        composable(
            route = Destination.Home.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(ARG_SHOW_VAULT_LIST) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            HomeScreen(navController)
        }
        composable(
            route = Destination.JoinKeygen.STATIC_ROUTE,
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

        composable(
            route = Destination.SelectVaultType.route,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                    nullable = true
                }
            ),
        ) {
            SelectVaultTypeScreen(
                navController = navController
            )
        }

        composable(
            route = Destination.KeygenEmail.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_SETUP_TYPE) {
                    type = NavType.IntType
                },
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(ARG_VAULT_NAME) {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            KeygenEmailScreen(navController)
        }

        composable(
            route = Destination.KeygenPassword.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_SETUP_TYPE) {
                    type = NavType.IntType
                },
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(ARG_VAULT_NAME) {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            KeygenPasswordScreen(navController)
        }

        composable(
            route = Destination.KeygenFlow.STATIC_ROUTE,
            arguments = listOf(
                navArgument(Destination.KeygenFlow.ARG_VAULT_ID) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(Destination.KeygenFlow.ARG_VAULT_NAME) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(ARG_VAULT_SETUP_TYPE) {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument(Destination.ARG_EMAIL) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(Destination.ARG_PASSWORD) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(Destination.KeygenFlow.ARG_PASSWORD_HINT) {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) {
            KeygenFlowView(navController)
        }

        composable(route = Destination.ImportVault.route) {
            ImportFileScreen(navController)
        }
        composable(route = Destination.CreateFolder.route) {
            CreateFolderScreen(navController)
        }
        composable(
            route = Destination.Folder.STATIC_ROUTE,
            arguments = listOf(
                navArgument(Destination.Folder.ARG_FOLDER_ID) { type = NavType.StringType }
            )
        ) {
            FolderScreen()
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
                navArgument(ARG_VAULT_ID) { type = NavType.StringType }
            )
        ) {
            VaultSettingsScreen(
                navController = navController
            )
        }
        composable(
            route = Destination.Details.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType }
            )
        ) {
            VaultDetailScreen(navController)
        }

        composable(
            route = Destination.Rename.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType }
            )
        ) {
            VaultRenameScreen(navController)
        }


        composable(
            route = Destination.JoinKeysign.STATIC_ROUTE,
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
            route = Destination.AddVault.route,
        ) {
            AddVaultScreen(navController)
        }
        composable(
            route = Destination.ChainTokens.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_CHAIN_ID) { type = NavType.StringType }
            )
        ) {
            ChainTokensScreen(navController)
        }
        composable(
            route = Destination.TokenDetail.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_CHAIN_ID) { type = NavType.StringType },
                navArgument(ARG_TOKEN_ID) { type = NavType.StringType },
            )
        ) {
            TokenDetailScreen(navController)
        }
        composable(
            route = Destination.SelectTokens.STATIC_ROUTE,
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
            route = Destination.SelectToken.STATIC_ROUTE,
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
            route = Destination.Send.STATIC_ROUTE,
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
            route = Destination.SignMessage.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
            )
        ) { entry ->
            val args = requireNotNull(entry.arguments)

            SignMessageScreen(
                navController = navController,
                vaultId = requireNotNull(args.getString(ARG_VAULT_ID)),
            )
        }

        composable(
            route = Destination.JoinThroughQr.STATIC_ROUTE,
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
            route = Destination.ScanError.route,
        ) {
            ScanQrErrorScreen()
        }

        composable(
            route = Destination.AddressBook.STATIC_ROUTE,
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
            route = Destination.AddressEntry.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_CHAIN_ID) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(ARG_ADDRESS) {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { entry ->
            val savedStateHandle = entry.savedStateHandle
            val args = requireNotNull(entry.arguments)

            AddAddressEntryScreen(
                navController = navController,
                qrCodeResult = savedStateHandle.remove(ARG_QR_CODE) ?: args.getString(ARG_QR),
            )
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
                navArgument(ARG_DST_TOKEN_ID) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(ARG_SRC_TOKEN_ID) {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { entry ->
            val args = requireNotNull(entry.arguments)
            SwapScreen(
                navController = navController,
                vaultId = requireNotNull(args.getString(ARG_VAULT_ID)),
                chainId = args.getString(ARG_CHAIN_ID),
                srcTokenId = args.getString(ARG_SRC_TOKEN_ID),
                dstTokenId = args.getString(ARG_DST_TOKEN_ID),
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
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType }
            )
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
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
            )
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
            route = Destination.BackupSuggestion.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            BackupSuggestionScreen()
        }

        /*
        disabled for now, as there's no use for it, should be removed in the future

        composable(
            route = Destination.VerifyServerBackup.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                },
                navArgument(Destination.VerifyServerBackup.ARG_SHOULD_SUGGEST_BACKUP) {
                    type = NavType.BoolType
                }
            )
        ) {
            KeygenVerifyServerBackupScreen(
                navController = navController,
            )
        }
         */

        composable(
            route = Destination.ShareVaultQr.STATIC_ROUTE,
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

        composable(
            route = Destination.ReshareStartScreen.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            ReshareStartScreen(navController)
        }

        composable(
            route = Destination.BiometricsEnable.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            BiometricsEnableScreen(navController)
        }

        composable(
            route = Destination.RegisterVault.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            RegisterVaultScreen(navController)
        }
    }
}