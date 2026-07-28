package com.vultisig.wallet.data.api.models.quotes

import com.vultisig.wallet.data.models.Chain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KyberSwapQuoteJsonTest {

    @Test
    fun `gasForChain applies the 2x safety multiplier to Mantle like the other Kyber-eligible chains`() {
        val quote = kyberQuote(gas = "600000")

        val mantleGas = quote.gasForChain(Chain.Mantle)
        val ethereumGas = quote.gasForChain(Chain.Ethereum)

        assertEquals(1_200_000L, mantleGas)
        assertEquals(ethereumGas, mantleGas)
    }

    @Test
    fun `gasForChain keeps the 1_6x multiplier for chains outside the safety-margin set`() {
        val quote = kyberQuote(gas = "600000")

        assertEquals(960_000L, quote.gasForChain(Chain.ZkSync))
    }

    @Test
    fun `gasForChain defaults base gas to 600000 when the quote omits gas`() {
        val quote = kyberQuote(gas = null)

        assertEquals(1_200_000L, quote.gasForChain(Chain.Mantle))
    }

    @Test
    fun `gasForChain defaults base gas to 600000 when gas is not numeric`() {
        val quote = kyberQuote(gas = "not-a-number")

        assertEquals(1_200_000L, quote.gasForChain(Chain.Mantle))
    }

    private fun kyberQuote(gas: String?) =
        KyberSwapQuoteJson(
            code = 0,
            message = "OK",
            data =
                KyberSwapQuoteData(
                    amountIn = "1000000",
                    amountInUsd = "1.0",
                    amountOut = "990000",
                    amountOutUsd = "0.99",
                    gas = gas,
                    gasUsd = "0.5",
                    data = "0xkyberdata",
                    routerAddress = "0xRouter",
                    transactionValue = "0",
                ),
            requestId = "req-1",
        )
}
