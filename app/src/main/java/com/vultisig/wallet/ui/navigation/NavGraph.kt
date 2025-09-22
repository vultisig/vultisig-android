package com.vultisig.wallet.ui.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import androidx.navigation.toRoute
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_ADDRESS
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_CHAIN_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_EXPIRATION_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_REFERRAL_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_REQUEST_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Destination.Home.Companion.ARG_SHOW_VAULT_LIST
import com.vultisig.wallet.ui.navigation.Destination.SelectToken.Companion.ARG_SWAP_SELECT
import com.vultisig.wallet.ui.navigation.Destination.SelectToken.Companion.ARG_TARGET_ARG
import com.vultisig.wallet.ui.navigation.Route.*
import com.vultisig.wallet.ui.screens.BackupPasswordScreen
import com.vultisig.wallet.ui.screens.ChainSelectionScreen
import com.vultisig.wallet.ui.screens.ChainTokensScreen
import com.vultisig.wallet.ui.screens.CustomTokenScreen
import com.vultisig.wallet.ui.screens.ImportFileScreen
import com.vultisig.wallet.ui.screens.QrAddressScreen
import com.vultisig.wallet.ui.screens.SecretScreen
import com.vultisig.wallet.ui.screens.SelectTokenScreen
import com.vultisig.wallet.ui.screens.ShareVaultQrScreen
import com.vultisig.wallet.ui.screens.TokenDetailScreen
import com.vultisig.wallet.ui.screens.TokenSelectionScreen
import com.vultisig.wallet.ui.screens.VaultDetailScreen
import com.vultisig.wallet.ui.screens.VaultRenameScreen
import com.vultisig.wallet.ui.screens.backup.BackupPasswordRequestScreen
import com.vultisig.wallet.ui.screens.deposit.DepositScreen
import com.vultisig.wallet.ui.screens.deposit.VerifyDepositScreen
import com.vultisig.wallet.ui.screens.folder.CreateFolderScreen
import com.vultisig.wallet.ui.screens.folder.FolderScreen
import com.vultisig.wallet.ui.screens.home.FastVaultPasswordReminderDialog
import com.vultisig.wallet.ui.screens.home.HomeScreen
import com.vultisig.wallet.ui.screens.keygen.BackupVaultScreen
import com.vultisig.wallet.ui.screens.keygen.ChooseVaultScreen
import com.vultisig.wallet.ui.screens.keygen.FastVaultEmailScreen
import com.vultisig.wallet.ui.screens.keygen.FastVaultPasswordHintScreen
import com.vultisig.wallet.ui.screens.keygen.FastVaultPasswordScreen
import com.vultisig.wallet.ui.screens.keygen.FastVaultVerificationScreen
import com.vultisig.wallet.ui.screens.keygen.JoinKeygenScreen
import com.vultisig.wallet.ui.screens.keygen.KeygenScreen
import com.vultisig.wallet.ui.screens.keygen.NameVaultScreen
import com.vultisig.wallet.ui.screens.keygen.StartScreen
import com.vultisig.wallet.ui.screens.keygen.VaultConfirmationScreen
import com.vultisig.wallet.ui.screens.keysign.JoinKeysignView
import com.vultisig.wallet.ui.screens.keysign.KeysignPasswordScreen
import com.vultisig.wallet.ui.screens.keysign.KeysignScreen
import com.vultisig.wallet.ui.screens.migration.MigrationOnboardingScreen
import com.vultisig.wallet.ui.screens.migration.MigrationPasswordScreen
import com.vultisig.wallet.ui.screens.onboarding.OnboardingScreen
import com.vultisig.wallet.ui.screens.onboarding.OnboardingSummaryScreen
import com.vultisig.wallet.ui.screens.onboarding.VaultBackupOnboardingScreen
import com.vultisig.wallet.ui.screens.onboarding.VaultBackupSummaryScreen
import com.vultisig.wallet.ui.screens.peer.KeygenPeerDiscoveryScreen
import com.vultisig.wallet.ui.screens.referral.ReferralCreateScreen
import com.vultisig.wallet.ui.screens.referral.ReferralOnboardingScreen
import com.vultisig.wallet.ui.screens.referral.ReferralScreen
import com.vultisig.wallet.ui.screens.referral.ReferralEditExternalScreen
import com.vultisig.wallet.ui.screens.referral.ReferralEditVaultScreen
import com.vultisig.wallet.ui.screens.referral.ReferralVaultListScreen
import com.vultisig.wallet.ui.screens.referral.ReferralViewScreen
import com.vultisig.wallet.ui.screens.reshare.ReshareStartScreen
import com.vultisig.wallet.ui.screens.scan.ScanQrErrorScreen
import com.vultisig.wallet.ui.screens.scan.ScanQrScreen
import com.vultisig.wallet.ui.screens.select.SelectAssetScreen
import com.vultisig.wallet.ui.screens.select.SelectNetworkScreen
import com.vultisig.wallet.ui.screens.send.SendScreen
import com.vultisig.wallet.ui.screens.send.VerifySendScreen
import com.vultisig.wallet.ui.screens.settings.CheckForUpdateScreen
import com.vultisig.wallet.ui.screens.settings.CurrencyUnitSettingScreen
import com.vultisig.wallet.ui.screens.settings.DefaultChainSetting
import com.vultisig.wallet.ui.screens.settings.FaqSettingScreen
import com.vultisig.wallet.ui.screens.settings.LanguageSettingScreen
import com.vultisig.wallet.ui.screens.settings.RegisterVaultScreen
import com.vultisig.wallet.ui.screens.settings.SettingsScreen
import com.vultisig.wallet.ui.screens.settings.VultisigTokenScreen
import com.vultisig.wallet.ui.screens.sign.SignMessageScreen
import com.vultisig.wallet.ui.screens.swap.SwapScreen
import com.vultisig.wallet.ui.screens.swap.VerifySwapScreen
import com.vultisig.wallet.ui.screens.transaction.AddAddressEntryScreen
import com.vultisig.wallet.ui.screens.transaction.AddressBookBottomSheet
import com.vultisig.wallet.ui.screens.transaction.AddressBookScreen
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsScreen
import com.vultisig.wallet.ui.screens.vault_settings.components.biometrics.BiometricsEnableScreen
import com.vultisig.wallet.ui.screens.vault_settings.components.delete.ConfirmDeleteScreen
import com.vultisig.wallet.ui.screens.vault_settings.components.security.SecurityScannerEnableScreen
import com.vultisig.wallet.ui.theme.slideInFromBottomEnterTransition
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

        composable<ImportVault> {
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
        dialog <AddChainAccount>{
            ChainSelectionScreen()
        }
        composable(
            route = Destination.VaultSettings.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType }
            )
        ) {
            VaultSettingsScreen()
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
            route = Destination.AddVault.route,
        ) {
            StartScreen()
        }
        composable(
            route = Destination.ChainTokens.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(ARG_CHAIN_ID) { type = NavType.StringType },
            )
        ) {
            ChainTokensScreen(navController)
        }
        composable<TokenDetail> {
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
            route = Destination.AddressBook.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_REQUEST_ID) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(ARG_CHAIN_ID) {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                    nullable = false
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
                },
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                    nullable = false
                },

                )
        ) {
            AddAddressEntryScreen(
                navController = navController,
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
            SettingsScreen()
        }

        composable(
            route = Destination.DefaultChainSetting.route,
        ) {
            DefaultChainSetting(navController = navController)
        }

        composable(
            route = Destination.FAQSetting.route,
        ) {
            FaqSettingScreen(navController = navController)
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
            route = Destination.ConfirmDelete.STATIC_ROUTE,
        ) {
            ConfirmDeleteScreen(navController)
        }

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
            route = Destination.OnChainSecurity.route,
        ) {
            SecurityScannerEnableScreen(navController)
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

        composable<Secret> {
            SecretScreen(navController)
        }

        // onboarding
        composable<Onboarding.VaultCreation>(
            enterTransition = slideInFromBottomEnterTransition(),
        ) {
            OnboardingScreen()
        }

        composable<Onboarding.VaultCreationSummary> {
            OnboardingSummaryScreen()
        }

        // home

        dialog<FastVaultPasswordReminder> {
            FastVaultPasswordReminderDialog()
        }

        // scan

        composable<ScanQr> {
            ScanQrScreen()
        }

        composable<ScanError> {
            ScanQrErrorScreen()
        }

        // keygen vault info
        composable<ChooseVaultType> {
            ChooseVaultScreen()
        }

        composable<VaultInfo.Name> {
            NameVaultScreen()
        }

        composable<VaultInfo.Email> {
            FastVaultEmailScreen()
        }

        composable<VaultInfo.Password> {
            FastVaultPasswordScreen()
        }

        composable<VaultInfo.PasswordHint> {
            FastVaultPasswordHintScreen()
        }

        // keygen
        composable<Keygen.Join> {
            JoinKeygenScreen()
        }

        composable<Keygen.PeerDiscovery> {
            KeygenPeerDiscoveryScreen()
        }

        composable<Keygen.Generating> {
            KeygenScreen()
        }

        // vault backup
        composable<Onboarding.VaultBackup>(
            enterTransition = slideInFromBottomEnterTransition(),
        ) {
            VaultBackupOnboardingScreen()
        }

        composable<FastVaultVerification> {
            FastVaultVerificationScreen()
        }

        composable<BackupVault> {
            BackupVaultScreen()
        }

        composable<BackupPasswordRequest> {
            BackupPasswordRequestScreen()
        }

        composable<BackupPassword> {
            BackupPasswordScreen()
        }

        composable<VaultBackupSummary> {
            VaultBackupSummaryScreen()
        }

        composable<VaultConfirmation> {
            VaultConfirmationScreen()
        }

        // transactions

        // select asset / network
        dialog<SelectAsset> {
            SelectAssetScreen()
        }

        dialog<SelectNetwork> {
            SelectNetworkScreen()
        }

        // send
        composable<Send> {
            SendScreen()
        }

        composable<VerifySend> {
            VerifySendScreen()
        }

        // swap
        composable<Swap> {
            SwapScreen()
        }

        composable<VerifySwap> {
            VerifySwapScreen()
        }

        composable<VerifyDeposit> {
            VerifyDepositScreen(
                navController = navController,
            )
        }

        // keysign
        composable<Keysign.Join> {
            JoinKeysignView(
                navController = navController,
            )
        }

        composable<Keysign.Password> {
            KeysignPasswordScreen()
        }

        composable<Keysign.Keysign> { entry ->
            val args = entry.toRoute<Keysign.Keysign>()
            KeysignScreen(
                txType = args.txType,
                transactionId = args.transactionId,
            )
        }

        // migration

        composable<Migration.Onboarding> {
            MigrationOnboardingScreen()
        }

        composable<Migration.Password> {
            MigrationPasswordScreen()
        }

        // address book

        dialog<AddressBook> {
            AddressBookBottomSheet()
        }

        composable(
            route = Destination.ReferralCode.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            ReferralScreen(
                navController = navController,
            )
        }

        composable(
            route = Destination.ReferralOnboarding.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            ReferralOnboardingScreen(
                navController = navController,
            )
        }

        composable(
            route = Destination.ReferralListVault.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            ReferralVaultListScreen(
                navController = navController,
            )
        }

        composable(
            route = Destination.ReferralCode.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            ReferralScreen(
                navController = navController,
            )
        }

        composable(
            route = Destination.ReferralOnboarding.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            ReferralOnboardingScreen(
                navController = navController,
            )
        }

        composable(
            route = Destination.ReferralExternalEdition.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            ReferralEditExternalScreen(
                navController = navController,
            )
        }

        composable(
            route = Destination.ReferralCreation.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            ReferralCreateScreen(
                navController = navController,
            )
        }

        composable(
            route = Destination.ReferralView.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                },
                navArgument(ARG_REFERRAL_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            ReferralViewScreen(
                navController = navController,
            )
        }

        composable(
            route = Destination.ReferralVaultEdition.STATIC_ROUTE,
            arguments = listOf(
                navArgument(ARG_VAULT_ID) {
                    type = NavType.StringType
                },
                navArgument(ARG_REFERRAL_ID) {
                    type = NavType.StringType
                },
                navArgument(ARG_EXPIRATION_ID) {
                    type = NavType.StringType
                }
            )
        ) {
            ReferralEditVaultScreen(
                navController = navController,
            )
        }

        composable(
            route = Destination.CheckForUpdateSetting.route,
        ) {
            CheckForUpdateScreen()
        }
    }
}