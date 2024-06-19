package com.vultisig.wallet.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.vultisig.wallet.data.models.TransactionId

internal sealed class SendDst(route: String) : Dst(route) {

    companion object {
        const val ARG_TRANSACTION_ID = "transaction_id"

        val transactionArgs = listOf(
            navArgument(ARG_TRANSACTION_ID) { type = NavType.StringType }
        )
    }

    data object Send : SendDst(
        route = "send"
    )

    data class VerifyTransaction(
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
            ) = "transaction/${transactionId}/verify"
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

    data class KeysignApproval(
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
            ) = "send/${transactionId}/sign/approval"
        }
    }

}