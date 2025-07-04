package com.vultisig.wallet.ui.navigation

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.VaultId
import kotlinx.serialization.Serializable

internal open class Dst(
    val route: String,
)

internal sealed class Destination(
    route: String,
) : Dst(route) {

    companion object {
        const val ARG_VAULT_ID = "vault_id"
        const val ARG_CHAIN_ID = "chain_id"
        const val ARG_ADDRESS = "address"
        const val ARG_TOKEN_ID = "token_id"
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

    data class TokenDetail(
        val vaultId: String,
        val chainId: String,
        val tokenId: String,
    ) : Destination(
        route = "vault_detail/${vaultId}/account/${chainId}/${tokenId}"
    ) {
        companion object {
            const val STATIC_ROUTE =
                "vault_detail/{$ARG_VAULT_ID}/account/{$ARG_CHAIN_ID}/{$ARG_TOKEN_ID}"
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

    data class SelectTokens(
        val vaultId: String,
        val chainId: String,
    ) : Destination(
        route = "vault_detail/${vaultId}/account/${chainId}/select_tokens"
    ) {
        companion object {
            const val STATIC_ROUTE =
                "vault_detail/{$ARG_VAULT_ID}/account/{$ARG_CHAIN_ID}/select_tokens"
        }
    }

    data class SelectToken(
        val vaultId: String,
        val targetArg: String,
        val swapSelect: Boolean = false,
    ) : Destination(
        route = "select_token?${ARG_VAULT_ID}=$vaultId&${ARG_TARGET_ARG}=$targetArg" +
                "&${ARG_SWAP_SELECT}=$swapSelect"
    ) {
        companion object {
            const val ARG_SELECTED_TOKEN_ID = "arg_selected_token_id"
            const val ARG_TARGET_ARG = "target_arg"
            const val ARG_SWAP_SELECT = "swap_select"

            const val STATIC_ROUTE =
                "select_token?$ARG_VAULT_ID={$ARG_VAULT_ID}&$ARG_TARGET_ARG={$ARG_TARGET_ARG}" +
                        "&$ARG_SWAP_SELECT={$ARG_SWAP_SELECT}"
        }
    }

    data class AddressBook(
        val chain: Chain? = null,
        val requestId: String? = null,
    ) : Destination(
        route = "address_book?$ARG_REQUEST_ID=$requestId&$ARG_CHAIN_ID=${chain?.id}"
    ) {
        companion object {
            const val STATIC_ROUTE =
                "address_book?$ARG_REQUEST_ID={$ARG_REQUEST_ID}&$ARG_CHAIN_ID={$ARG_CHAIN_ID}"
        }
    }

    data class AddressEntry(
        val chainId: String? = null,
        val address: String? = null,
    ) : Destination(route = "address_book/entry?$ARG_CHAIN_ID=$chainId&$ARG_ADDRESS=$address") {
        companion object {
            const val STATIC_ROUTE =
                "address_book/entry?$ARG_CHAIN_ID={$ARG_CHAIN_ID}&$ARG_ADDRESS={$ARG_ADDRESS}"
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

    data class Rename(val vaultId: String) :
        Destination(route = "vault_detail/$vaultId/settings/rename") {
        companion object {
            const val STATIC_ROUTE = VaultSettings.STATIC_ROUTE + "/rename"
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
    data object LanguageSetting : Destination(route = "settings/language")
    data object CurrencyUnitSetting : Destination(route = "settings/currency")

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

    data class Folder(val folderId: String) : Destination(route = "folder/$folderId") {
        companion object {
            const val ARG_FOLDER_ID = "folder_id"
            const val STATIC_ROUTE = "folder/{$ARG_FOLDER_ID}"
        }
    }

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

    internal data class CustomToken(val chainId: String) :
        Destination(route = "custom_token/$chainId") {
        companion object {
            const val ARG_CHAIN_ID = "chain_id"
            const val STATIC_ROUTE = "custom_token/{$ARG_CHAIN_ID}"
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


    // transactions

    // select asset / network
    @Serializable
    data class SelectAsset(
        val vaultId: VaultId,
        val preselectedNetworkId: ChainId,
        val networkFilters: SelectNetwork.Filters,
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
    )

    @Serializable
    data class VerifySwap(
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
        // vault type only provided if vault confirmation screen is required
        val vaultType: VaultInfo.VaultType? = null,
        val action: TssAction? = null,
    )

    @Serializable
    data class BackupPassword(
        val vaultId: VaultId,
        // vault type only provided if vault confirmation screen is required
        val vaultType: VaultInfo.VaultType? = null,
        val action: TssAction? = null,
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

}