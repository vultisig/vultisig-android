package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QbtcClaimAmountFormatterTest {

    @Test
    fun `formats satoshis with up to 8 decimals, trailing zeros trimmed`() {
        assertEquals("0.00000001", QbtcClaimAmountFormatter.formatAmount(1))
        assertEquals("1", QbtcClaimAmountFormatter.formatAmount(100_000_000))
        assertEquals("0.12345678", QbtcClaimAmountFormatter.formatAmount(12_345_678))
        assertEquals("1.5", QbtcClaimAmountFormatter.formatAmount(150_000_000))
        assertEquals("0.0006", QbtcClaimAmountFormatter.formatAmount(60_000))
        assertEquals("0", QbtcClaimAmountFormatter.formatAmount(0))
    }

    @Test
    fun `appends the unit suffix`() {
        assertEquals("0.0006 BTC", QbtcClaimAmountFormatter.formatBtc(60_000))
        assertEquals("0.0006 QBTC", QbtcClaimAmountFormatter.formatQbtc(60_000))
    }
}
