package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QbtcClaimAmountFormatterTest {

    @Test
    fun `formats satoshis to 8 decimal places`() {
        assertEquals("0.00000001", QbtcClaimAmountFormatter.formatAmount(1))
        assertEquals("1.00000000", QbtcClaimAmountFormatter.formatAmount(100_000_000))
        assertEquals("0.12345678", QbtcClaimAmountFormatter.formatAmount(12_345_678))
        assertEquals("0.00000000", QbtcClaimAmountFormatter.formatAmount(0))
    }

    @Test
    fun `appends the unit suffix`() {
        assertEquals("0.00060000 BTC", QbtcClaimAmountFormatter.formatBtc(60_000))
        assertEquals("0.00060000 QBTC", QbtcClaimAmountFormatter.formatQbtc(60_000))
    }
}
