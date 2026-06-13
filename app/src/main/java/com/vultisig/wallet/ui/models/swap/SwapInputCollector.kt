package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import javax.inject.Inject

/**
 * Gathers and validates every input required to build a swap transaction.
 *
 * Kept independent of the ViewModel's lifecycle so the validation can be unit-tested on its own:
 * the caller passes in the current selections/fees and gets back [ValidatedSwapInputs] or an
 * [InvalidTransactionDataException].
 */
internal class SwapInputCollector
@Inject
constructor(
    private val convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase,
    private val swapValidator: SwapValidator,
) {

    /**
     * Collects and validates every input required to build a swap transaction.
     *
     * @param srcAmount the raw source-amount text entered by the user.
     * @return the validated inputs ready for [SwapTransactionBuilder.build].
     * @throws InvalidTransactionDataException if any required input is missing or invalid (no
     *   vault/source/destination, unusable gas fee, same-asset pair, invalid/zero amount,
     *   insufficient balance, missing quote, or a failed preflight check).
     */
    fun collect(
        vaultId: String?,
        selectedSrc: SendSrc?,
        selectedDst: SendSrc?,
        srcAmount: String,
        quote: SwapQuote?,
        gasFee: TokenValue?,
        estimatedNetworkFeeTokenValue: TokenValue?,
        estimatedNetworkFeeFiatValue: FiatValue?,
    ): ValidatedSwapInputs {
        val vaultIdValue =
            vaultId
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.swap_screen_invalid_no_vault)
                )
        val selectedSrcValue =
            selectedSrc
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.swap_screen_invalid_no_src_error)
                )
        val selectedDstValue =
            selectedDst
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.swap_screen_invalid_selected_no_dst)
                )

        val gasFeeValue =
            gasFee?.takeIf { it.value != BigInteger.ZERO }
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.swap_screen_invalid_gas_fee_calculation)
                )
        val gasFeeFiatValue =
            estimatedNetworkFeeFiatValue
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.swap_screen_invalid_gas_fee_calculation)
                )

        val srcToken = selectedSrcValue.account.token
        val dstToken = selectedDstValue.account.token

        if (srcToken == dstToken) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.swap_screen_same_asset_error_message)
            )
        }

        val srcAddress = selectedSrcValue.address.address

        val srcAmountDecimal = srcAmount.toBigDecimalOrNull()
        val srcAmountInt =
            srcAmountDecimal?.movePointRight(srcToken.decimal)?.toBigInteger()?.takeIf {
                it != BigInteger.ZERO
            }
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(
                        if (srcAmountDecimal == null) R.string.swap_form_invalid_amount
                        else R.string.swap_screen_invalid_zero_token_amount
                    )
                )

        val selectedSrcBalance =
            selectedSrcValue.account.tokenValue?.value
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_insufficient_balance)
                )

        val srcTokenValue = convertTokenAndValueToTokenValue(srcToken, srcAmountInt)

        val quoteValue =
            quote?.takeIf { it.expectedDstValue.value != BigInteger.ZERO }
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.swap_screen_invalid_quote_calculation)
                )

        swapValidator
            .validateSwapPreflight(
                selectedSrc = selectedSrcValue,
                srcAmountValue = srcAmountInt,
                selectedSrcBalance = selectedSrcBalance,
                estimatedNetworkFeeTokenValue = estimatedNetworkFeeTokenValue,
            )
            ?.let { throw InvalidTransactionDataException(it) }

        return ValidatedSwapInputs(
            vaultId = vaultIdValue,
            srcToken = srcToken,
            dstToken = dstToken,
            srcAddress = srcAddress,
            srcTokenValue = srcTokenValue,
            quote = quoteValue,
            gasFee = gasFeeValue,
            gasFeeFiatValue = gasFeeFiatValue,
            estimatedNetworkFeeTokenValue = estimatedNetworkFeeTokenValue,
            estimatedNetworkFeeFiatValue = estimatedNetworkFeeFiatValue,
        )
    }
}
