package com.vultisig.wallet.data.models.payload

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vultisig.keysign.v1.TransactionType

class CosmosEffectiveGasLimitTest {

    private fun cosmos(gasLimit: BigInteger?) =
        BlockChainSpecific.Cosmos(
            accountNumber = BigInteger.ONE,
            sequence = BigInteger.ZERO,
            gas = BigInteger("7500"),
            ibcDenomTraces = null,
            transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            gasLimit = gasLimit,
        )

    @Test
    fun `unset gas limit falls back to the default`() {
        assertEquals(300_000L, cosmos(null).effectiveGasLimit(default = 300_000L))
    }

    @Test
    fun `relayed gas limit wins over the default`() {
        assertEquals(550_000L, cosmos(BigInteger("550000")).effectiveGasLimit(default = 300_000L))
    }

    @Test
    fun `explicit zero falls back to the default`() {
        assertEquals(300_000L, cosmos(BigInteger.ZERO).effectiveGasLimit(default = 300_000L))
    }

    @Test
    fun `negative gas limit falls back to the default`() {
        assertEquals(300_000L, cosmos(BigInteger("-1")).effectiveGasLimit(default = 300_000L))
    }

    @Test
    fun `largest Long gas limit is kept`() {
        assertEquals(
            Long.MAX_VALUE,
            cosmos(BigInteger.valueOf(Long.MAX_VALUE)).effectiveGasLimit(default = 300_000L),
        )
    }

    @Test
    fun `gas limit beyond Long range is rejected`() {
        assertThrows<IllegalArgumentException> {
            cosmos(BigInteger.valueOf(Long.MAX_VALUE).inc()).effectiveGasLimit(default = 300_000L)
        }
    }
}
