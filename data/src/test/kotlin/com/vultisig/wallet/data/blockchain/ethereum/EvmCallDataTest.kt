package com.vultisig.wallet.data.blockchain.ethereum

import java.math.BigInteger
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class EvmCallDataTest {

    @Test
    fun `memo calldata gas is zero without a memo`() {
        assertEquals(BigInteger.ZERO, memoCallDataGas(null))
        assertEquals(BigInteger.ZERO, memoCallDataGas(""))
    }

    @Test
    fun `memo calldata gas charges 40 per byte of an ASCII memo`() {
        val memo = "SECURE+:thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt"

        assertEquals(BigInteger.valueOf(51L * 40), memoCallDataGas(memo))
    }

    @Test
    fun `memo calldata gas counts a hex memo as its decoded bytes with zero bytes at 10`() {
        // 0x00ff00ab -> two zero bytes and two non-zero bytes, not ten ASCII characters.
        assertEquals(BigInteger.valueOf(2L * 10 + 2L * 40), memoCallDataGas("0x00ff00ab"))
    }
}
