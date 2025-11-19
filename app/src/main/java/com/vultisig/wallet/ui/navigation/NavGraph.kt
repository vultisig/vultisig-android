@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_REFERRAL_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Route.AddChainAccount
import com.vultisig.wallet.ui.navigation.Route.AddressBook
import com.vultisig.wallet.ui.navigation.Route.AddressEntry
import com.vultisig.wallet.ui.navigation.Route.BackupPasswordRequest
import com.vultisig.wallet.ui.navigation.Route.BackupVault
import com.vultisig.wallet.ui.navigation.Route.BackupVault.BackupPasswordType
import com.vultisig.wallet.ui.navigation.Route.ChooseVaultType
import com.vultisig.wallet.ui.navigation.Route.FastVaultPasswordReminder
import com.vultisig.wallet.ui.navigation.Route.FastVaultVerification
import com.vultisig.wallet.ui.navigation.Route.ImportVault
import com.vultisig.wallet.ui.navigation.Route.Keygen
import com.vultisig.wallet.ui.navigation.Route.Keysign
import com.vultisig.wallet.ui.navigation.Route.Migration
import com.vultisig.wallet.ui.navigation.Route.Onboarding
import com.vultisig.wallet.ui.navigation.Route.ScanError
import com.vultisig.wallet.ui.navigation.Route.ScanQr
import com.vultisig.wallet.ui.navigation.Route.Secret
import com.vultisig.wallet.ui.navigation.Route.SelectAsset
import com.vultisig.wallet.ui.navigation.Route.SelectNetwork
import com.vultisig.wallet.ui.navigation.Route.Send
import com.vultisig.wallet.ui.navigation.Route.Swap
import com.vultisig.wallet.ui.navigation.Route.TokenDetail
import com.vultisig.wallet.ui.navigation.Route.VaultBackupSummary
import com.vultisig.wallet.ui.navigation.Route.VaultConfirmation
import com.vultisig.wallet.ui.navigation.Route.VaultInfo
import com.vultisig.wallet.ui.navigation.Route.VaultList
import com.vultisig.wallet.ui.navigation.Route.VerifyDeposit
import com.vultisig.wallet.ui.navigation.Route.VerifySend
import com.vultisig.wallet.ui.navigation.Route.VerifySwap
import com.vultisig.wallet.ui.screens.BackupPasswordScreen
import com.vultisig.wallet.ui.screens.ChainSelectionScreen
import com.vultisig.wallet.ui.screens.ChainTokensScreen
import com.vultisig.wallet.ui.screens.ImportFileScreen
import com.vultisig.wallet.ui.screens.OnRampScreen
import com.vultisig.wallet.ui.screens.QrAddressScreen
import com.vultisig.wallet.ui.screens.SecretScreen
import com.vultisig.wallet.ui.screens.ShareVaultQrScreen
import com.vultisig.wallet.ui.screens.TokenDetailScreen
import com.vultisig.wallet.ui.screens.TokenSelectionScreen
import com.vultisig.wallet.ui.screens.VaultDetailScreen
import com.vultisig.wallet.ui.screens.VaultRenameScreen
import com.vultisig.wallet.ui.screens.backup.BackupPasswordRequestScreen
import com.vultisig.wallet.ui.screens.backup.VaultsToBackupScreen
import com.vultisig.wallet.ui.screens.deposit.DepositScreen
import com.vultisig.wallet.ui.screens.deposit.VerifyDepositScreen
import com.vultisig.wallet.ui.screens.home.FastVaultPasswordReminderDialog
import com.vultisig.wallet.ui.screens.home.VaultAccountsScreen
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
import com.vultisig.wallet.ui.screens.referral.ReferralEditExternalScreen
import com.vultisig.wallet.ui.screens.referral.ReferralEditVaultScreen
import com.vultisig.wallet.ui.screens.referral.ReferralOnboardingScreen
import com.vultisig.wallet.ui.screens.referral.ReferralScreen
import com.vultisig.wallet.ui.screens.referral.ReferralVaultListScreen
import com.vultisig.wallet.ui.screens.referral.ReferralViewScreen
import com.vultisig.wallet.ui.screens.reshare.ReshareStartScreen
import com.vultisig.wallet.ui.screens.scan.ScanQrErrorScreen
import com.vultisig.wallet.ui.screens.scan.ScanQrScreen
import com.vultisig.wallet.ui.screens.select.SelectAssetScreen
import com.vultisig.wallet.ui.screens.select.SelectNetworkScreen
import com.vultisig.wallet.ui.screens.send.VerifySendScreen
import com.vultisig.wallet.ui.screens.send.sendScreen
import com.vultisig.wallet.ui.screens.settings.CheckForUpdateScreen
import com.vultisig.wallet.ui.screens.settings.CurrencyUnitSettingScreen
import com.vultisig.wallet.ui.screens.settings.DefaultChainSetting
import com.vultisig.wallet.ui.screens.settings.DiscountTiersScreen
import com.vultisig.wallet.ui.screens.settings.FaqSettingScreen
import com.vultisig.wallet.ui.screens.settings.LanguageSettingScreen
import com.vultisig.wallet.ui.screens.settings.RegisterVaultScreen
import com.vultisig.wallet.ui.screens.settings.SettingsScreen
import com.vultisig.wallet.ui.screens.settings.VultisigTokenScreen
import com.vultisig.wallet.ui.screens.sign.SignMessageScreen
import com.vultisig.wallet.ui.screens.swap.VerifySwapScreen
import com.vultisig.wallet.ui.screens.swap.swapScreen
import com.vultisig.wallet.ui.screens.transaction.AddAddressEntryScreen
import com.vultisig.wallet.ui.screens.transaction.AddressBookBottomSheet
import com.vultisig.wallet.ui.screens.transaction.AddressBookScreen
import com.vultisig.wallet.ui.screens.v2.customtoken.CustomTokenScreen
import com.vultisig.wallet.ui.screens.v2.defi.DefiPositionsScreen
import com.vultisig.wallet.ui.screens.v2.home.bottomsheets.vaultlist.VaultListBottomSheet
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsScreen
import com.vultisig.wallet.ui.screens.vault_settings.components.biometrics.BiometricsEnableScreen
import com.vultisig.wallet.ui.screens.vault_settings.components.delete.ConfirmDeleteScreen
import com.vultisig.wallet.ui.screens.vault_settings.components.security.SecurityScannerEnableScreen
import com.vultisig.wallet.ui.theme.slideInFromBottomEnterTransition
import com.vultisig.wallet.ui.theme.slideInFromEndEnterTransition
import com.vultisig.wallet.ui.theme.slideInFromStartEnterTransition
import com.vultisig.wallet.ui.theme.slideOutToEndExitTransition
import com.vultisig.wallet.ui.theme.slideOutToStartExitTransition
import kotlin.reflect.typeOf

