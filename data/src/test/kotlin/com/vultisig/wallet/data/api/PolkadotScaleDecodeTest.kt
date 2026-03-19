package com.vultisig.wallet.data.api

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class PolkadotScaleDecodeTest {

    // AccountInfo layout: nonce(u32)+consumers(u32)+providers(u32)+sufficients(u32) = 16-byte header
    private val header = "01000000000000000100000000000000"

    @Test
    fun `returns zero for empty hex`() {
        assertEquals(BigInteger.ZERO, parsePolkadotFreeBalance(""))
    }

    @Test
    fun `returns zero for truncated response`() {
        assertEquals(BigInteger.ZERO, parsePolkadotFreeBalance(header.take(16)))
    }

    @Test
    fun `decodes small balance correctly`() {
        // free = 1000 planck → u128 LE: e8 03 00 ... 00
        val free = "e8030000000000000000000000000000"
        assertEquals(BigInteger.valueOf(1000), parsePolkadotFreeBalance(header + free))
    }

    @Test
    fun `decodes large balance correctly`() {
        // free = 100_000_000_000 planck (10 DOT) → u128 LE: 00 e8 76 48 17 00 ... 00
        val free = "00e87648170000000000000000000000"
        assertEquals(BigInteger.valueOf(100_000_000_000L), parsePolkadotFreeBalance(header + free))
    }

    @Test
    fun `returns zero for zero balance`() {
        val free = "00000000000000000000000000000000"
        assertEquals(BigInteger.ZERO, parsePolkadotFreeBalance(header + free))
    }
}
