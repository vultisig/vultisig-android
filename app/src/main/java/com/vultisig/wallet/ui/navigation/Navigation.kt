package com.vultisig.wallet.ui.navigation

import android.os.Bundle
import androidx.navigation.NavType
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.SendDeeplinkData
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.navigation.Route.BackupVault
import com.vultisig.wallet.ui.navigation.Route.SelectNetwork.Filters
import com.vultisig.wallet.ui.navigation.Route.VaultInfo
import com.vultisig.wallet.ui.navigation.Route.VaultList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal open class Dst(
    val route: String,
)

internal sealed class Destination(
    route: String,
) : Dst(route) {

    companion object {
        const val ARG_REFERRAL_ID = "referral_id"
        const val ARG_VAULT_ID = "vault_id"
    }

    data object Back : Destination(
        route = "back"
    )


    data class ReferralCode(
        val vaultId: String,
    ) : Destination(route = "referral/referral_screen/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "referral/referral_screen/{$ARG_VAULT_ID}"
        }
    }

    data class ReferralView(
        val vaultId: String,
        val code: String,
    ) : Destination(route = "referral/referral_view/$vaultId/$code") {
        companion object {
            const val STATIC_ROUTE = "referral/referral_view/{$ARG_VAULT_ID}/{$ARG_REFERRAL_ID}"
        }
    }
}

internal sealed class Route {

    data object Onboarding {
        @Serializable
        data object VaultCreation

        @Serializable
        data object VaultCreationSummary

        @Serializable
        data class VaultBackup(
            val vaultId: VaultId,
            val pubKeyEcdsa: String,
            val email: String?,
            val vaultType: VaultInfo.VaultType,
            val action: TssAction,
            val vaultName: String,
            val password: String?,
            val deviceCount: Int?,
        )
    }

    @Serializable
    data object Secret : Route()

    // home

    @Serializable
    data class FastVaultPasswordReminder(
        val vaultId: VaultId,
    )


    // scan

    @Serializable
    data class ScanQr(
        // non null if used for join flow
        val vaultId: VaultId? = null,
        // non null if used for respond flow
        val requestId: String? = null,
    )

    @Serializable
    data object ScanError


    // select asset / network
    @Serializable
    data class SelectAsset(
        val vaultId: VaultId,
        val preselectedNetworkId: ChainId,
        val networkFilters: Filters,
        val requestId: String,
    )

    @Serializable
    data class SelectNetwork(
        val vaultId: VaultId,
        val selectedNetworkId: ChainId,
        val requestId: String,
        val filters: Filters,
        val consolidateEvm: Boolean = false,
        val showAllChains: Boolean = false,
    ) {
        @Serializable
        enum class Filters {
            SwapAvailable,
            DisableNetworkSelection,
            None,
        }
    }

    // send
    @Serializable
    data class Send(
        val vaultId: VaultId,
        val chainId: ChainId? = null,
        val tokenId: TokenId? = null,
        val type: String? = null,
        val address: String? = null,
        val amount: String? = null,
        val memo: String? = null,
        val mscaAddress: String? = null,
    ) {

        @Serializable
        object SendMain
    }

    @Serializable
    data class SelectNetworkPopup(
        val pressX: Float = 0f,
        val pressY: Float = 0f,
        val vaultId: VaultId,
        val selectedNetworkId: ChainId,
        val requestId: String,
        val filters: Filters,
    )

    @Serializable
    data class SelectAssetPopup(
        val vaultId: VaultId,
        val preselectedNetworkId: ChainId,
        val selectedAssetId: String,
        val networkFilters: Filters,
        val requestId: String,
        val pressX: Float = 0f,
        val pressY: Float = 0f,
    )

    @Serializable
    data class VerifySend(
        val vaultId: VaultId,
        val transactionId: TransactionId,
    )

    // swap

    @Serializable
    data class Swap(
        val vaultId: VaultId,
        val chainId: ChainId? = null,
        val srcTokenId: TokenId? = null,
        val dstTokenId: TokenId? = null,
    ) {
        @Serializable
        object SwapMain
    }

    @Serializable
    data class VerifySwap(
        val vaultId: VaultId,
        val transactionId: TransactionId,
    )

    @Serializable
    data class VerifyDeposit(
        val vaultId: VaultId,
        val transactionId: TransactionId,
    )

    // keysign

    data object Keysign {

        @Serializable
        data class Join(
            val vaultId: String,
            val qr: String,
        )

        @Serializable
        data class Password(
            val transactionId: TransactionId,
            val vaultId: VaultId,
            val txType: Keysign.TxType,
        )

        @Serializable
        data class Keysign(
            val transactionId: TransactionId,
            val password: String? = null,
            val txType: TxType,
        ) {
            @Serializable
            enum class TxType {
                Send, Swap, Deposit, Sign
            }
        }
    }

