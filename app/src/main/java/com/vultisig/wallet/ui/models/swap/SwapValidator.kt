package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

internal class SwapValidator @Inject constructor() {

    fun validateSrcAmount(srcAmount: String): UiText? {
        if (srcAmount.isEmpty() || srcAmount.length > TextFieldUtils.AMOUNT_MAX_LENGTH) {
            return UiText.StringResource(R.string.swap_form_invalid_amount)
        }
        val srcAmountBigDecimal = srcAmount.toBigDecimalOrNull()
        if (srcAmountBigDecimal == null || srcAmountBigDecimal <= BigDecimal.ZERO) {
            return UiText.StringResource(R.string.swap_error_no_amount)
        }
        return null
    }

    /**
     * Pre-flight balance/gas validation run inside `swap()` before building the transaction.
     * Delegates to the shared [validateSwapBalance] core and returns the form error to surface
     * (wrapped in `InvalidTransactionDataException`), or `null` when the swap may proceed.
     *
     * @param selectedSrcBalance the source account balance, already resolved and non-null by the
     *   caller.
     * @return the error [UiText] to raise, or `null` if pre-flight validation passes.
     */
    fun validateSwapPreflight(
        selectedSrc: SendSrc,
        srcAmountValue: BigInteger,
        selectedSrcBalance: BigInteger,
        estimatedNetworkFeeTokenValue: TokenValue?,
    ): UiText? =
        validateSwapBalance(
            selectedSrc,
            srcAmountValue,
            selectedSrcBalance,
            estimatedNetworkFeeTokenValue,
        )

    /**
     * Form-time balance/gas validation sharing the same [validateSwapBalance] core as
     * [validateSwapPreflight], so the two cannot diverge. Returns `null` when the source balance is
     * unknown (nothing to validate yet) or when validation passes; otherwise wraps the error.
     */
    fun validateBalanceForSwap(
        src: SendSrc,
        srcAmountValue: BigInteger,
        estimatedNetworkFeeTokenValue: TokenValue?,
    ): SwapBalanceValidation? {
        val selectedSrcBalance = src.account.tokenValue?.value ?: return null
        val formError =
            validateSwapBalance(
                src,
                srcAmountValue,
                selectedSrcBalance,
                estimatedNetworkFeeTokenValue,
            ) ?: return null
        return SwapBalanceValidation(formError = formError)
    }

    /**
     * Shared source-balance and native-gas check for native vs. non-native source tokens, returning
     * the error [UiText] to surface or `null` when the swap may proceed. Single source of truth for
     * both [validateSwapPreflight] and [validateBalanceForSwap].
     */
    private fun validateSwapBalance(
        selectedSrc: SendSrc,
        srcAmountValue: BigInteger,
        selectedSrcBalance: BigInteger,
        estimatedNetworkFeeTokenValue: TokenValue?,
    ): UiText? {
        val srcToken = selectedSrc.account.token
        val feeValue = estimatedNetworkFeeTokenValue?.value ?: BigInteger.ZERO
        if (srcToken.isNativeToken) {
            if (srcAmountValue + feeValue > selectedSrcBalance) {
                return UiText.FormattedText(
                    R.string.swap_error_insufficient_balance_and_fees,
                    listOf(srcToken.ticker),
                )
            }
        } else {
            val nativeTokenAccount = selectedSrc.address.accounts.find { it.token.isNativeToken }
            val nativeTokenValue =
                nativeTokenAccount?.tokenValue?.value
                    ?: return UiText.StringResource(R.string.send_error_no_token)
            if (selectedSrcBalance < srcAmountValue) {
                return UiText.FormattedText(
                    R.string.swap_error_insufficient_source_token,
                    listOf(srcToken.ticker),
                )
            }
            if (nativeTokenValue < feeValue) {
                return UiText.FormattedText(
                    R.string.swap_error_insufficient_gas_fees,
                    listOf(
                        "${nativeTokenAccount.token.ticker} (${nativeTokenAccount.token.chain.raw})"
                    ),
                )
            }
        }
        return null
    }
}

internal data class SwapBalanceValidation(val formError: UiText)
