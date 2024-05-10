package com.vultisig.wallet.ui.navigation

internal sealed class Destination(
    val route: String,
) {

    data object Send : Destination(
        route = "send"
    ) {
        const val staticRoute = "send"
    }

    data class SelectTokens(
        val vaultId: String,
        val accountId: String,
    ) : Destination(
        route = "vault_detail/${vaultId}/account/${accountId}/select_tokens"
    ) {
        companion object {
            const val ARG_VAULT_ID = "vault_id"
            const val ARG_ACCOUNT_ID = "account_id"
            const val staticRoute =
                "vault_detail/{$ARG_VAULT_ID}/account/{$ARG_ACCOUNT_ID}/select_tokens"
        }
    }

    data object Back : Destination(
        route = ""
    )

}