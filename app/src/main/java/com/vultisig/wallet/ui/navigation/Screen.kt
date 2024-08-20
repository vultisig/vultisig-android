package com.vultisig.wallet.ui.navigation

sealed class Screen(val route: String) {
    data object Welcome : Screen(route = "welcome_screen")
    data object Home : Screen(route = "home")

    data object CreateNewVault : Screen(route = "create_new_vault")
    data object ImportFile : Screen(route = "import_file")

    data object SigningError : Screen(route = "signing_error")

    data object AddChainAccount : Screen(route = "vault_detail/{vault_id}/add_account") {
        const val ARG_VAULT_ID: String = "vault_id"
        fun createRoute(vaultId: String): String {
            return "vault_detail/$vaultId/add_account"
        }
    }

}