package com.vultisig.wallet.ui.navigation

import android.os.Bundle
import androidx.navigation.NavType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.SendDeeplinkData
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.navigation.Route.*
import com.vultisig.wallet.ui.navigation.Route.SelectNetwork.Filters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal open class Dst(
    val route: String,
)

internal sealed class Destination(
    route: String,
) : Dst(route) {

    companion object {
        const val ARG_EXPIRATION_ID = "expiration_id"
        const val ARG_REFERRAL_ID = "referral_id"
        const val ARG_VAULT_ID = "vault_id"
        const val ARG_CHAIN_ID = "chain_id"
        const val ARG_ADDRESS = "address"
        const val ARG_TOKEN_ID = "token_id"
        const val ARG_MERGE_ID = "merge_id"
        const val ARG_REQUEST_ID = "request_id"
        const val ARG_QR = "qr"
    }

    data object AddVault : Destination(
        route = "vault/new"
    )

    data class ChainTokens(
        val vaultId: String,
        val chainId: String,
    ) : Destination(
        route = "vault_detail/${vaultId}/account/${chainId}"
    ) {
        companion object {
            const val STATIC_ROUTE =
                "vault_detail/{$ARG_VAULT_ID}/account/{$ARG_CHAIN_ID}"
        }
    }

    data class PositionTokens(
        val vaultId: String,
    ): Destination (
        route = "position_detail/${vaultId}"
    ) {
        companion object {
            const val STATIC_ROUTE =
                "position_detail/{$ARG_VAULT_ID}"
        }
    }

    data class Send(
        val vaultId: String,
        val chainId: String? = null,
        val tokenId: String? = null,
        val address: String? = null,
    ) : Destination(
        route = "vault_detail/${vaultId}/account/${chainId}/send?qr=${address}&$ARG_TOKEN_ID=${tokenId}"
    ) {
        companion object {
            const val STATIC_ROUTE =
                "vault_detail/{$ARG_VAULT_ID}/account/{$ARG_CHAIN_ID}/send?qr={$ARG_QR}&$ARG_TOKEN_ID={$ARG_TOKEN_ID}"
        }
    }

    data class Deposit(
        val vaultId: String,
        val chainId: String,
    ) : Destination(
        route = buildRoute(vaultId, chainId)
    ) {
        companion object {
            val staticRoute = buildRoute("{$ARG_VAULT_ID}", "{$ARG_CHAIN_ID}")

            fun buildRoute(vaultId: String, chainId: String?) =
                "vault_detail/$vaultId/account/$chainId/deposit"
        }
    }

    data class SignMessage(
        val vaultId: String,
    ) : Destination(
        route = "vault_detail/${vaultId}/sign_message"
    ) {
        companion object {
            const val STATIC_ROUTE =
                "vault_detail/{$ARG_VAULT_ID}/sign_message"
        }
    }

    data class AddressBook(
        val chain: Chain? = null,
        val requestId: String? = null,
        val vaultId: String,
    ) : Destination(
        route = "address_book?$ARG_REQUEST_ID=$requestId&$ARG_CHAIN_ID=${chain?.id}&$ARG_VAULT_ID=$vaultId"
    ) {
        companion object {
            const val STATIC_ROUTE =
                "address_book?$ARG_REQUEST_ID={$ARG_REQUEST_ID}&$ARG_CHAIN_ID={$ARG_CHAIN_ID}&$ARG_VAULT_ID={$ARG_VAULT_ID}"
        }
    }


    data object Back : Destination(
        route = "back"
    )

    data class Home(
        val openVaultId: String? = null,
        val showVaultList: Boolean = false,
    ) : Destination(
        route = "home?vault_id=$openVaultId&show_vault_list=$showVaultList"
    ) {
        companion object {
            const val ARG_SHOW_VAULT_LIST = "show_vault_list"
            const val STATIC_ROUTE =
                "home?vault_id={$ARG_VAULT_ID}&show_vault_list={$ARG_SHOW_VAULT_LIST}"
        }
    }

    data class VaultSettings(val vaultId: String) :
        Destination(route = "vault_detail/$vaultId/settings") {

        companion object {
            const val STATIC_ROUTE = "vault_detail/{vault_id}/settings"
        }
    }

    data class ConfirmDelete(val vaultId: String) :
        Destination(route = "vault_detail/confirm_delete/$vaultId") {
        companion object {
            const val ARG_VAULT_ID = "vault_id"
            const val STATIC_ROUTE = "vault_detail/confirm_delete/{vault_id}"
        }
    }

    data class Details(val vaultId: String) :
        Destination(route = "vault_detail/$vaultId/settings/details") {
        companion object {
            const val STATIC_ROUTE = VaultSettings.STATIC_ROUTE + "/details"
        }
    }

    data class Settings(val vaultId: String) : Destination(route = "settings/$vaultId") {
        companion object {
            const val ARG_VAULT_ID = "vault_id"
            const val STATIC_ROUTE = "settings/{$ARG_VAULT_ID}"
        }
    }

    data class RegisterVault(
        val vaultId: String,
    ) : Destination(route = "settings/register_vault/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "settings/register_vault/{$ARG_VAULT_ID}"
        }
    }

    data object DefaultChainSetting : Destination(route = "settings/default_chains")
    data object FAQSetting : Destination(route = "settings/faq")
    data object VultisigToken : Destination(route = "settings/vultisig_token")
    data class DiscountTiers(
        val vaultId: String
    ) : Destination(route = "settings/discount_tiers/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "settings/discount_tiers/{$ARG_VAULT_ID}"
        }
    }

    data object LanguageSetting : Destination(route = "settings/language")
    data object CurrencyUnitSetting : Destination(route = "settings/currency")

    data object CheckForUpdateSetting : Destination(route = "settings/check_for_update")

    data class ReferralListVault(
        val vaultId: String,
    ) : Destination(route = "referral/vaultlist/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "referral/vaultlist/{$ARG_VAULT_ID}"
        }
    }

    data class ReferralOnboarding(
        val vaultId: String,
    ) : Destination(route = "referral/onboarding/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "referral/onboarding/{$ARG_VAULT_ID}"
        }
    }

    data class ReferralCode(
        val vaultId: String
    ) : Destination(route = "referral/referral_screen/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "referral/referral_screen/{$ARG_VAULT_ID}"
        }
    }

    data class ReferralCreation(
        val vaultId: String,
    ) : Destination(route = "referral/referral_creation/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "referral/referral_creation/{$ARG_VAULT_ID}"
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

    data class ReferralVaultEdition(
        val vaultId: String,
        val code: String,
        val expiration: String,
    ) : Destination(route = "referral/referral_edition/$vaultId/$code/$expiration") {
        companion object {
            const val STATIC_ROUTE =
                "referral/referral_edition/{$ARG_VAULT_ID}/{$ARG_REFERRAL_ID}/{$ARG_EXPIRATION_ID}"
        }
    }

    data class ReferralExternalEdition(
        val vaultId: String,
    ) : Destination(route = "referral/referral_external_edition/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "referral/referral_external_edition/{$ARG_VAULT_ID}"
        }
    }

    data class QrAddressScreen(
        val vaultId: String? = null,
        val address: String,
        val chainName: String,
    ) :
        Destination(
            route = "vault_details/${vaultId}/qr_address_screen/$address" +
                    "?${ARG_CHAIN_NAME}=${chainName}"
        ) {
        companion object {
            const val ARG_COIN_ADDRESS = "coin_address"
            const val ARG_CHAIN_NAME = "chain_name"
            const val STATIC_ROUTE =
                "vault_details/{$ARG_VAULT_ID}/qr_address_screen/{$ARG_COIN_ADDRESS}" +
                        "?${ARG_CHAIN_NAME}={$ARG_CHAIN_NAME}"
        }
    }

    data class JoinKeygen(
        val qr: String,
        val isReshare: Boolean,
    ) : Destination(route = "join_keygen?qr=$qr")

    data class ShareVaultQr(val vaultId: String) :
        Destination(route = "share_vault_qr/$vaultId") {
        companion object {
            const val ARG_VAULT_ID = "vault_id"
            const val STATIC_ROUTE = "share_vault_qr/{$ARG_VAULT_ID}"
        }
    }

    data object CreateFolder : Destination(route = "create_folder")

    data class ReshareStartScreen(val vaultId: String) :
        Destination(route = "reshare_start_screen/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "reshare_start_screen/{$ARG_VAULT_ID}"
        }
    }

    data class BiometricsEnable(val vaultId: String) :
        Destination(route = "biometrics/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "biometrics/{$ARG_VAULT_ID}"
        }
    }

    data object OnChainSecurity : Destination(route = "onchain_security")

    data class OnRamp(val vaultId: String, val chainId: String) :
        Destination(route = "onramp/$vaultId/$chainId") {
        companion object {
            const val STATIC_ROUTE = "onramp/{$ARG_VAULT_ID}/{$ARG_CHAIN_ID}"
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
        val address: String? = null,
        val amount: String? = null,
        val memo: String? = null,
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

    object VaultInfo {

        @Serializable
        enum class VaultType {
            Fast, Secure
        }

        // required by both vault types
        @Serializable
        data class Name(
            val vaultType: VaultType,
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
    )

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
        val vaultId: String
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



internal val BackupTypeNavType = object : NavType<BackupType>(
    isNullableAllowed = false
) {
    override fun put(bundle: Bundle, key: String, value: BackupType) {
        bundle.putString(key, Json.encodeToString(value))
    }

    override fun get(bundle: Bundle, key: String): BackupType {
        return Json.decodeFromString(bundle.getString(key)!!)
    }

    override fun parseValue(value: String): BackupType {
        return Json.decodeFromString(value)
    }

    override fun serializeAsValue(value: BackupType): String {
        return Json.encodeToString(value)
    }
}


internal val VaultListOpenTypeNavType = object : NavType<VaultList.OpenType>(
    isNullableAllowed = false
) {
    override fun put(bundle: Bundle, key: String, value: VaultList.OpenType) {
        bundle.putString(key, Json.encodeToString(value))
    }

    override fun get(bundle: Bundle, key: String): VaultList.OpenType {
        return Json.decodeFromString(bundle.getString(key)!!)
    }

    override fun parseValue(value: String): VaultList.OpenType {
        return Json.decodeFromString(value)
    }

    override fun serializeAsValue(value: VaultList.OpenType): String {
        return Json.encodeToString(value)
    }
}