package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.data.models.DepositTransaction

/**
 * Builds a [DepositTransaction] for a single [com.vultisig.wallet.ui.models.deposit.DepositOption].
 *
 * Mirrors `SendSubmitStrategy` from the send flow but returns a transaction instead of submitting
 * it directly — the deposit dispatcher remains responsible for persisting the transaction and
 * navigating to verification.
 */
internal fun interface DepositSubmitStrategy {
    suspend fun build(): DepositTransaction
}
