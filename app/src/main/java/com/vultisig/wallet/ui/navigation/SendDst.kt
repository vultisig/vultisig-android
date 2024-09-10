package com.vultisig.wallet.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.vultisig.wallet.data.models.TransactionId

internal sealed class SendDst(route: String) : Dst(route) {

    companion object {
        const val ARG_TRANSACTION_ID = "transaction_id"
        const val ARG_VAULT_ID = "vault_id"
        const val ARG_PASSWORD = "password"

        val transactionArgs = listOf(
            navArgument(ARG_TRANSACTION_ID) { type = NavType.StringType },
            navArgument(ARG_PASSWORD) {
                type = NavType.StringType
                nullable = true
            }
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

    data class Password(
        val transactionId: TransactionId,
    ) : SendDst(
        route = buildRoute(transactionId)
    ) {
        companion object {

            val staticRoute = buildRoute(
                "{$ARG_TRANSACTION_ID}"
            )

            private fun buildRoute(
                transactionId: TransactionId,
            ) = "send/${transactionId}/password"
        }
    }

    data class Keysign(
        val transactionId: TransactionId,
        val password: String?,
    ) : SendDst(
        route = buildRoute(transactionId, password)
    ) {
        companion object {

            val staticRoute = buildRoute(
                "{$ARG_TRANSACTION_ID}",
                "{$ARG_PASSWORD}"
            )

            private fun buildRoute(
                transactionId: TransactionId,
                password: String?,
            ) = "send/${transactionId}/sign?$ARG_PASSWORD=$password"
        }
    }

    data object Back : SendDst(
        route = "back"
    )
}