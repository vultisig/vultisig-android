package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.allowZeroGas
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asAddressInput
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import timber.log.Timber

internal data class ValidatedAccount(
    val vaultId: String,
    val selectedAccount: Account,
    val chain: Chain,
    val gasFee: TokenValue,
    val dstAddress: String,
)

internal class AccountValidator(
    private val vaultIdProvider: () -> String?,
    private val selectedAccountProvider: () -> Account?,
    private val tokenAmountFieldState: TextFieldState,
    private val addressFieldState: TextFieldState,
    private val gasFee: StateFlow<TokenValue?>,
    private val addressParserRepository: AddressParserRepository,
) {
    suspend fun validate(): ValidatedAccount {
        val vaultId =
            vaultIdProvider()
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_token)
                )

        val selectedAccount =
            selectedAccountProvider()
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_token)
                )

        val chain = selectedAccount.token.chain

        val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()
        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val gasFeeValue = awaitGasFee()

        if (!selectedAccount.token.allowZeroGas() && gasFeeValue.value <= BigInteger.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_gas_fee)
            )
        }

        val dstAddress =
            try {
                addressParserRepository.resolveName(addressFieldState.text.asAddressInput(), chain)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e)
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.failed_to_resolve_address)
                )
            }

        return ValidatedAccount(
            vaultId = vaultId,
            selectedAccount = selectedAccount,
            chain = chain,
            gasFee = gasFeeValue,
            dstAddress = dstAddress,
        )
    }

    suspend fun resolveDstAddress(rawInput: String, chain: Chain): String =
        addressParserRepository.resolveName(rawInput, chain)

    suspend fun awaitGasFee(): TokenValue {
        gasFee.value?.let {
            return it
        }
        try {
            return withTimeout(GAS_FEE_TIMEOUT_MS) { gasFee.filterNotNull().first() }
        } catch (_: TimeoutCancellationException) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_gas_fee)
            )
        }
    }

    private companion object {
        const val GAS_FEE_TIMEOUT_MS = 5_000L
    }
}
