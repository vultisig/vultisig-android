package com.vultisig.wallet.ui.navigation

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.models.keygen.VaultSetupType

internal open class Dst(
    val route: String,
)

internal sealed class Destination(
    route: String,
) : Dst(route) {

    companion object {
        const val ARG_VAULT_ID = "vault_id"
        const val ARG_CHAIN_ID = "chain_id"
        const val ARG_TOKEN_ID = "token_id"
        const val ARG_DST_TOKEN_ID = "dst_token_id"
        const val ARG_REQUEST_ID = "request_id"
        const val ARG_QR = "qr"
        const val ARG_VAULT_SETUP_TYPE = "vault_setup_type"
        const val ARG_VAULT_NAME = "vault_name"
        const val ARG_EMAIL = "email"
        const val ARG_PASSWORD = "password"
    }

    data object Welcome : Destination(route = "welcome_screen")

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

    data class Swap(
        val vaultId: String,
        val chainId: String? = null,
        val dstTokenId: String? = null,
    ) : Destination(
        route = buildRoute(vaultId, chainId, dstTokenId)
    ) {
        companion object {
            const val ARG_SELECTED_SRC_TOKEN_ID = "ARG_SELECTED_SRC_TOKEN_ID"
            const val ARG_SELECTED_DST_TOKEN_ID = "ARG_SELECTED_DST_TOKEN_ID"

            val staticRoute = buildRoute(
                "{$ARG_VAULT_ID}",
                "{$ARG_CHAIN_ID}",
                "{$ARG_DST_TOKEN_ID}"
            )

            fun buildRoute(vaultId: String, chainId: String?, dstTokenId: String?) =
                "vault_detail/$vaultId/account/$chainId/swap?$ARG_DST_TOKEN_ID=$dstTokenId"
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

    data object ScanQr : Destination(route = "scan_qr")

    data class JoinThroughQr(
        val vaultId: String?,
    ) : Destination(route = "join/qr?vault_id=$vaultId") {
        companion object {
            const val STATIC_ROUTE = "join/qr?vault_id={$ARG_VAULT_ID}"
        }
    }

    data class ScanError(
        val vaultId: String?,
    ) : Destination(route = "scan_error?vault_id=$vaultId") {
        companion object {
            const val STATIC_ROUTE = "scan_error?vault_id={$ARG_VAULT_ID}"
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

    data object AddAddressEntry : Destination(
        route = "address_book/add"
    ) {
        const val STATIC_ROUTE = "address_book/add"
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
            const val ARG_VAULT_ID = "vault_id"
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

    data object DefaultChainSetting : Destination(route = "settings/default_chains")
    data object FAQSetting : Destination(route = "settings/faq")
    data object VultisigToken : Destination(route = "settings/vultisig_token")
    data object LanguageSetting : Destination(route = "settings/language")
    data object CurrencyUnitSetting : Destination(route = "settings/currency")

    data class NamingVault(
        val vaultSetupType: VaultSetupType,
    ) : Destination(route = "naming_vault/${vaultSetupType.raw}") {
        companion object {
            const val ARG_VAULT_SETUP_TYPE = "vault_setup_type"
            const val STATIC_ROUTE = "naming_vault/{$ARG_VAULT_SETUP_TYPE}"
        }
    }


    data class QrAddressScreen(
        val vaultId: String? = null,
        val address: String,
    ) :
        Destination(route = "vault_details/${vaultId}/qr_address_screen/$address") {
        companion object {
            const val ARG_COIN_ADDRESS = "coin_address"
            const val STATIC_ROUTE =
                "vault_details/{$ARG_VAULT_ID}/qr_address_screen/{$ARG_COIN_ADDRESS}"
        }
    }

    data object SelectVaultType : Destination(route = "setup")

    data class KeygenEmail(
        val name: String,
        val setupType: VaultSetupType,
    ) : Destination(route = buildRoute(name, setupType.raw)) {
        companion object {
            const val STATIC_ROUTE = "keygen/email?${ARG_VAULT_SETUP_TYPE}={$ARG_VAULT_SETUP_TYPE}" +
                    "&${ARG_VAULT_NAME}={$ARG_VAULT_NAME}"

            fun buildRoute(name: String, setupType: Int) =
                "keygen/email?${ARG_VAULT_NAME}=${name}&${ARG_VAULT_SETUP_TYPE}=${setupType}"
        }
    }

    data class KeygenPassword(
        val name: String,
        val setupType: VaultSetupType,
        val email: String,
    ) : Destination(route = buildRoute(name, email, setupType)) {
        companion object {
            const val STATIC_ROUTE = "keygen/password?${ARG_EMAIL}={$ARG_EMAIL}" +
                    "&${ARG_VAULT_SETUP_TYPE}={$ARG_VAULT_SETUP_TYPE}" +
                    "&${ARG_VAULT_NAME}={$ARG_VAULT_NAME}"

            private fun buildRoute(
                name: String,
                email: String,
                setupType: VaultSetupType,
            ) = "keygen/password?${ARG_EMAIL}=$email&${ARG_VAULT_SETUP_TYPE}=${setupType.raw}" +
                    "&${ARG_VAULT_NAME}=$name"

        }
    }

    data class KeygenFlow(
        val vaultName: String?,
        val vaultSetupType: VaultSetupType,
        val isReshare: Boolean,
        val email: String?,
        val password: String?,
    ) : Destination(
        route = buildRoute(
            vaultName,
            vaultSetupType.raw,
            isReshare.toString(),
            email,
            password
        )
    ) {
        companion object {
            const val ARG_VAULT_NAME = "vault_name"
            const val ARG_IS_RESHARE = "is_reshare"

            const val STATIC_ROUTE = "keygen/generate?${ARG_VAULT_NAME}={$ARG_VAULT_NAME}" +
                    "&${ARG_VAULT_SETUP_TYPE}={$ARG_VAULT_SETUP_TYPE}" +
                    "&${ARG_IS_RESHARE}={$ARG_IS_RESHARE}" +
                    "&${ARG_EMAIL}={$ARG_EMAIL}" +
                    "&${ARG_PASSWORD}={$ARG_PASSWORD}"

            private fun buildRoute(
                name: String?,
                type: Int,
                isReshare: String,
                email: String?,
                password: String?,
            ) = "keygen/generate?${ARG_VAULT_NAME}=$name&${ARG_VAULT_SETUP_TYPE}=${type}" +
                    "&${ARG_IS_RESHARE}=$isReshare&${ARG_EMAIL}=$email&${ARG_PASSWORD}=$password"

        }
    }

    data class JoinKeysign(
        val vaultId: String,
        val qr: String,
    ) : Destination(route = "join_keysign/$vaultId?qr=$qr") {

        companion object {
            const val STATIC_ROUTE = "join_keysign/{$ARG_VAULT_ID}?qr={$ARG_QR}"
        }

    }

    data class JoinKeygen(
        val qr: String,
        val isReshare: Boolean,
    ) : Destination(route = "join_keygen?qr=$qr") {

        companion object {
            const val STATIC_ROUTE = "join_keygen?qr={$ARG_QR}"
        }

    }


    data class BackupPassword(val vaultId: String) :
        Destination(route = "backup_password/$vaultId") {
        companion object {
            const val ARG_VAULT_ID = "vault_id"
            const val STATIC_ROUTE = "backup_password/{$ARG_VAULT_ID}"
        }
    }

    data class BackupSuggestion(val vaultId: String) :
        Destination(route = "backup_suggestion/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "backup_suggestion/{$ARG_VAULT_ID}"
        }
    }

    data class ShareVaultQr(val vaultId: String) :
        Destination(route = "share_vault_qr/$vaultId") {
        companion object {
            const val ARG_VAULT_ID = "vault_id"
            const val STATIC_ROUTE = "share_vault_qr/{$ARG_VAULT_ID}"
        }
    }

    data object ImportVault : Destination(route = "import_file")

    data class AddChainAccount(val vaultId: String) :
        Destination(route = "vault_detail/$vaultId/add_account")

    data class ReshareStartScreen(val vaultId: String) :
        Destination(route = "reshare_start_screen/$vaultId") {
        companion object {
            const val STATIC_ROUTE = "reshare_start_screen/{$ARG_VAULT_ID}"
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