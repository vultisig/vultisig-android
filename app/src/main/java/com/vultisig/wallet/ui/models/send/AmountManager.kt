package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

internal class AmountManager(
    private val scope: CoroutineScope,
    private val tokenAmountFieldState: TextFieldState,
    private val fiatAmountFieldState: TextFieldState,
    private val selectedToken: StateFlow<Coin?>,
    private val gasFee: StateFlow<TokenValue?>,
    private val accountProvider: () -> Account?,
    private val appCurrency: StateFlow<AppCurrency>,
    private val chainValidationService: ChainValidationService,
    private val tokenPriceRepository: TokenPriceRepository,
) {
    private var lastTokenValueUserInput = ""
    private var lastFiatValueUserInput = ""
    private var maxAmount: BigDecimal = BigDecimal.ZERO

    private val _isMaxAmount = MutableStateFlow(false)
    val isMaxAmount: StateFlow<Boolean> = _isMaxAmount.asStateFlow()

    private val _reapingError = MutableStateFlow<UiText?>(null)
    val reapingError: StateFlow<UiText?> = _reapingError.asStateFlow()

    /** Snapshot of the last value the user picked as "max" — read at submit time. */
    val currentMaxAmount: BigDecimal
        get() = maxAmount

    fun start() {
        scope.launch { collectConversion() }
        scope.launch { collectReaping() }
    }

    /** Capture the value the user just picked as max, so the conversion flow can recognize it. */
    fun markMax(amount: BigDecimal) {
        maxAmount = amount
        _isMaxAmount.value = amount > BigDecimal.ZERO
    }

    /** Reset the bidirectional cache so a fresh token selection re-triggers conversion. */
    fun resetUserInputCache() {
        lastTokenValueUserInput = ""
    }

    fun validateTokenAmount(value: String): UiText? {
        if (value.length > TextFieldUtils.AMOUNT_MAX_LENGTH) {
            return UiText.StringResource(R.string.send_from_invalid_amount)
        }
        val decimal = value.toBigDecimalOrNull()
        if (decimal == null || decimal <= BigDecimal.ZERO) {
            return UiText.StringResource(R.string.send_error_no_amount)
        }
        return null
    }

    private suspend fun collectConversion() {
        combine(
                selectedToken.filterNotNull(),
                tokenAmountFieldState.textAsFlow(),
                fiatAmountFieldState.textAsFlow(),
            ) { token, tokenField, fiatField ->
                val tokenString = tokenField.toString()
                val fiatString = fiatField.toString()
                when {
                    lastTokenValueUserInput != tokenString -> handleTokenInput(token, tokenString)
                    lastFiatValueUserInput != fiatString -> handleFiatInput(token, fiatString)
                }
            }
            .collect()
    }

    private suspend fun handleTokenInput(token: Coin, tokenString: String) {
        val tokenDecimal = tokenString.toBigDecimalOrNull()
        _isMaxAmount.value = tokenDecimal == maxAmount && maxAmount > BigDecimal.ZERO

        val fiatValue =
            convertValue(tokenString, token) { value, price, t ->
                    // Fiat output: bound to the token's decimal precision
                    value
                        .multiply(price)
                        .setScale(t.decimal, RoundingMode.DOWN)
                        .stripTrailingZeros()
                }
                ?.takeIf { it.isNotEmpty() } ?: return

        lastTokenValueUserInput = tokenString
        lastFiatValueUserInput = fiatValue
        fiatAmountFieldState.setTextAndPlaceCursorAtEnd(fiatValue)
    }

    private suspend fun handleFiatInput(token: Coin, fiatString: String) {
        val tokenValue =
            convertValue(fiatString, token) { value, price, t ->
                    value.divide(price, t.decimal, RoundingMode.DOWN)
                }
                ?.takeIf { it.isNotEmpty() } ?: return

        val tokenDecimal = tokenValue.toBigDecimalOrNull()
        _isMaxAmount.value = tokenDecimal == maxAmount && maxAmount > BigDecimal.ZERO

        lastTokenValueUserInput = tokenValue
        lastFiatValueUserInput = fiatString
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd(tokenValue)
    }

    private suspend fun convertValue(
        value: String,
        token: Coin,
        transform: (value: BigDecimal, price: BigDecimal, token: Coin) -> BigDecimal,
    ): String? {
        val decimalValue = value.toBigDecimalOrNull() ?: return ""

        val price =
            try {
                tokenPriceRepository.getPrice(token, appCurrency.value).first()
            } catch (e: Exception) {
                Timber.d("Failed to get price for token %s", token)
                return null
            }

        if (price == BigDecimal.ZERO) {
            Timber.w("convertValue: price is ZERO for token %s, skipping", token.ticker)
            return null
        }

        return transform(decimalValue, price, token).toPlainString()
    }

    private suspend fun collectReaping() {
        combine(
                selectedToken.filterNotNull(),
                tokenAmountFieldState.textAsFlow(),
                gasFee.filterNotNull(),
            ) { token, tokenAmount, gas ->
                _reapingError.value =
                    chainValidationService.checkIsReapable(
                        accountProvider(),
                        token,
                        tokenAmount.toString(),
                        gas,
                    )
            }
            .collect()
    }
}
