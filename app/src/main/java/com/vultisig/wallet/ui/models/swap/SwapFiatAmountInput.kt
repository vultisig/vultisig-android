package com.vultisig.wallet.ui.models.swap

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Currency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Fiat entry for the swap form's From amount (#5888): the user can flip the field to type an
 * app-currency amount, which is converted into the token amount the quote pipeline and the signed
 * transaction always read from [tokenAmountState]. Fiat never leaves the form — it is an input
 * conversion only.
 *
 * The token field stays the source of truth. Typing into the fiat field rewrites it, and any other
 * write to it (percentage / Max chips, a flip, a token switch) re-derives the fiat mirror from it,
 * so the two never disagree. Both directions use the source token's current app-currency price —
 * the same price source that values the fiat line shown under the token amount.
 */
internal class SwapFiatAmountInput(
    private val scope: CoroutineScope,
    private val tokenAmountState: TextFieldState,
    private val fiatAmountState: TextFieldState,
    private val selectedSrcToken: Flow<Coin?>,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val uiState: MutableStateFlow<SwapFormUiModel>,
    private val onTokenAmountConverted: () -> Unit,
) {
    // The last text this class wrote to, or observed from, each field. A field whose text differs
    // from its entry is the one the user (or another writer) just changed, so its counterpart is
    // what gets re-derived — the same guard AmountManager uses to keep the mirror from echoing.
    private var lastTokenText = ""
    private var lastFiatText = ""
    private var lastTokenId: String? = null
    private var lastCurrency: AppCurrency? = null
    private var wasFiatInput = false

    fun start() {
        scope.launch { observePriceAvailability() }
        scope.launch { collectConversion() }
    }

    /**
     * Flips the From field between token and fiat input. Entering fiat mode requires a price for
     * the source token; without one there is nothing to convert by, so the tap is ignored.
     */
    fun toggle() {
        uiState.update {
            if (it.isSrcFiatInput) {
                it.copy(isSrcFiatInput = false)
            } else if (it.isSrcFiatInputAvailable) {
                it.copy(isSrcFiatInput = true)
            } else {
                it
            }
        }
    }

    /**
     * Tracks whether the source token can be priced in the app currency. Fiat mode is offered only
     * while it can, and is left the moment the price goes away, so the field can never sit in a
     * mode whose conversion would fail.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observePriceAvailability() {
        // Deduped so a balance refresh (which re-emits the same token) doesn't restart the price
        // flow.
        combine(selectedSrcToken.distinctUntilChanged(), appCurrencyRepository.currency) {
                token,
                currency ->
                token to currency
            }
            .flatMapLatest { (token, currency) ->
                if (token == null) {
                    flowOf(FiatAvailability(isAvailable = false, symbol = ""))
                } else {
                    val symbol = currencySymbol(currency)
                    tokenPriceRepository
                        .getPrice(token, currency)
                        .map { price -> FiatAvailability(price.signum() > 0, symbol) }
                        .catch { e ->
                            Timber.w(e, "Failed to observe %s price for fiat input", token.ticker)
                            emit(FiatAvailability(isAvailable = false, symbol = symbol))
                        }
                }
            }
            .distinctUntilChanged()
            .collect { (isAvailable, symbol) ->
                uiState.update {
                    it.copy(
                        isSrcFiatInputAvailable = isAvailable,
                        isSrcFiatInput = it.isSrcFiatInput && isAvailable,
                        fiatSymbol = symbol,
                    )
                }
            }
    }

    private suspend fun collectConversion() {
        combine(
                selectedSrcToken.filterNotNull().distinctUntilChanged(),
                appCurrencyRepository.currency,
                // Both fields in one snapshotFlow so each change is observed against the other
                // field's current text, never a half-updated pair (see AmountManager).
                snapshotFlow {
                    tokenAmountState.text.toString() to fiatAmountState.text.toString()
                },
                uiState.map { it.isSrcFiatInput }.distinctUntilChanged(),
            ) { token, currency, (tokenText, fiatText), isFiatInput ->
                val isEnteringFiatInput = isFiatInput && !wasFiatInput
                wasFiatInput = isFiatInput
                when {
                    // A new source token or currency prices differently, so the fiat mirror is
                    // re-derived from the token amount it carries over — the token amount is
                    // what the user keeps across a token switch today.
                    token.id != lastTokenId || currency != lastCurrency -> {
                        lastTokenId = token.id
                        lastCurrency = currency
                        mirrorTokenToFiat(token, currency, tokenText)
                    }
                    // The mirror is off screen in token mode, so it is re-seeded at the current
                    // price the moment it comes on — a price that moved since the token amount
                    // was last written would otherwise open a fiat field that disagrees with the
                    // line just tapped. Live ticks are not chased while the field is in use: that
                    // would rewrite a typed amount under the user.
                    isEnteringFiatInput -> mirrorTokenToFiat(token, currency, tokenText)
                    tokenText != lastTokenText -> mirrorTokenToFiat(token, currency, tokenText)
                    fiatText != lastFiatText -> convertFiatToToken(token, currency, fiatText)
                }
            }
            .collect()
    }

    private suspend fun mirrorTokenToFiat(token: Coin, currency: AppCurrency, tokenText: String) {
        lastTokenText = tokenText
        val amount = tokenText.toBigDecimalOrNull()
        val price = amount?.let { unitPrice(token, currency) }
        val fiatText =
            if (amount == null || price == null) {
                ""
            } else {
                // Rounded the way the fiat line under the token amount is, so tapping "$2.52"
                // opens a field reading 2.52, not a truncated 2.51.
                amount
                    .multiply(price)
                    .setScale(fractionDigits(currency), RoundingMode.HALF_EVEN)
                    .stripTrailingZeros()
                    .toPlainString()
            }
        lastFiatText = fiatText
        if (fiatText != fiatAmountState.text.toString()) {
            fiatAmountState.setTextAndPlaceCursorAtEnd(fiatText)
        }
    }

    private suspend fun convertFiatToToken(token: Coin, currency: AppCurrency, fiatText: String) {
        lastFiatText = fiatText
        val fiat = fiatText.toBigDecimalOrNull()
        val price = fiat?.let { unitPrice(token, currency) }
        val tokenText =
            if (fiat == null || price == null) {
                ""
            } else {
                // Empty rather than "0" for a fiat amount too small to buy a displayable unit: an
                // empty field clears the quote silently, where a literal "0" reaches the pipeline
                // as an error.
                fiat
                    .divide(price, token.decimal, RoundingMode.DOWN)
                    .formatFlippedAmount(token.decimal)
                    .takeIf { it.toBigDecimal().signum() > 0 } ?: ""
            }
        lastTokenText = tokenText
        if (tokenText != tokenAmountState.text.toString()) {
            onTokenAmountConverted()
            tokenAmountState.setTextAndPlaceCursorAtEnd(tokenText)
        }
    }

    /** The app-currency price of one whole [token], or null when it is unknown or not positive. */
    private suspend fun unitPrice(token: Coin, currency: AppCurrency): BigDecimal? =
        try {
            tokenPriceRepository.getPrice(token, currency).first().takeIf { it.signum() > 0 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to price %s for fiat input", token.ticker)
            null
        }

    private suspend fun currencySymbol(currency: AppCurrency): String =
        (appCurrencyRepository.getCurrencyFormat(currency) as? DecimalFormat)
            ?.decimalFormatSymbols
            ?.currencySymbol ?: currency.ticker

    private fun fractionDigits(currency: AppCurrency): Int =
        runCatching { Currency.getInstance(currency.ticker).defaultFractionDigits }
            .getOrDefault(DEFAULT_FRACTION_DIGITS)
            .coerceAtLeast(0)

    private data class FiatAvailability(val isAvailable: Boolean, val symbol: String)

    private companion object {
        private const val DEFAULT_FRACTION_DIGITS = 2
    }
}
