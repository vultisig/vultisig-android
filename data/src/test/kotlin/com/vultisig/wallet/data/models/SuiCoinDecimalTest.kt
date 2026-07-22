package com.vultisig.wallet.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SuiCoinDecimalTest {

    @Test
    fun `SEND uses its real on-chain decimals of 6`() {
        assertEquals(6, Coins.Sui.SEND.decimal)
    }

    @Test
    fun `SEND is part of the curated Sui catalog`() {
        assertTrue(Coins.Sui.all.contains(Coins.Sui.SEND))
    }
}
