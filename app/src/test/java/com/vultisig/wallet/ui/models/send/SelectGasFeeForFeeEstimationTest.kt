package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SelectGasFeeForFeeEstimationTest {

    private val cardanoFee =
        TokenValue(value = BigInteger.valueOf(180_000L), unit = "ADA", decimals = 6)

    private val btcFee = TokenValue(value = BigInteger.valueOf(10L), unit = "BTC", decimals = 8)

    private val ethFee = TokenValue(value = BigInteger.valueOf(1L), unit = "ETH", decimals = 18)

    @Test
    fun `cardano returns the original gas fee unchanged - regression for fee showing 0`() {
        // planFee is the sentinel value 1 for Cardano because it skips the BTC UTXO planner.
        // The Cardano branch must run BEFORE the generic UTXO branch, otherwise the real
        // 180000 lovelace fee gets overwritten with 1 and the UI shows ~0 ADA.
        val result =
            selectGasFeeForFeeEstimation(
                chain = Chain.Cardano,
                gasFee = cardanoFee,
                planFee = 1L,
                evmGasSettings = null,
            )

        assertEquals(cardanoFee, result)
        assertEquals(BigInteger.valueOf(180_000L), result.value)
    }

    @Test
    fun `bitcoin with positive plan fee uses the plan fee value`() {
        val result =
            selectGasFeeForFeeEstimation(
                chain = Chain.Bitcoin,
                gasFee = btcFee,
                planFee = 1234L,
                evmGasSettings = null,
            )

        assertEquals(BigInteger.valueOf(1234L), result.value)
        assertEquals(btcFee.unit, result.unit)
    }

    @Test
    fun `bitcoin with non-positive plan fee falls back to the original gas fee`() {
        val result =
            selectGasFeeForFeeEstimation(
                chain = Chain.Bitcoin,
                gasFee = btcFee,
                planFee = 0L,
                evmGasSettings = null,
            )

        assertEquals(btcFee, result)
    }

    @Test
    fun `utxo chain with null plan fee throws InvalidTransactionDataException`() {
        assertThrows<InvalidTransactionDataException> {
            selectGasFeeForFeeEstimation(
                chain = Chain.Bitcoin,
                gasFee = btcFee,
                planFee = null,
                evmGasSettings = null,
            )
        }
    }

    @Test
    fun `ethereum uses the base fee from evm gas settings`() {
        val baseFee = BigInteger.valueOf(42L)
        val result =
            selectGasFeeForFeeEstimation(
                chain = Chain.Ethereum,
                gasFee = ethFee,
                planFee = null,
                evmGasSettings =
                    GasSettings.Eth(
                        baseFee = baseFee,
                        priorityFee = BigInteger.valueOf(2L),
                        gasLimit = BigInteger.valueOf(21_000L),
                    ),
            )

        assertEquals(baseFee, result.value)
    }

    @Test
    fun `non-utxo chain without evm settings returns the original gas fee`() {
        val result =
            selectGasFeeForFeeEstimation(
                chain = Chain.Solana,
                gasFee = ethFee,
                planFee = null,
                evmGasSettings = null,
            )

        assertEquals(ethFee, result)
    }

    @Test
    fun `bitcoin with non-null evm gas settings uses plan fee and ignores evm settings`() {
        val result =
            selectGasFeeForFeeEstimation(
                chain = Chain.Bitcoin,
                gasFee = btcFee,
                planFee = 500L,
                evmGasSettings =
                    GasSettings.Eth(
                        baseFee = BigInteger.valueOf(99L),
                        priorityFee = BigInteger.ONE,
                        gasLimit = BigInteger.valueOf(21_000L),
                    ),
            )

        assertEquals(BigInteger.valueOf(500L), result.value)
    }

    @Test
    fun `ethereum with zero base fee returns fee of zero`() {
        val result =
            selectGasFeeForFeeEstimation(
                chain = Chain.Ethereum,
                gasFee = ethFee,
                planFee = null,
                evmGasSettings =
                    GasSettings.Eth(
                        baseFee = BigInteger.ZERO,
                        priorityFee = BigInteger.valueOf(2L),
                        gasLimit = BigInteger.valueOf(21_000L),
                    ),
            )

        assertEquals(BigInteger.ZERO, result.value)
    }
}
