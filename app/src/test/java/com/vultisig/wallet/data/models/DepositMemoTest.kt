package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DepositMemoTest {

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
    }
}