    // vault creation / keygen
    @Serializable
    data class ImportVault(
        val uri: String? = null,
    )

    @Serializable
    data object ChooseVaultType

    data object KeyImport {
        @Serializable
        data object ImportSeedphrase

        @Serializable
        data object ChainsSetup

        @Serializable
        data object DeviceCount
    }

    object VaultInfo {

        @Serializable
        enum class VaultType {
            Fast, Secure
        }

        // required by both vault types
        @Serializable
        data class Name(
            val vaultType: VaultType,
            val tssAction: TssAction = TssAction.KEYGEN,
        )

        // required only by fast vault
        @Serializable
        data class Email(
            val name: String,
            val action: TssAction,
            val vaultId: VaultId? = null,
            // if password is not null, then it's migration flow
            val password: String? = null,
        )

        @Serializable
        data class Password(
            val name: String,
            val email: String,
            val tssAction: TssAction,
            val vaultId: VaultId? = null,
        )

        @Serializable
        data class PasswordHint(
            val name: String,
            val email: String,
            val password: String,
            val tssAction: TssAction,
            val vaultId: VaultId? = null,
        )
    }

    @Serializable
    data object Keygen {

        @Serializable
        data class Join(
            val qr: String,
        )

        @Serializable
        data class PeerDiscovery(
            val action: TssAction,
            val vaultName: String,

            val email: String? = null,
            val password: String? = null,
            val hint: String? = null,
            val deviceCount: Int? = null,

            // reshare
            val vaultId: VaultId? = null,
        )

        @Serializable
        data class Generating(
            val action: TssAction,
            val sessionId: String,
            val serverUrl: String,
            val localPartyId: String,
            val vaultName: String,
            val hexChainCode: String,
            val keygenCommittee: List<String>,
            val encryptionKeyHex: String,
            val isInitiatingDevice: Boolean,
            val libType: SigningLibType,

            // reshare
            val vaultId: VaultId? = null,
            val oldCommittee: List<String>,
            val oldResharePrefix: String,

            val email: String?,
            val password: String?,
            val hint: String?,
            val deviceCount: Int?,
        )

    }

    @Serializable
    data class FastVaultVerification(
        val vaultId: VaultId,
        val pubKeyEcdsa: String,
        val email: String,
        val tssAction: TssAction,
        val vaultName: String,
        val password: String?,
    )

    @Serializable
    data class BackupVault(
        val vaultId: VaultId,
        val vaultType: VaultInfo.VaultType?,
        val action: TssAction? = null,
        val passwordType: BackupPasswordType,
    ) {

        @Serializable
        sealed interface BackupPasswordType {
            @Serializable
            data class VultiServerPassword(
                val password: String?,
            ) : BackupPasswordType

            @Serializable
            data object UserSelectionPassword : BackupPasswordType
        }
    }

    @Serializable
    data class BackupPasswordRequest(
        val vaultId: VaultId,
        val backupType: BackupType = BackupType.CurrentVault(),
    )

    @Serializable
    data class VaultsToBackup(
        val vaultId: VaultId,
    )

    @Serializable
    data class BackupPassword(
        val vaultId: VaultId,
        val backupType: BackupType = BackupType.CurrentVault(),
    )

    @Serializable
    data class VaultBackupSummary(
        val vaultId: VaultId,
        val vaultType: VaultInfo.VaultType,
    )

    @Serializable
    data class VaultConfirmation(
        val vaultId: VaultId,
        val vaultType: VaultInfo.VaultType,
        val action: TssAction? = null,
    )

    // vault migration
    object Migration {
        @Serializable
        data class Onboarding(
            val vaultId: VaultId,
        )

        @Serializable
        data class Password(
            val vaultId: VaultId,
        )
    }

    // address book
    @Serializable
    data class AddressBook(
        val requestId: String,
        val chainId: ChainId,
        val excludeVaultId: VaultId,
    )

    @Serializable
    data class TokenDetail(
        val vaultId: String,
        val chainId: String,
        val tokenId: String,
        val mergeId: String,
    )

    @Serializable
    data class AddChainAccount(
        val vaultId: String,
        val routeFromInitVault: Boolean = false,
    )

    @Serializable
    data class AddDeFiChainAccount(
        val vaultId: String,
    )

    @Serializable
    data class VaultList(
        val openType: OpenType,
    ){
        @Serializable
        sealed interface OpenType {
            @Serializable
            data class DeepLink(
                val sendDeepLinkData: SendDeeplinkData,
            ): OpenType

            @Serializable
            data class Home (val vaultId: VaultId): OpenType
        }
    }


    @Serializable
    data class FolderList(
        val folderId: String,
        val vaultId: VaultId,
    )


    @Serializable
    data class CreateFolder(
        val folderId: String?,
    )

