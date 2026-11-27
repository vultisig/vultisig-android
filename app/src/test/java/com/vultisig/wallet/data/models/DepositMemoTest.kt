package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class DepositMemoTest {

    // Expected memos follow the BONDYIELD examples in https://dev.thorchain.org/concepts/memos.html
    @Test
    fun `bond yield memo pays the share out to the payout address in basis points`() {
        assertEquals(
            "BONDYIELD:$NODE_ADDRESS:4000:$THOR_ADDRESS",
            DepositMemo.BondYield(NODE_ADDRESS, 40, THOR_ADDRESS).toString(),
        )
        assertEquals(
            "BONDYIELD:$NODE_ADDRESS:100:$THOR_ADDRESS",
            DepositMemo.BondYield(NODE_ADDRESS, 1, THOR_ADDRESS).toString(),
        )
        assertEquals(
            "BONDYIELD:$NODE_ADDRESS:10000:$THOR_ADDRESS",
            DepositMemo.BondYield(NODE_ADDRESS, 100, THOR_ADDRESS).toString(),
        )
    }

    @Test
    fun `bond yield memo disables payouts without a target at zero`() {
        assertEquals(
            "BONDYIELD:$NODE_ADDRESS:0",
            DepositMemo.BondYield(NODE_ADDRESS, 0, THOR_ADDRESS).toString(),
        )
    }

    @Test
    fun `bond yield memo rejects a percentage outside 0 to 100`() {
        assertThrows<IllegalArgumentException> {
            DepositMemo.BondYield(NODE_ADDRESS, -1, THOR_ADDRESS)
        }
        assertThrows<IllegalArgumentException> {
            DepositMemo.BondYield(NODE_ADDRESS, 101, THOR_ADDRESS)
        }
    }

    @Test
    fun `add liquidity memo without paired address`() {
        val memo = DepositMemo.AddLiquidity(pool = "BTC.BTC")
        assertEquals("+:BTC.BTC", memo.toString())
    }

    @Test
    fun `add liquidity memo with paired address`() {
        val memo = DepositMemo.AddLiquidity(pool = "BTC.BTC", pairedAddress = THOR_ADDRESS)
        assertEquals("+:BTC.BTC:$THOR_ADDRESS", memo.toString())
    }

    @Test
    fun `add liquidity memo treats blank paired address as absent`() {
        val memo = DepositMemo.AddLiquidity(pool = "BTC.BTC", pairedAddress = "   ")
        assertEquals("+:BTC.BTC", memo.toString())
    }

    @Test
    fun `remove liquidity memo formats basis points`() {
        val memo = DepositMemo.RemoveLiquidity(pool = "ETH.ETH", basisPoints = 5000)
        assertEquals("-:ETH.ETH:5000", memo.toString())
    }

    private companion object {
        const val THOR_ADDRESS = "thor12a9rpf9u2ulwuezxkh6uas4au7xnde8umdua5t"
        const val NODE_ADDRESS = "thor19m4kqulyqvya339jfja84h6qp8tkjgxuxa4n4a"
    }
}