@Suppress("ReplaceNotNullAssertionWithElvisReturn")
@ExperimentalAnimationApi
@Composable
internal fun SetupNavGraph(
    navController: NavHostController,
    startDestination: Any,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = slideInFromEndEnterTransition(),
        exitTransition = slideOutToStartExitTransition(),
        popEnterTransition = slideInFromStartEnterTransition(),
        popExitTransition = slideOutToEndExitTransition(),
    ) {
        composable<Route.Home> {
            VaultAccountsScreen()
        }

        composable<ImportVault> {
            ImportFileScreen()
        }

        dialog<AddChainAccount> {
            ChainSelectionScreen()
        }
        composable<Route.VaultSettings>{
            VaultSettingsScreen()
        }
        composable<Route.Details> {
            VaultDetailScreen(navController)
        }

        composable<Route.Rename> {
            VaultRenameScreen()
        }

        composable<Route.AddVault> {
            StartScreen()
        }
        composable<Route.ChainTokens> {
            ChainTokensScreen(navController)
        }

        composable<Route.PositionTokens> {
            DefiPositionsScreen()
        }

        dialog<TokenDetail> {
            TokenDetailScreen()
        }
        dialog<Route.SelectTokens> {
            TokenSelectionScreen()
        }

        composable<Route.SignMessage>{ entry ->
            val args = entry.toRoute<Route.SignMessage>()

            SignMessageScreen(
                navController = navController,
                vaultId = args.vaultId,
            )
        }

        composable<Route.AddressBookScreen> {
            AddressBookScreen(navController = navController)
        }

        composable<AddressEntry> {
            AddAddressEntryScreen(
                navController = navController,
            )
        }

        composable<Route.Deposit> { entry ->
            val args = entry.toRoute<Route.Deposit>()

            DepositScreen(
                navController = navController,
                vaultId = args.vaultId,
                chainId = args.chainId,
                depositType = args.depositType,
                bondAddress = args.bondAddress,
            )
        }

        composable<Route.Settings> {
            SettingsScreen()
        }

        composable<Route.DefaultChainSetting>{
            DefaultChainSetting(navController = navController)
        }

        composable<Route.FAQSetting> {
            FaqSettingScreen(navController = navController)
        }

        composable<Route.VultisigToken>{
            VultisigTokenScreen(navController = navController)
        }

        composable<Route.DiscountTiers>{
            val vaultId = it.toRoute<Route.DiscountTiers>().vaultId
            DiscountTiersScreen(
                navController = navController,
                vaultId = vaultId
            )
        }

        composable<Route.LanguageSetting>{
            LanguageSettingScreen(navController = navController)
        }

        composable<Route.CurrencyUnitSetting> {
            CurrencyUnitSettingScreen(navController = navController)
        }

        composable<Route.QrAddressScreen> {
            QrAddressScreen(navController = navController)
        }

        composable<Route.ConfirmDelete>{
            ConfirmDeleteScreen(navController)
        }

        composable<Route.ShareVaultQr>{
            ShareVaultQrScreen(
                navController = navController
            )
        }

        dialog<Route.CustomToken>{
            CustomTokenScreen()
        }

        composable<Route.ReshareStartScreen>{
            ReshareStartScreen(navController)
        }

        composable<Route.BiometricsEnable>{
            BiometricsEnableScreen(navController)
        }

        composable<Route.OnChainSecurity>{
            SecurityScannerEnableScreen(navController)
        }

        composable<Route.RegisterVault> {
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

        composable<BackupVault>(
            typeMap = mapOf(
                typeOf<BackupPasswordType>() to BackupPasswordTypeNavType
            )
        ) {
            BackupVaultScreen()
        }

        composable<BackupPasswordRequest>(
            typeMap = mapOf(
                typeOf<BackupType>() to BackupTypeNavType
            )
        ) {
            BackupPasswordRequestScreen()
        }
        composable<Route.BackupPassword>(
            typeMap = mapOf(
                typeOf<BackupType>() to BackupTypeNavType
            )
        ) {
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
        navigation<Send>(
            startDestination = Send.SendMain
        ) {
            sendScreen(
                navController = navController
            )
        }

        composable<VerifySend> {
            VerifySendScreen()
        }

        // swap
        navigation<Swap>(
            startDestination = Swap.SwapMain
        ) {
            swapScreen(
                navController = navController
            )
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

        composable<Route.ReferralOnboarding>{
            ReferralOnboardingScreen(
                navController = navController,
            )
        }

        composable<Route.ReferralListVault>{
            ReferralVaultListScreen(
                navController = navController,
            )
        }

        composable<Route.ReferralExternalEdition> {
            ReferralEditExternalScreen(
                navController = navController,
            )
        }

        composable<Route.ReferralCreation>{
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

        composable<Route.ReferralVaultEdition>{
            ReferralEditVaultScreen(
                navController = navController,
            )
        }

        composable<Route.CheckForUpdateSetting>{
            CheckForUpdateScreen()
        }

        composable<Route.OnRamp> {
            OnRampScreen(
                navController = navController
            )
        }


        dialog<VaultList>(
            typeMap = mapOf(
                typeOf<VaultList.OpenType>() to  VaultListOpenTypeNavType
            )
        ) { backStackEntry ->
            VaultListBottomSheet(
                vaultList = backStackEntry.toRoute<VaultList>(),
                onDismiss = navController::popBackStack
            )
        }

        composable<Route.VaultsToBackup> {
            VaultsToBackupScreen()
        }
    }
}