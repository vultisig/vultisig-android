package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.text.DecimalFormat
import javax.inject.Inject

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
internal class FormatLimitOrderLabelsUseCase @Inject constructor() {

    /**
     * Returns null for a lifetime this app has no pill for, so a caller can never end up rendering
     * the limit-order title with half its detail row.
     */
    suspend operator fun invoke(
        srcToken: Coin,
        dstToken: Coin,
        targetPrice: BigDecimal,
        expiryHours: Int,
    ): LimitOrderLabels? {
        val expiry =
            LimitExpiryOption.entries.firstOrNull { it.hours == expiryHours } ?: return null
        return LimitOrderLabels(
            targetPriceLabel =
                targetPriceLabel(
                    srcToken = srcToken,
                    dstToken = dstToken,
                    targetPrice = targetPrice,
                ),
            expiryLabel = UiText.StringResource(expiry.labelRes),
        )
    }

    /**
     * "1 RUNE = 2.55210159 DOGE" — the target price in asset terms, per whole SELL unit.
     *
     * Deliberately NOT converted to fiat. This label names the exact rate the memo's LIM encodes,
     * and it is the last thing a co-signer reads before approving; a fiat conversion would restate
     * it through a price feed that does not always agree with the THORChain pool rate the order
     * settles at, so the figure a signer approves would drift from the figure that gets signed.
     */
    private fun targetPriceLabel(srcToken: Coin, dstToken: Coin, targetPrice: BigDecimal): String {
        // DecimalFormat is not thread-safe, and this use case is injected into singleton-scoped
        // callers reached from several screens' scopes, so the formatter is built per call rather
        // than shared.
        val assetFormat = DecimalFormat("#,##0.########")
        return "1 ${srcToken.ticker} = ${assetFormat.format(targetPrice)} ${dstToken.ticker}"
    }
}
