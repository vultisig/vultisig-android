package com.vultisig.wallet.data.api

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LiFiChainApiIntegratorFeeTest {

    @Test
    fun `fee with no discount applies full 50 bps`() {
        val dstAmount = BigInteger.valueOf(1_000_000)
        // 50 bps = 0.5% -> 1_000_000 * 50 / 10_000 = 5_000
        val fee = LiFiChainApi.integratorFeeAmount(dstAmount)
        assertEquals(BigInteger.valueOf(5_000), fee)
    }

    @Test
    fun `fee with partial discount reduces bps`() {
        val dstAmount = BigInteger.valueOf(1_000_000)
        // discount 20 bps -> effective 30 bps -> 1_000_000 * 30 / 10_000 = 3_000
        val fee = LiFiChainApi.integratorFeeAmount(dstAmount, bpsDiscount = 20)
        assertEquals(BigInteger.valueOf(3_000), fee)
    }

    @Test
    fun `fee with full discount returns zero`() {
        val dstAmount = BigInteger.valueOf(1_000_000)
        val fee = LiFiChainApi.integratorFeeAmount(dstAmount, bpsDiscount = 50)
        assertEquals(BigInteger.ZERO, fee)
    }

    @Test
    fun `fee with discount exceeding fee bps returns zero`() {
        val dstAmount = BigInteger.valueOf(1_000_000)
        val fee = LiFiChainApi.integratorFeeAmount(dstAmount, bpsDiscount = 100)
        assertEquals(BigInteger.ZERO, fee)
    }

    @Test
    fun `fee with zero dst amount returns zero`() {
        val fee = LiFiChainApi.integratorFeeAmount(BigInteger.ZERO)
        assertEquals(BigInteger.ZERO, fee)
    }

    @Test
    fun `fee with large dst amount is computed correctly`() {
        // 1 ETH in wei = 10^18
        val oneEthWei = BigInteger("1000000000000000000")
        // 50 bps = 0.5% of 10^18 = 5 * 10^15
        val expected = BigInteger("5000000000000000")
        val fee = LiFiChainApi.integratorFeeAmount(oneEthWei)
        assertEquals(expected, fee)
    }
}
