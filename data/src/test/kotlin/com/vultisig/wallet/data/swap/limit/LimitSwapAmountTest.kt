package com.vultisig.wallet.data.swap.limit

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LimitSwapAmountTest {

    @Test
    fun `is a no-op for 8-decimal sources`() {
        assertEquals(
            BigInteger.valueOf(100_000_000L),
            toThorchainFixedPoint(BigInteger.valueOf(100_000_000L), 8),
        )
    }

    @Test
    fun `scales an 18-decimal source down to 1e8`() {
        // 1 ETH -> 1e8, not the 1e18 that would be 1e10x too large.
        assertEquals(
            BigInteger.valueOf(100_000_000L),
            toThorchainFixedPoint(BigInteger.TEN.pow(18), 18),
        )
    }

    @Test
    fun `scales a 6-decimal source up to 1e8`() {
        assertEquals(
            BigInteger.valueOf(100_000_000L),
            toThorchainFixedPoint(BigInteger.valueOf(1_000_000L), 6),
        )
    }

    @Test
    fun `keeps precision on a fractional 18-decimal amount`() {
        // 1.5 ETH
        assertEquals(
            BigInteger.valueOf(150_000_000L),
            toThorchainFixedPoint(BigInteger.valueOf(15L) * BigInteger.TEN.pow(17), 18),
        )
    }

    @Test
    fun `floors sub-1e8 dust rather than rounding up`() {
        assertEquals(BigInteger.ZERO, toThorchainFixedPoint(BigInteger.ONE, 18))
    }

    @Test
    fun `converts a fixed-point amount back to natural units`() {
        assertEquals(
            0,
            BigDecimal("1").compareTo(fromThorchainFixedPoint(BigInteger.valueOf(100_000_000L))),
        )
        assertEquals(
            0,
            BigDecimal("1.5").compareTo(fromThorchainFixedPoint(BigInteger.valueOf(150_000_000L))),
        )
        assertEquals(
            0,
            BigDecimal("16").compareTo(fromThorchainFixedPoint(BigInteger.valueOf(1_600_000_000L))),
        )
    }
}
