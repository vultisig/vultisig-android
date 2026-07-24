package com.vultisig.wallet.data.utils

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class BigIntegerExtensionsTest {

    @Test
    fun `median of an empty list is null`() {
        assertNull(emptyList<BigInteger>().median())
    }

    @Test
    fun `median of a single-element list is that element`() {
        assertEquals(500.toBigInteger(), listOf(500.toBigInteger()).median())
    }

    @Test
    fun `median of an odd-length list is the middle element`() {
        val samples = listOf(100, 200, 300).map { it.toBigInteger() }
        assertEquals(200.toBigInteger(), samples.median())
    }

    @Test
    fun `median of an even-length list averages the two central elements`() {
        // Regression case: the upper-middle element (800) is wrong; the true median is
        // (200+800)/2 = 500.
        val samples = listOf(100, 200, 800, 900).map { it.toBigInteger() }
        assertEquals(500.toBigInteger(), samples.median())
    }

    @Test
    fun `median of two elements averages both`() {
        val samples = listOf(100, 300).map { it.toBigInteger() }
        assertEquals(200.toBigInteger(), samples.median())
    }

    @Test
    fun `median of an even-length list with an odd sum truncates down instead of rounding`() {
        val samples = listOf(100, 100, 201, 300).map { it.toBigInteger() }
        // (100 + 201) / 2 = 150 (BigInteger division truncates toward zero for non-negative
        // values).
        assertEquals(150.toBigInteger(), samples.median())
    }

    @Test
    fun `median of a ten-element list matches the real getFeeHistory window shape`() {
        val samples = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100).map { it.toBigInteger() }
        assertEquals(55.toBigInteger(), samples.median()) // avg(50, 60) = 55
    }

    @Test
    fun `median of large values does not overflow`() {
        val samples = listOf(BigInteger("9999999999999999999"), BigInteger("10000000000000000001"))
        assertEquals(BigInteger("10000000000000000000"), samples.median())
    }
}
