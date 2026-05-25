package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.data.models.DepositTransaction

/**
 * Builds a [DepositTransaction] for a single [com.vultisig.wallet.ui.models.deposit.DepositOption].
 *
 * Pure builder contract — no loading/error/navigation side effects. The deposit dispatcher in
 * `DepositFormViewModel` owns persistence and navigation after [build] returns.
 */
internal fun interface DepositSubmitStrategy {
    /** Builds the [DepositTransaction] for the current form state. */
    suspend fun build(): DepositTransaction
}
