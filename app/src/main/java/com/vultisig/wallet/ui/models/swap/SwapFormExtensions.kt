package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.models.findCurrentSrc
import com.vultisig.wallet.ui.models.firstSendSrc
import com.vultisig.wallet.ui.models.send.SendSrc
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.MutableStateFlow

private const val MAX_DISPLAY_DECIMALS = 8

/**
 * Formats an amount for display after a flip, trimming to the token's decimals (capped at
 * [MAX_DISPLAY_DECIMALS]) and stripping trailing zeros.
 *
 * @param tokenDecimals the token's decimal precision; falls back to [MAX_DISPLAY_DECIMALS] when
 *   null.
 */
internal fun BigDecimal.formatFlippedAmount(tokenDecimals: Int? = null): String =
    setScale(
            (tokenDecimals ?: MAX_DISPLAY_DECIMALS).coerceAtMost(MAX_DISPLAY_DECIMALS),
            RoundingMode.DOWN,
        )
        .stripTrailingZeros()
        .toPlainString()

/**
 * Updates the source-selection flow for the given token/chain: clears it when no addresses are
 * available, picks the first matching source when unset, or re-resolves the current source
 * otherwise.
 */
internal fun MutableStateFlow<SendSrc?>.updateSrc(
    selectedTokenId: String?,
    addresses: List<Address>,
    chain: Chain?,
) {
    val selectedSrcValue = value
    value =
        if (addresses.isEmpty()) {
            null
        } else {
            if (selectedSrcValue == null) {
                addresses.firstSendSrc(selectedTokenId, chain)
            } else {
                addresses.findCurrentSrc(selectedTokenId, selectedSrcValue)
            }
        }
}
