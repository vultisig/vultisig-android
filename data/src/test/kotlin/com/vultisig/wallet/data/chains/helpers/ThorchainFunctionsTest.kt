package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThorchainFunctionsTest {

    @Test
    fun `rujiRewardsMemo builds correct memo string`() {
        val memo =
            ThorchainFunctions.rujiRewardsMemo(
                contractAddress = "cosmos1contractabc",
                tokenAmountInt = BigInteger.valueOf(1_000_000L),
            )
        assertEquals("claim:cosmos1contractabc:1000000", memo)
    }

    @Test
    fun `rujiRewardsMemo handles zero amount`() {
        val memo =
            ThorchainFunctions.rujiRewardsMemo(
                contractAddress = "cosmos1contract",
                tokenAmountInt = BigInteger.ZERO,
            )
        assertEquals("claim:cosmos1contract:0", memo)
    }

    @Test
    fun `tcyUnstakeMemo builds correct memo string`() {
        val memo = ThorchainFunctions.tcyUnstakeMemo(basisPoints = 5000)
        assertEquals("TCY-:5000", memo)
    }

    @Test
    fun `tcyUnstakeMemo handles zero basis points`() {
        val memo = ThorchainFunctions.tcyUnstakeMemo(basisPoints = 0)
        assertEquals("TCY-:0", memo)
    }

    @Test
    fun `tcyUnstakeMemo handles max basis points`() {
        val memo = ThorchainFunctions.tcyUnstakeMemo(basisPoints = 10000)
        assertEquals("TCY-:10000", memo)
    }
}
