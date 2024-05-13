package com.vultisig.wallet.ui.navigation

internal sealed class Destination(
    val route: String,
) {

    data class Send(
        val vaultId: String,
        val chainId: String,
    ) : Destination(
        route = "vault_detail/${vaultId}/account/${chainId}/send"
    ) {
        companion object {
            const val ARG_VAULT_ID = "vault_id"
            const val ARG_CHAIN_ID = "chain_id"
            const val staticRoute =
                "vault_detail/{$ARG_VAULT_ID}/account/{$ARG_CHAIN_ID}/send"
        }
    }

    data class SelectTokens(
        val vaultId: String,
        val chainId: String,
    ) : Destination(
        route = "vault_detail/${vaultId}/account/${chainId}/select_tokens"
    ) {
        companion object {
            const val ARG_VAULT_ID = "vault_id"
            const val ARG_CHAIN_ID = "chain_id"
            const val staticRoute =
                "vault_detail/{$ARG_VAULT_ID}/account/{$ARG_CHAIN_ID}/select_tokens"
        }
    }

    data object Back : Destination(
        route = ""
    )

    data object Home : Destination(route = "home_screen")
    data class VaultSettings(val vaultId: String) : Destination(route = "vault_detail/$vaultId/settings") {

        companion object {
            const val ARG_VAULT_ID = "vault_id"
            const val STATIC_ROUTE = "vault_detail/{vault_id}/settings"
        }
    }
    data class Details(val vaultId: String) : Destination(route = "vault_detail/$vaultId/settings/details") {
        companion object {
            const val STATIC_ROUTE = VaultSettings.STATIC_ROUTE + "/details"
        }
    }

    data class Rename(val vaultId: String) : Destination(route = "vault_detail/$vaultId/settings/rename") {
        companion object {
            const val STATIC_ROUTE = VaultSettings.STATIC_ROUTE + "/rename"
        }
    }
    data object Settings : Destination(route = "settings")
    data object DefaultChainSetting : Destination(route = "settings/default_chains")
    data object FAQSetting : Destination(route = "settings/faq")
    data object VultisigToken : Destination(route = "settings/vultisig_token")
    data class LanguageSetting(val langId:Int) : Destination(route = "settings/language/$langId"){
        companion object {
            const val ARG_LANG_ID = "lang_id"
            const val STATIC_ROUTE = "settings/language/{$ARG_LANG_ID}"
        }
    }
    data class CurrencyUnitSetting(val currencyId:Int) : Destination(route = "settings/currency/$currencyId"){
        companion object {
            const val ARG_CURRENCY_ID = "currency_id"
            const val STATIC_ROUTE = "settings/currency/{$ARG_CURRENCY_ID}"
        }
    }

}