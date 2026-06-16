package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.FiatValue
import java.math.BigDecimal

/**
 * Clamps a destination fiat figure to the source fiat for a value-preserving swap (#4878).
 *
 * Destination fiat is derived from the destination token's independent market price (e.g.
 * CoinGecko). For illiquid tokens that mark diverges from the DEX pool rate the quote actually
 * executes at, inflating the destination above the source — a swap can never yield more fiat than
 * you put in. So when the market value exceeds the source, fall back to the source value. Genuine
 * price impact / fees that push the destination *below* the source are real and pass through
 * unchanged. A non-positive source fiat (cold price) disables the clamp so the destination isn't
 * zeroed out.
 *
 * Applied everywhere the destination fiat is displayed — the swap form, plus the verify and keysign
 * screens the user signs from (via
 * [com.vultisig.wallet.ui.models.mappers.SwapTransactionToUiModelMapper]).
 */
internal fun clampDstFiatToSrcFiat(srcFiat: FiatValue, marketDstFiat: FiatValue): FiatValue =
    if (srcFiat.value > BigDecimal.ZERO && marketDstFiat.value > srcFiat.value) {
        FiatValue(value = srcFiat.value, currency = marketDstFiat.currency)
    } else {
        marketDstFiat
    }
