package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormat
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/** The confirmation-screen labels of a limit order — always produced as a pair or not at all. */
internal data class LimitOrderLabels(val targetPriceLabel: String, val expiryLabel: UiText)

/**
 * Formats the Target Price / expiry labels shown on a limit order's confirmation screen.
 *
 * Shared by both signing devices: the initiator formats them from the order it built, the cosigner
 * from the price and lifetime it recovers out of the `=<` memo it is asked to sign (#4154). Keeping
 * one formatter is what makes the two screens read identically — the co-signer must be able to see
 * that it is approving a limit order at a given price, not an ordinary swap.
 */
internal class FormatLimitOrderLabelsUseCase
@Inject
constructor(
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
) {

    /**
     * Returns null for a lifetime this app has no pill for, so a caller can never end up rendering
     * the limit-order title with half its detail row.
     */
    suspend operator fun invoke(
        srcToken: Coin,
        dstToken: Coin,
        targetPrice: BigDecimal,
        expiryHours: Int,
        currency: AppCurrency,
    ): LimitOrderLabels? {
        val expiry =
            LimitExpiryOption.entries.firstOrNull { it.hours == expiryHours } ?: return null
        return LimitOrderLabels(
            targetPriceLabel =
                targetPriceLabel(
                    srcToken = srcToken,
                    dstToken = dstToken,
                    targetPrice = targetPrice,
                    currency = currency,
                ),
            expiryLabel = UiText.StringResource(expiry.labelRes),
        )
    }

    /**
     * "1 <sell> = $0.42" — the target price expressed per whole SELL unit, in the app currency
     * (never a hardcoded `$`), matching how the limit form prices the same order. Falls back to
     * buy-asset units when the buy asset has no price.
     *
     * Quoted per sell unit rather than per buy unit so the label states the rate the memo's LIM
     * actually encodes instead of its reciprocal, and so the confirm screen a co-signer reads
     * matches the form the initiator placed the order from.
     */
    private suspend fun targetPriceLabel(
        srcToken: Coin,
        dstToken: Coin,
        targetPrice: BigDecimal,
        currency: AppCurrency,
    ): String {
        val buyUnitFiat =
            try {
                convertTokenValueToFiat(
                    dstToken,
                    TokenValue(
                        BigInteger.TEN.pow(dstToken.decimal),
                        dstToken.ticker,
                        dstToken.decimal,
                    ),
                    currency,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to price the limit order's buy asset")
                null
            }
        val fiat = LimitOrderPricing.fiatPricePerSellUnit(targetPrice, buyUnitFiat?.value)
        if (fiat != null) {
            val formatted =
                fiatValueToStringMapper(FiatValue(fiat, currency.ticker), asPrice = true)
            return "1 ${srcToken.ticker} = $formatted"
        }
        // DecimalFormat is not thread-safe, and this use case is injected into singleton-scoped
        // callers reached from several screens' scopes, so the formatter is built per call rather
        // than shared.
        val assetFormat = DecimalFormat("#,##0.########")
        return "1 ${srcToken.ticker} = ${assetFormat.format(targetPrice)} ${dstToken.ticker}"
    }
}
