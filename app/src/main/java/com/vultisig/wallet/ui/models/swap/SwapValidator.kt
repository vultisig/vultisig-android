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

    fun validateBalanceForSwap(
        src: SendSrc,
        srcAmountValue: BigInteger,
        estimatedNetworkFeeTokenValue: TokenValue?,
    ): SwapBalanceValidation? {
        val srcToken = src.account.token
        val selectedSrcBalance = src.account.tokenValue?.value ?: return null

        if (srcToken.isNativeToken) {
            val totalRequired =
                srcAmountValue + (estimatedNetworkFeeTokenValue?.value ?: BigInteger.ZERO)
            if (totalRequired > selectedSrcBalance) {
                return SwapBalanceValidation(
                    formError =
                        UiText.FormattedText(
                            R.string.swap_error_insufficient_balance_and_fees,
                            listOf(srcToken.ticker),
                        )
                )
            }
        } else {
            if (srcAmountValue > selectedSrcBalance) {
                return SwapBalanceValidation(
                    formError =
                        UiText.FormattedText(
                            R.string.swap_error_insufficient_source_token,
                            listOf(srcToken.ticker),
                        )
                )
            } else {
                val nativeTokenAccount =
                    src.address.accounts.find { it.token.isNativeToken } ?: return null
                val nativeTokenValue = nativeTokenAccount.tokenValue?.value ?: return null
                if (nativeTokenValue < (estimatedNetworkFeeTokenValue?.value ?: BigInteger.ZERO)) {
                    return SwapBalanceValidation(
                        formError =
                            UiText.FormattedText(
                                R.string.swap_error_insufficient_gas_fees,
                                listOf(
                                    "${nativeTokenAccount.token.ticker} (${nativeTokenAccount.token.chain.raw})"
                                ),
                            )
                    )
                }
            }
        }
        return null
    }
}

internal data class SwapBalanceValidation(val formError: UiText)