    @Serializable
    data class AddressEntry(
        val chainId: String? = null,
        val address: String? = null,
        val vaultId: String,
    )

    @Serializable
    data class SelectTokens(
        val vaultId: String,
        val chainId: String,
    )

    @Serializable
    data class CustomToken(
        val chainId: String,
    )

    @Serializable
    data class Rename(
        val vaultId: String,
    )

    @Serializable
    data class AddressQr(
        val vaultId: VaultId,
        val address: String,
        val name: String,
        val logo: Int?,
    )

    @Serializable
    data class Receive(
        val vaultId: String,
    )

    @Serializable
    data class Home(
        val openVaultId: String? = null,
        val showVaultList: Boolean = false,
    )

    @Serializable
    data object AddVault

    @Serializable
    data class VaultSettings(val vaultId: String)

    @Serializable
    data class Details(val vaultId: String)



    @Serializable
    data class SignMessage(
        val vaultId: String,
    )

    @Serializable
    data class Deposit(
        val vaultId: String,
        val chainId: String,
        val depositType: String? = null,
        val bondAddress: String? = null,
    )

    @Serializable
    data class Settings(val vaultId: String)

    @Serializable
    data object DefaultChainSetting

    @Serializable
    data object FAQSetting

    @Serializable
    data class DiscountTiers(
        val vaultId: String,
    )

    @Serializable
    data object LanguageSetting

    @Serializable
    data object CurrencyUnitSetting

    @Serializable
    data class QrAddressScreen(
        val vaultId: String? = null,
        val address: String,
        val chainName: String,
    )

    @Serializable
    data class ConfirmDelete(val vaultId: String)

    @Serializable
    data class ShareVaultQr(val vaultId: String)

    @Serializable
    data class ReshareStartScreen(val vaultId: String)

    @Serializable
    data class BiometricsEnable(val vaultId: String)

    @Serializable
    data object OnChainSecurity


    @Serializable
    data class ReferralOnboarding(
        val vaultId: String,
    )

    @Serializable
    data object CheckForUpdateSetting

    @Serializable
    data class ReferralVaultEdition(
        val vaultId: String,
        val code: String,
        val expiration: String,
    )

    @Serializable
    data class ReferralCreation(
        val vaultId: String,
    )

    @Serializable
    data class ReferralExternalEdition(
        val vaultId: String,
    )

    @Serializable
    data class ReferralListVault(
        val vaultId: String,
    )

    @Serializable
    data class OnRamp(val vaultId: String, val chainId: String)

    @Serializable
    data class AddressBookScreen(
        val chainId: String? = null,
        val requestId: String? = null,
        val vaultId: String,
    )

    @Serializable
    data class ChainDashboard(
        val route: ChainDashboardRoute
    )


    @Serializable
    data class EnterVaultInfo(
        val count: Int
    )


    @Serializable
    data class SetupVaultInfo(
        val count: Int
    )

    @Serializable
    data object ChooseVaultCount

    @Serializable
    data class ReviewVaultDevices(
        val vaultId: VaultId,
        val pubKeyEcdsa: String,
        val email: String?,
        val vaultType: VaultInfo.VaultType,
        val action: TssAction,
        val vaultName: String,
        val password: String?,
        val devices: List<String>?,
        val localPartyId: String?,
    )


}

@Serializable
internal sealed interface BackupType {
    @Serializable
    data class CurrentVault(
        val vaultType: VaultInfo.VaultType? = null,
        val action: TssAction? = null,
    ) : BackupType

    @Serializable
    data object AllVaults : BackupType
}

@Serializable
sealed interface ChainDashboardRoute {
    @Serializable
    data class Wallet(
        val vaultId: String,
        val chainId: String,
    ) : ChainDashboardRoute

    @Serializable
    data class PositionTokens(
        val vaultId: String,
    ) : ChainDashboardRoute

    @Serializable
    data class PositionCircle(
        val vaultId: String,
    ) : ChainDashboardRoute
}




internal val BackupTypeNavType = createNavType<BackupType>()

internal val VaultListOpenTypeNavType = createNavType<VaultList.OpenType>()

internal val BackupPasswordTypeNavType = createNavType<BackupVault.BackupPasswordType>()

internal val ChainDashboardRouteNavType = createNavType<ChainDashboardRoute>()

private inline fun <reified T> createNavType(
    isNullableAllowed: Boolean = false
): NavType<T> = object : NavType<T>(isNullableAllowed = isNullableAllowed) {
    override fun put(bundle: Bundle, key: String, value: T) {
        bundle.putString(key, Json.encodeToString(value))
    }

    override fun get(bundle: Bundle, key: String): T {
        return Json.decodeFromString(bundle.getString(key)!!)
    }

    override fun parseValue(value: String): T {
        return Json.decodeFromString(value)
    }

    override fun serializeAsValue(value: T): String {
        return Json.encodeToString(value)
    }
}