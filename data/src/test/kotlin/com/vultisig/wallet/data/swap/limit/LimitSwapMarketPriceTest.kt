package com.vultisig.wallet.data.swap.limit

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LimitSwapMarketPriceTest {

    @Test
    fun `sizes the probe to the target fiat notional`() {
        // 100 USD / 2000 USD-per-ETH = 0.05 ETH = 5e16 wei.
        assertEquals(BigInteger("50000000000000000"), getMarketProbeAmount(BigDecimal("2000"), 18))
    }

    @Test
    fun `never returns a probe that converts to zero on the 1e8 scale`() {
        // A 1e12-priced 18-decimal asset would size to 1e8 native units, which floors to 0 after
        // the
        // 1e8 conversion; clamp up to the minimum that survives.
        val probe = getMarketProbeAmount(BigDecimal("1000000000000"), 18)
        assertEquals(BigInteger.TEN.pow(10), probe)
        assertTrue(toThorchainFixedPoint(probe, 18) >= BigInteger.ONE)
    }

    @Test
    fun `falls back to one unit for a zero price`() {
        assertEquals(BigInteger.TEN.pow(8), getMarketProbeAmount(BigDecimal.ZERO, 8))
    }

    @Test
    fun `derives price from expected output, applying only the source decimals`() {
        // 1 ETH (1e18 native) quoting 0.04 BTC (4_000_000 in 1e8) -> price 0.04 BTC/ETH.
        val price =
            getLimitSwapMarketPrice(
                expectedAmountOut = "4000000",
                sourceAmount = BigInteger.TEN.pow(18),
                sourceDecimals = 18,
            )
        assertEquals(0, BigDecimal("0.04").compareTo(price))
    }

    @Test
    fun `rejects a zero source amount`() {
        assertThrows(IllegalArgumentException::class.java) {
            getLimitSwapMarketPrice("4000000", BigInteger.ZERO, 18)
        }
    }

    @Test
    fun `rejects a non-numeric expected output`() {
        assertThrows(IllegalArgumentException::class.java) {
            getLimitSwapMarketPrice("not-a-number", BigInteger.TEN.pow(18), 18)
        }
    }
}
