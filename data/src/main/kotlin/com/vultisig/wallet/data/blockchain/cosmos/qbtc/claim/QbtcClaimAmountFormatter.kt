package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats satoshi amounts to 8-decimal BTC/QBTC strings. BTC and QBTC share the same numeric value
 * (1 sat → 1 qsat) — only the unit suffix differs. Mirrors iOS `QBTCClaimAmountFormatter` and the
 * Windows `btcDecimals = 8` formatting.
 */
object QbtcClaimAmountFormatter {
    private const val SATS_PER_COIN = 100_000_000L
    private const val DECIMALS = 8

    fun formatAmount(sats: Long): String =
        BigDecimal.valueOf(sats)
            .divide(BigDecimal.valueOf(SATS_PER_COIN), DECIMALS, RoundingMode.DOWN)
            .toPlainString()

    fun formatBtc(sats: Long): String = "${formatAmount(sats)} BTC"

    fun formatQbtc(sats: Long): String = "${formatAmount(sats)} QBTC"
}
