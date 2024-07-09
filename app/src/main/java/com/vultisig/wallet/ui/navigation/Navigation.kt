package com.vultisig.wallet.ui.navigation

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
            const val staticRoute =
                "vault_detail/{$ARG_VAULT_ID}/account/{$ARG_CHAIN_ID}"
        }
    }

    data class Send(
        val vaultId: String,
        val chainId: String? = null,
        val address: String? = null,
    ) : Destination(
        route = "vault_detail/${vaultId}/account/${chainId}/send?qr=${address}"
    ) {
        companion object {
            const val staticRoute =
                "vault_detail/{$ARG_VAULT_ID}/account/{$ARG_CHAIN_ID}/send?qr={$ARG_QR}"
        }
    }

    data class Swap(
        val vaultId: String,
        val chainId: String? = null,
    ) : Destination(
        route = buildRoute(vaultId, chainId)
    ) {
        companion object {
            const val ARG_SELECTED_SRC_TOKEN_ID = "ARG_SELECTED_SRC_TOKEN_ID"
            const val ARG_SELECTED_DST_TOKEN_ID = "ARG_SELECTED_DST_TOKEN_ID"

            val staticRoute = buildRoute("{$ARG_VAULT_ID}", "{$ARG_CHAIN_ID}")

            fun buildRoute(vaultId: String, chainId: String?) =
                "vault_detail/$vaultId/account/$chainId/swap"
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
            const val staticRoute =
                "vault_detail/{$ARG_VAULT_ID}/account/{$ARG_CHAIN_ID}/select_tokens"
        }
    }

    data class SelectToken(
        val vaultId: String,
        val targetArg: String,
    ) : Destination(
        route = "select_token?${ARG_VAULT_ID}=$vaultId&${ARG_TARGET_ARG}=$targetArg"
    ) {
        companion object {
            const val ARG_SELECTED_TOKEN_ID = "arg_selected_token_id"
            const val ARG_TARGET_ARG = "target_arg"

            const val staticRoute =
                "select_token?$ARG_VAULT_ID={$ARG_VAULT_ID}&$ARG_TARGET_ARG={$ARG_TARGET_ARG}"
        }
    }

    data object ScanQr : Destination(route = "scan_qr")

    data class JoinThroughQr(
        val vaultId: String?,
    ) : Destination(route = "join/qr?vault_id=$vaultId") {
        companion object {
            const val staticRoute = "join/qr?vault_id={$ARG_VAULT_ID}"
        }
    }


    data object Back : Destination(
        route = ""
    )

    data class Home(
        val openVaultId: String? = null,
    ) : Destination(
        route = buildRoute(openVaultId)
    ) {
        companion object {
            val staticRoute = buildRoute(
                "{$ARG_VAULT_ID}",
            )

            private fun buildRoute(
                vaultId: String?,
            ) = "home?vault_id=$vaultId"
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
    data class NamingVault(val vaultSetupType: VaultSetupType) :
        Destination(route = "naming_vault/${vaultSetupType.raw}") {
        companion object {
            const val ARG_VAULT_SETUP_TYPE = "vault_setup_type"
            const val STATIC_ROUTE = "naming_vault/{$ARG_VAULT_SETUP_TYPE}"
        }
    }


    data class QrAddressScreen(val address: String) :
        Destination(route = "vault_details/qr_address_screen/$address") {
        companion object {
            const val ARG_COIN_ADDRESS = "coin_address"
            const val STATIC_ROUTE = "vault_details/qr_address_screen/{$ARG_COIN_ADDRESS}"
        }
    }

    data class KeygenFlow(val vaultName: String, val vaultSetupType: VaultSetupType) :
        Destination(route = "keygen_flow/$vaultName/${vaultSetupType.raw}") {
        companion object {
            const val STATIC_ROUTE = "keygen_flow/{vault_name}/{vault_type}"
            const val ARG_VAULT_NAME = "vault_name"
            const val ARG_VAULT_TYPE = "vault_type"
            const val DEFAULT_NEW_VAULT = "*vultisig_new_vault*"
        }
    }

    data class JoinKeysign(
        val vaultId: String,
        val qr: String,
    ) : Destination(route = "join_keysign/$vaultId?qr=$qr") {

        companion object {
            const val staticRoute = "join_keysign/{$ARG_VAULT_ID}?qr={$ARG_QR}"
        }

    }

    data class JoinKeygen(
        val qr: String,
    ) : Destination(route = "join_keygen?qr=$qr") {

        companion object {
            const val staticRoute = "join_keygen?qr={$ARG_QR}"
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
            const val staticRoute = "backup_suggestion/{$ARG_VAULT_ID}"
        }
    }

    data object CreateNewVault : Destination(
        route = "create_new_vault"
    )
}