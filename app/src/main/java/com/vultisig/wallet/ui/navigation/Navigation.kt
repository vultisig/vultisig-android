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

}