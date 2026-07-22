package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.median
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class SolanaMedianPriorityFeeTest {

    private val fallback = 1_000_000.toBigInteger()

    @Test
    fun `empty sample set returns the fallback`() {
        assertEquals(fallback, solanaMedianPriorityFee(emptyList(), fallback))
    }

    @Test
    fun `non-empty sample set delegates to the shared median calculation`() {
        val samples = listOf(100, 200, 800, 900).map { it.toBigInteger() }
        assertEquals(samples.median(), solanaMedianPriorityFee(samples, fallback))
    }
}
