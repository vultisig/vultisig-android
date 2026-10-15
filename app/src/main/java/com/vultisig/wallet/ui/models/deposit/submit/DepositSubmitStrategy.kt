package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger

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

/**
 * Refuses an amount that [formAddress] cannot cover together with [gasFee], via
 * [requireTokenAmount].
 *
 * Checked against the form's address because it carries the loaded balances; a strategy's own
 * `loadAddress(...).first()` is the balance-less first emission.
 *
 * @throws InvalidTransactionDataException when the form has no address or no account for [token],
 *   or when the balance is insufficient.
 */
internal fun requireCoveredByFormBalance(
    formAddress: Address?,
    token: Coin,
    gasFee: TokenValue,
    requireTokenAmount: (Coin, Account, Address, TokenValue) -> BigInteger,
) {
    val address =
        formAddress
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
    val account =
        address.accounts.firstOrNull { it.token.id == token.id }
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
    requireTokenAmount(token, account, address, gasFee)
}
