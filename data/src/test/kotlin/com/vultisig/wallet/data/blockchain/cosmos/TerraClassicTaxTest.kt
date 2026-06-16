package com.vultisig.wallet.data.blockchain.cosmos

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TerraClassicTaxTest {

    @Test
    fun `burnTax rounds up so the signed fee never undershoots`() {
        // 333 × 0.005 = 1.665 → ceil → 2.
        assertEquals(
            BigInteger("2"),
            TerraClassicTax.burnTax(BigInteger("333"), BigDecimal("0.005")),
        )
    }

    @Test
    fun `burnTax exact multiple is not rounded up`() {
        // 1_000_000 × 0.005 = 5_000 exactly.
        assertEquals(
            BigInteger("5000"),
            TerraClassicTax.burnTax(BigInteger("1000000"), BigDecimal("0.005")),
        )
    }

    @Test
    fun `burnTax is zero for non-positive amount or rate`() {
        assertEquals(BigInteger.ZERO, TerraClassicTax.burnTax(BigInteger.ZERO, BigDecimal("0.005")))
        assertEquals(BigInteger.ZERO, TerraClassicTax.burnTax(BigInteger("100"), BigDecimal.ZERO))
    }

    @Test
    fun `parseRate parses a high-precision decimal string`() {
        assertEquals(
            BigDecimal("0.005000000000000000"),
            TerraClassicTax.parseRate("0.005000000000000000"),
        )
    }

    @Test
    fun `parseRate falls back on null, blank, garbage and negative`() {
        assertEquals(TerraClassicTax.fallbackBurnTaxRate, TerraClassicTax.parseRate(null))
        assertEquals(TerraClassicTax.fallbackBurnTaxRate, TerraClassicTax.parseRate(""))
        assertEquals(TerraClassicTax.fallbackBurnTaxRate, TerraClassicTax.parseRate("   "))
        assertEquals(TerraClassicTax.fallbackBurnTaxRate, TerraClassicTax.parseRate("not-a-number"))
        assertEquals(TerraClassicTax.fallbackBurnTaxRate, TerraClassicTax.parseRate("-0.1"))
    }

    @Test
    fun `parseRate falls back when the rate exceeds the sanity ceiling`() {
        // A malformed "5" would otherwise be applied as a 500% tax and massively overcharge.
        assertEquals(TerraClassicTax.fallbackBurnTaxRate, TerraClassicTax.parseRate("5"))
        assertEquals(TerraClassicTax.fallbackBurnTaxRate, TerraClassicTax.parseRate("0.2"))
        // A plausible governance rate at/under the ceiling is accepted as-is.
        assertEquals(BigDecimal("0.012"), TerraClassicTax.parseRate("0.012"))
        assertEquals(TerraClassicTax.maxBurnTaxRate, TerraClassicTax.parseRate("0.1"))
    }

    @Test
    fun `isBankDenom only true for non-native non-contract non-ibc denoms`() {
        assertTrue(TerraClassicTax.isBankDenom("uusd", isNativeToken = false))
        assertFalse(TerraClassicTax.isBankDenom("uusd", isNativeToken = true))
        assertFalse(TerraClassicTax.isBankDenom("terra1abc", isNativeToken = false))
        assertFalse(TerraClassicTax.isBankDenom("ibc/ABC123", isNativeToken = false))
        assertFalse(TerraClassicTax.isBankDenom("factory/terra1/foo", isNativeToken = false))
    }

    @Test
    fun `baseGas picks uusd for bank denom and uluna otherwise`() {
        assertEquals(
            TerraClassicTax.UUSD_BASE_GAS,
            TerraClassicTax.baseGas("uusd", isNativeToken = false),
        )
        assertEquals(
            TerraClassicTax.ULUNA_BASE_GAS,
            TerraClassicTax.baseGas("", isNativeToken = true),
        )
        assertEquals(
            TerraClassicTax.ULUNA_BASE_GAS,
            TerraClassicTax.baseGas("terra1abc", isNativeToken = false),
        )
    }

    @Test
    fun `taxPaidInSendDenom is true for native LUNC and USTC bank denom only`() {
        assertTrue(TerraClassicTax.taxPaidInSendDenom("", isNativeToken = true)) // LUNC
        assertTrue(TerraClassicTax.taxPaidInSendDenom("uusd", isNativeToken = false)) // USTC
        assertFalse(TerraClassicTax.taxPaidInSendDenom("terra1abc", isNativeToken = false)) // CW20
        assertFalse(TerraClassicTax.taxPaidInSendDenom("ibc/ABC", isNativeToken = false)) // IBC
    }
}
