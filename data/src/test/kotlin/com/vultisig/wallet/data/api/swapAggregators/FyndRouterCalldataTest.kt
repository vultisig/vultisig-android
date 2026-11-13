package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.api.swapAggregators.FyndCalldataFixtures.FEE_RECEIVER
import com.vultisig.wallet.data.api.swapAggregators.FyndCalldataFixtures.NATIVE
import com.vultisig.wallet.data.api.swapAggregators.FyndCalldataFixtures.USDC
import com.vultisig.wallet.data.api.swapAggregators.FyndCalldataFixtures.VAULT
import io.kotest.assertions.throwables.shouldThrow
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FyndRouterCalldataTest {

    @Test
    fun `decodes a fee-free singleSwap`() {
        val call = FyndRouterCalldata.decode(FyndCalldataFixtures.SINGLE_NO_FEE)
        assertEquals(BigInteger.TEN.pow(18), call.amountIn)
        assertEquals(NATIVE, call.tokenIn)
        assertEquals(USDC, call.tokenOut)
        assertEquals(BigInteger.valueOf(1_844_002_345), call.expectedAmountOut)
        assertEquals(BigInteger.valueOf(1_834_763_986), call.minAmountOut)
        assertEquals(VAULT, call.receiver)
        assertEquals(BigInteger.ZERO, call.clientFeeUnits)
        assertEquals("", call.clientSignature)
    }

    @Test
    fun `decodes a splitSwap with a signed client fee`() {
        val call = FyndRouterCalldata.decode(FyndCalldataFixtures.SPLIT_WITH_FEE)
        assertEquals(VAULT, call.receiver)
        assertEquals(BigInteger.valueOf(570_000), call.clientFeeUnits)
        assertEquals(FEE_RECEIVER, call.clientFeeReceiver)
        assertEquals(BigInteger.ZERO, call.maxClientContribution)
        assertEquals("ab".repeat(65), call.clientSignature)
        assertEquals(484, call.clientSignatureOffset)
    }

    @Test
    fun `refuses unknown selectors and truncated calldata`() {
        shouldThrow<IllegalArgumentException> { FyndRouterCalldata.decode("0xce25e49e" + "00".repeat(400)) }
        shouldThrow<IllegalArgumentException> {
            FyndRouterCalldata.decode(FyndCalldataFixtures.SPLIT_WITH_FEE.dropLast(200))
        }
        shouldThrow<IllegalArgumentException> { FyndRouterCalldata.decode("0x0c1a") }
    }
}
