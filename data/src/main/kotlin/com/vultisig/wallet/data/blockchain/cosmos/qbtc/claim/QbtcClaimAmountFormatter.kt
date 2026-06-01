package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats satoshi amounts to BTC/QBTC strings with up to 8 decimals, trailing zeros trimmed. BTC
 * and QBTC share the same numeric value (1 sat → 1 qsat) — only the unit suffix differs. Computed
 * at the 8-decimal precision iOS/Windows use, then trimmed so a clean value reads "1.5", not
 * "1.50000000".
 */
object QbtcClaimAmountFormatter {
    private const val SATS_PER_COIN = 100_000_000L
    private const val DECIMALS = 8

    fun formatAmount(sats: Long): String {
        val plain =
            BigDecimal.valueOf(sats)
                .divide(BigDecimal.valueOf(SATS_PER_COIN), DECIMALS, RoundingMode.DOWN)
                .toPlainString()
        // Trim trailing zeros (and a dangling decimal point); precise values like "0.00000001" keep
        // every needed digit, "1.50000000" becomes "1.5", "2.00000000" becomes "2".
        return if ('.' in plain) plain.trimEnd('0').trimEnd('.') else plain
    }

    fun formatBtc(sats: Long): String = "${formatAmount(sats)} BTC"

    fun formatQbtc(sats: Long): String = "${formatAmount(sats)} QBTC"
}
