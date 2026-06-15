package com.vultisig.wallet.ui.models.send

import java.math.BigDecimal

/**
 * True when this amount carries more fractional digits than the token's [decimals] precision.
 *
 * Converting to base units with `movePointRight(decimals).toBigInteger()` rounds toward zero, so
 * any digit past [decimals] would be dropped silently. Trailing zeros are not real precision —
 * `1.5000000` fits a 6-decimal token — so they are stripped before the scale is compared.
 */
internal fun BigDecimal.hasExcessDecimals(decimals: Int): Boolean =
    stripTrailingZeros().scale() > decimals
