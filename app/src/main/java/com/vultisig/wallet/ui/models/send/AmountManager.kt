package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.CancellationException
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

    /**
     * Pre-arms the conversion caches for an amount the app itself is about to write into the fields
     * — a submit-time clamp, not a keystroke. Without it [collectConversion] reads that write as
     * fresh user input and re-runs the price conversion, which blanks the fiat mirror whenever the
     * fetch fails, leaving a resubmit one Back-tap away from persisting a $0.00 estimate beside a
     * correct amount.
     *
     * Call it immediately before writing the fields: the writes are synchronous, so the collector
     * only ever resumes on the final pair. Pass null for [fiatAmount] to leave the fiat field (and
     * its cache) alone.
     */
    fun markProgrammaticAmount(tokenAmount: String, fiatAmount: String?) {
        lastTokenValueUserInput = tokenAmount
        if (fiatAmount != null) {
            lastFiatValueUserInput = fiatAmount
        }
        // The suppressed handleTokenInput would have re-derived this; keep it in step.
        _isMaxAmount.value =
            tokenAmount.toPlainBigDecimalOrNull()?.compareTo(maxAmount) == 0 &&
                maxAmount > BigDecimal.ZERO
    }

    /**
     * Reset the bidirectional cache and the max snapshot. Called when the selected token changes —
     * keeping the prior token's max value would let `isMax` flip on a coincidental amount match
     * after the switch.
     */
    fun resetUserInputCache() {
        lastTokenValueUserInput = ""
        lastFiatValueUserInput = ""
        maxAmount = BigDecimal.ZERO
        _isMaxAmount.value = false
    }

    fun validateTokenAmount(value: String): UiText? {
        if (value.length > TextFieldUtils.AMOUNT_MAX_LENGTH) {
            return UiText.StringResource(R.string.send_from_invalid_amount)
        }
        val decimal = value.toPlainBigDecimalOrNull()
        if (decimal == null || decimal <= BigDecimal.ZERO) {
            return UiText.StringResource(R.string.send_error_no_amount)
        }
        val tokenDecimals = selectedToken.value?.decimal
        if (tokenDecimals != null && decimal.hasExcessDecimals(tokenDecimals)) {
            return UiText.FormattedText(
                R.string.send_error_too_many_decimals,
                listOf(tokenDecimals),
            )
        }
        return null
    }

    private suspend fun collectConversion() {
        combine(
                selectedToken.filterNotNull(),
                // Both fields are read in ONE snapshotFlow rather than combined as two. Combining
                // them delivers each field's change as its own emission, so a caller writing both
                // — the submit-time clamp — is observed once with the second field still holding
                // its old text. That half-updated pair reads as "the user cleared this field", and
                // the handler below clears its counterpart in response, emptying the amount the
                // write was trying to show. One flow always reports a consistent pair.
                snapshotFlow {
                    tokenAmountFieldState.text.toString() to fiatAmountFieldState.text.toString()
                },
            ) { token, (tokenString, fiatString) ->
                when {
                    lastTokenValueUserInput != tokenString -> handleTokenInput(token, tokenString)
                    lastFiatValueUserInput != fiatString -> handleFiatInput(token, fiatString)
                }
            }
            .collect()
    }

    private suspend fun handleTokenInput(token: Coin, tokenString: String) {
        val tokenDecimal = tokenString.toPlainBigDecimalOrNull()
        _isMaxAmount.value = tokenDecimal?.compareTo(maxAmount) == 0 && maxAmount > BigDecimal.ZERO

        val fiatValue =
            convertValue(tokenString, token) { value, price, t ->
                    // Fiat output: bound to the token's decimal precision
                    value
                        .multiply(price)
                        .setScale(t.decimal, RoundingMode.DOWN)
                        .stripTrailingZeros()
                }
                ?.takeIf { it.isNotEmpty() }

        if (fiatValue == null) {
            // Token side became blank/invalid — clear the mirrored fiat field so the
            // form does not show a stale converted value alongside an empty input.
            lastTokenValueUserInput = tokenString
            lastFiatValueUserInput = ""
            fiatAmountFieldState.setTextAndPlaceCursorAtEnd("")
            return
        }

        lastTokenValueUserInput = tokenString
        lastFiatValueUserInput = fiatValue
        fiatAmountFieldState.setTextAndPlaceCursorAtEnd(fiatValue)
    }

    private suspend fun handleFiatInput(token: Coin, fiatString: String) {
        val tokenValue =
            convertValue(fiatString, token) { value, price, t ->
                    value.divide(price, t.decimal, RoundingMode.DOWN)
                }
                ?.takeIf { it.isNotEmpty() }

        if (tokenValue == null) {
            // Fiat side became blank/invalid — clear the mirrored token field.
            lastFiatValueUserInput = fiatString
            lastTokenValueUserInput = ""
            tokenAmountFieldState.setTextAndPlaceCursorAtEnd("")
            _isMaxAmount.value = false
            return
        }

        val tokenDecimal = tokenValue.toPlainBigDecimalOrNull()
        _isMaxAmount.value = tokenDecimal?.compareTo(maxAmount) == 0 && maxAmount > BigDecimal.ZERO

        lastTokenValueUserInput = tokenValue
        lastFiatValueUserInput = fiatString
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd(tokenValue)
    }

    private suspend fun convertValue(
        value: String,
        token: Coin,
        transform: (value: BigDecimal, price: BigDecimal, token: Coin) -> BigDecimal,
    ): String? {
        val decimalValue = value.toPlainBigDecimalOrNull() ?: return ""

        val price =
            try {
                tokenPriceRepository.getPrice(token, appCurrency.value).first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to get price for token %s", token)
                return null
            }

        if (price.signum() == 0) {
            Timber.w("convertValue: price is ZERO for token %s, skipping", token.ticker)
            return null
        }

        return transform(decimalValue, price, token).toPlainString()
    }

    private suspend fun collectReaping() {
        combine(selectedToken, tokenAmountFieldState.textAsFlow(), gasFee) { token, tokenAmount, gas
                ->
                // Don't filterNotNull on token/gas — when they clear (e.g. mid fee recalculation)
                // we need the combine to still emit so we can drop any stale reaping warning.
                _reapingError.value =
                    if (token == null || gas == null) {
                        null
                    } else {
                        chainValidationService.checkIsReapable(
                            accountProvider(),
                            token,
                            tokenAmount.toString(),
                            gas,
                        )
                    }
            }
            .collect()
    }
}
