package com.vultisig.wallet.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.vultisig.wallet.data.models.TransactionId

internal sealed class SendDst(route: String) : Dst(route) {

    companion object {
        const val ARG_TRANSACTION_ID = "transaction_id"
        const val ARG_VAULT_ID = "vault_id"

        val transactionArgs = listOf(
            navArgument(ARG_TRANSACTION_ID) { type = NavType.StringType }
        )
    }

    data object Send : SendDst(
        route = "send"
    )

    data class VerifyTransaction(
        val transactionId: TransactionId,
        val vaultId: String? = null,
    ) : SendDst(
        route = buildRoute(transactionId, vaultId)
    ) {
        companion object {

            val staticRoute = buildRoute(
                "{$ARG_TRANSACTION_ID}",
                "{$ARG_VAULT_ID}"
            )

            private fun buildRoute(
                transactionId: TransactionId,
                vaultId: String?,
            ) = "transaction/${transactionId}/verify?vault=$vaultId"
        }
    }

    data class Keysign(
        val transactionId: TransactionId,
    ) : SendDst(
        route = buildRoute(transactionId)
    ) {
        companion object {

            val staticRoute = buildRoute(
                "{$ARG_TRANSACTION_ID}",
            )

            private fun buildRoute(
                transactionId: TransactionId,
            ) = "send/${transactionId}/sign"
        }
    }

}