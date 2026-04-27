package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SwapQuoteRepositoryThorChainStreamingTest {

    private val thorChainApi: ThorChainApi = mockk()

    private val repository =
        SwapQuoteRepositoryImpl(
            thorChainApi = thorChainApi,
            mayaChainApi = mockk(),
            oneInchApi = mockk(),
            liFiChainApi = mockk(),
            jupiterApi = mockk(),
            kyberApi = mockk(),
        )

    private val btc =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "",
            address = "bc1qsrc",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private val trx =
        Coin(
            chain = Chain.Tron,
            ticker = "TRX",
            logo = "",
            address = "TDst",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun thorQuote(
        expectedAmountOut: String,
        feesTotal: String,
        maxStreamingQuantity: Int = 10,
        error: String? = null,
    ) =
        THORChainSwapQuote(
            dustThreshold = null,
            expectedAmountOut = expectedAmountOut,
            expiry = BigInteger.valueOf(9999999),
            fees = Fees(affiliate = "0", asset = feesTotal, outbound = "0", total = feesTotal),
            inboundAddress = "thorInbound",
            inboundConfirmationBlocks = null,
            inboundConfirmationSeconds = null,
            maxStreamingQuantity = maxStreamingQuantity,
            memo = "SWAP:TRON.TRX:TDst",
            notes = "",
            outboundDelayBlocks = BigInteger.ZERO,
            outboundDelaySeconds = BigInteger.ZERO,
            recommendedMinAmountIn = "100000",
            streamingSwapBlocks = BigInteger.ZERO,
            totalSwapSeconds = null,
            warning = "",
            router = null,
            error = error,
        )

    @Test
    fun `rapid slippage below threshold returns rapid without fetching streaming`() = runTest {
        // fees=100, out=9900 → slippage=100*10000/10000=100 bps ≤ 300
        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "0" }) } returns
            THORChainSwapQuoteDeserialized.Result(
                thorQuote(expectedAmountOut = "9900", feesTotal = "100")
            )

        val result =
            repository.getSwapQuote(
                dstAddress = "TDst",
                srcToken = btc,
                dstToken = trx,
                tokenValue = TokenValue(value = BigInteger("100000000"), token = btc),
            )

        coVerify(exactly = 0) { thorChainApi.getSwapQuotes(match { it.interval == "1" }) }
        val thorResult = result as SwapQuote.ThorChain
        assertEquals("9900", thorResult.data.expectedAmountOut)
    }

    @Test
    fun `rapid slippage above threshold and streaming better returns streaming quote`() = runTest {
        // fees=4000, out=6000 → slippage=4000*10000/10000=4000 bps > 300
        val rapidData =
            thorQuote(expectedAmountOut = "6000", feesTotal = "4000", maxStreamingQuantity = 5)
        val streamingData = thorQuote(expectedAmountOut = "7500", feesTotal = "500")

        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "0" }) } returns
            THORChainSwapQuoteDeserialized.Result(rapidData)
        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "1" }) } returns
            THORChainSwapQuoteDeserialized.Result(streamingData)

        val result =
            repository.getSwapQuote(
                dstAddress = "TDst",
                srcToken = btc,
                dstToken = trx,
                tokenValue = TokenValue(value = BigInteger("100000000"), token = btc),
            )

        coVerify(exactly = 1) {
            thorChainApi.getSwapQuotes(match { it.interval == "1" && it.streamingQuantity == 5 })
        }
        val thorResult = result as SwapQuote.ThorChain
        assertEquals("7500", thorResult.data.expectedAmountOut)
    }

    @Test
    fun `rapid slippage above threshold but streaming worse returns rapid quote`() = runTest {
        // fees=4000, out=6000 → slippage > 300; streaming out=5500 < rapid 6000 → rapid wins
        val rapidData =
            thorQuote(expectedAmountOut = "6000", feesTotal = "4000", maxStreamingQuantity = 3)
        val streamingData = thorQuote(expectedAmountOut = "5500", feesTotal = "500")

        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "0" }) } returns
            THORChainSwapQuoteDeserialized.Result(rapidData)
        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "1" }) } returns
            THORChainSwapQuoteDeserialized.Result(streamingData)

        val result =
            repository.getSwapQuote(
                dstAddress = "TDst",
                srcToken = btc,
                dstToken = trx,
                tokenValue = TokenValue(value = BigInteger("100000000"), token = btc),
            )

        val thorResult = result as SwapQuote.ThorChain
        assertEquals("6000", thorResult.data.expectedAmountOut)
    }

    @Test
    fun `streaming fetch exception falls back to rapid silently`() = runTest {
        // fees=4000, out=6000 → slippage > 300; streaming call throws → rapid returned
        val rapidData = thorQuote(expectedAmountOut = "6000", feesTotal = "4000")

        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "0" }) } returns
            THORChainSwapQuoteDeserialized.Result(rapidData)
        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "1" }) } throws
            RuntimeException("rate limited")

        val result =
            repository.getSwapQuote(
                dstAddress = "TDst",
                srcToken = btc,
                dstToken = trx,
                tokenValue = TokenValue(value = BigInteger("100000000"), token = btc),
            )

        val thorResult = result as SwapQuote.ThorChain
        assertEquals("6000", thorResult.data.expectedAmountOut)
    }

    @Test
    fun `streaming quote with error field falls back to rapid`() = runTest {
        val rapidData = thorQuote(expectedAmountOut = "6000", feesTotal = "4000")
        val streamingErrorData =
            thorQuote(expectedAmountOut = "7000", feesTotal = "100", error = "pool unavailable")

        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "0" }) } returns
            THORChainSwapQuoteDeserialized.Result(rapidData)
        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "1" }) } returns
            THORChainSwapQuoteDeserialized.Result(streamingErrorData)

        val result =
            repository.getSwapQuote(
                dstAddress = "TDst",
                srcToken = btc,
                dstToken = trx,
                tokenValue = TokenValue(value = BigInteger("100000000"), token = btc),
            )

        val thorResult = result as SwapQuote.ThorChain
        assertEquals("6000", thorResult.data.expectedAmountOut)
    }

    @Test
    fun `streaming api error response falls back to rapid`() = runTest {
        val rapidData = thorQuote(expectedAmountOut = "6000", feesTotal = "4000")

        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "0" }) } returns
            THORChainSwapQuoteDeserialized.Result(rapidData)
        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "1" }) } returns
            THORChainSwapQuoteDeserialized.Error(THORChainSwapQuoteError("not found"))

        val result =
            repository.getSwapQuote(
                dstAddress = "TDst",
                srcToken = btc,
                dstToken = trx,
                tokenValue = TokenValue(value = BigInteger("100000000"), token = btc),
            )

        val thorResult = result as SwapQuote.ThorChain
        assertEquals("6000", thorResult.data.expectedAmountOut)
    }

    @Test
    fun `rapid api error falls back to streaming quote`() = runTest {
        val streamingData = thorQuote(expectedAmountOut = "8000", feesTotal = "200")

        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "0" }) } returns
            THORChainSwapQuoteDeserialized.Error(THORChainSwapQuoteError("pool suspended"))
        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "1" }) } returns
            THORChainSwapQuoteDeserialized.Result(streamingData)

        val result =
            repository.getSwapQuote(
                dstAddress = "TDst",
                srcToken = btc,
                dstToken = trx,
                tokenValue = TokenValue(value = BigInteger("100000000"), token = btc),
            )

        // streaming fallback used, no streamingQuantity hint since rapid failed
        coVerify(exactly = 1) {
            thorChainApi.getSwapQuotes(match { it.interval == "1" && it.streamingQuantity == null })
        }
        val thorResult = result as SwapQuote.ThorChain
        assertEquals("8000", thorResult.data.expectedAmountOut)
    }

    @Test
    fun `rapid exception falls back to streaming quote`() = runTest {
        val streamingData = thorQuote(expectedAmountOut = "7000", feesTotal = "300")

        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "0" }) } throws
            RuntimeException("connection timeout")
        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "1" }) } returns
            THORChainSwapQuoteDeserialized.Result(streamingData)

        val result =
            repository.getSwapQuote(
                dstAddress = "TDst",
                srcToken = btc,
                dstToken = trx,
                tokenValue = TokenValue(value = BigInteger("100000000"), token = btc),
            )

        val thorResult = result as SwapQuote.ThorChain
        assertEquals("7000", thorResult.data.expectedAmountOut)
    }

    @Test
    fun `both rapid and streaming fail throws rapid error`() = runTest {
        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "0" }) } returns
            THORChainSwapQuoteDeserialized.Error(THORChainSwapQuoteError("pool suspended"))
        coEvery { thorChainApi.getSwapQuotes(match { it.interval == "1" }) } throws
            RuntimeException("also failed")

        assertThrows<SwapException> {
            repository.getSwapQuote(
                dstAddress = "TDst",
                srcToken = btc,
                dstToken = trx,
                tokenValue = TokenValue(value = BigInteger("100000000"), token = btc),
            )
        }
    }
}
