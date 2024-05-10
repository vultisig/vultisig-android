package com.vultisig.wallet.ui.navigation

sealed class Screen(val route: String) {
    data object Welcome : Screen(route = "welcome_screen")
    data object Home : Screen(route = "home_screen")

    data object CreateNewVault : Screen(route = "create_new_vault")
    data object JoinKeygen : Screen(route = "join_keygen")
    data object ImportFile : Screen(route = "import_file/{has_file}")
    data object Setup : Screen(route = "setup")
    data object KeygenFlow : Screen(route = "keygen_flow")
    data object SigningError : Screen(route = "signing_error")
    data object VaultDetail : Screen(route = "vault_detail/{vault_name}") {
        fun createRoute(vaultName: String): String {
            return "vault_detail/$vaultName"
        }

        data object AddChainAccount : Screen(route = "vault_detail/{vault_id}/add_account") {
            const val ARG_VAULT_ID = "vault_id"
            fun createRoute(vaultId: String): String {
                return "vault_detail/$vaultId/add_account"
            }
        }

    }

    data object KeysignFlow : Screen(route = "keysign_flow")
    data object JoinKeysign : Screen(route = "join_keysign/{vault_id}"){
        const val ARG_VAULT_ID = "vault_id"
        fun createRoute(vaultId: String): String {
            return "join_keysign/$vaultId"
        }
    }

    data object ChainCoin : Screen(route = "chainCoin/{$CHAIN_COIN_ACCOUNT_JSON}/{$CHAIN_COIN_VAULT_JSON}/{$CHAIN_COIN_COINS_JSON}"){
        fun createRoute(accountJson: String, vaultJson: String, coinsJson:String): String {
            return "chainCoin/$accountJson/$vaultJson/$coinsJson"
        }
    }

    companion object {
        const val CHAIN_COIN_ACCOUNT_JSON = "account_json"
        const val CHAIN_COIN_VAULT_JSON = "vault_json"
        const val CHAIN_COIN_COINS_JSON = "coins_json"
    }

}