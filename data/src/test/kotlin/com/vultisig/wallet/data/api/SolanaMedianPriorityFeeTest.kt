package com.vultisig.wallet.data.api

import java.math.BigInteger
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class SolanaMedianPriorityFeeTest {

    private val fallback = 1_000_000.toBigInteger()

    @Test
    fun `empty sample set returns the fallback`() {
        assertEquals(fallback, solanaMedianPriorityFee(emptyList(), fallback))
    }

    @Test
    fun `single sample returns that sample`() {
        assertEquals(
            500.toBigInteger(),
            solanaMedianPriorityFee(listOf(500.toBigInteger()), fallback),
        )
    }

    @Test
    fun `odd sample count returns the middle element unchanged`() {
        val samples = listOf(100, 200, 300).map { it.toBigInteger() }
        assertEquals(200.toBigInteger(), solanaMedianPriorityFee(samples, fallback))
    }

    @Test
    fun `even sample count averages the two central elements`() {
        // Ticket regression case: upper-middle (800) is wrong, true median is (200+800)/2 = 500.
        val samples = listOf(100, 200, 800, 900).map { it.toBigInteger() }
        assertEquals(500.toBigInteger(), solanaMedianPriorityFee(samples, fallback))
    }

    @Test
    fun `two samples averages both`() {
        val samples = listOf(100, 300).map { it.toBigInteger() }
        assertEquals(200.toBigInteger(), solanaMedianPriorityFee(samples, fallback))
    }

    @Test
    fun `even sample count with an odd sum truncates down instead of rounding`() {
        val samples = listOf(100, 100, 201, 300).map { it.toBigInteger() }
        // (100 + 201) / 2 = 150 (BigInteger division truncates toward zero for non-negative
        // values).
        assertEquals(150.toBigInteger(), solanaMedianPriorityFee(samples, fallback))
    }

    @Test
    fun `large values do not overflow`() {
        val samples = listOf(BigInteger("9999999999999999999"), BigInteger("10000000000000000001"))
        assertEquals(BigInteger("10000000000000000000"), solanaMedianPriorityFee(samples, fallback))
    }
}
