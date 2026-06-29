package com.vultisig.wallet.data.blockchain.ton

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class TonNominatorPoolTest {

    @Test
    fun `whales comments are capitalized Deposit and Withdraw`() {
        assertEquals("Deposit", TonNominatorPool.depositComment("whales"))
        assertEquals("Withdraw", TonNominatorPool.withdrawComment("whales"))
    }

    @Test
    fun `tf comments are short d and w`() {
        assertEquals("d", TonNominatorPool.depositComment("tf"))
        assertEquals("w", TonNominatorPool.withdrawComment("tf"))
    }

    @Test
    fun `unknown and liquidTF implementations have no comment`() {
        assertNull(TonNominatorPool.depositComment("liquidTF"))
        assertNull(TonNominatorPool.withdrawComment("liquidTF"))
        assertNull(TonNominatorPool.depositComment(null))
        assertNull(TonNominatorPool.depositComment("something-else"))
    }

    @Test
    fun `only whales and tf are nominator implementations`() {
        assertTrue(TonNominatorPool.isNominatorImplementation("whales"))
        assertTrue(TonNominatorPool.isNominatorImplementation("tf"))
        assertFalse(TonNominatorPool.isNominatorImplementation("liquidTF"))
        assertFalse(TonNominatorPool.isNominatorImplementation(null))
    }

    @Test
    fun `minimum deposit adds the one TON commission to min stake`() {
        val minStake = BigInteger.valueOf(50_000_000_000L) // 50 TON
        assertEquals(BigInteger.valueOf(51_000_000_000L), TonNominatorPool.minimumDeposit(minStake))
    }

    @Test
    fun `withdraw fee is fixed at 0_2 TON`() {
        assertEquals(BigInteger.valueOf(200_000_000L), TonNominatorPool.WITHDRAW_FEE)
    }
}
