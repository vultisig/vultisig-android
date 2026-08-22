package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteError
import com.vultisig.wallet.data.api.models.quotes.ThorChainSwapQuoteRequest
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ThorChainQuoteSourceTest {

    private val thorChainApi: ThorChainApi = mockk()

    private fun source() = ThorChainQuoteSource(thorChainApi)

    private fun coin(
        chain: Chain,
        ticker: String,
        contractAddress: String,
        isNativeToken: Boolean,
    ) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = isNativeToken,
        )

    private fun quote() =
        THORChainSwapQuote(
            dustThreshold = null,
            expectedAmountOut = "1000000",
            expiry = BigInteger.ZERO,
            fees = Fees(affiliate = "0", asset = "0", outbound = "0", total = "0"),
            inboundAddress = "thorInboundAddress",
            inboundConfirmationBlocks = null,
            inboundConfirmationSeconds = null,
            // Zero disables the streaming-fallback path, keeping this a single-request test.
            maxStreamingQuantity = 0,
            memo = "=:AVAX-USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7:thor1dst",
            notes = "",
            outboundDelayBlocks = BigInteger.ZERO,
            outboundDelaySeconds = BigInteger.ZERO,
            recommendedMinAmountIn = "0",
            streamingSwapBlocks = BigInteger.ZERO,
            totalSwapSeconds = null,
            warning = "",
            router = null,
            error = null,
        )

    @Test
    fun `secured-asset destination is quoted with the raw dash denom, not a dot-normalized name`() =
        runTest {
            val requestSlot = slot<ThorChainSwapQuoteRequest>()
            coEvery { thorChainApi.getSwapQuotes(capture(requestSlot)) } returns
                THORChainSwapQuoteDeserialized.Result(quote())

            val btc = coin(Chain.Bitcoin, "BTC", "", isNativeToken = true)
            val securedUsdt =
                coin(
                    Chain.ThorChain,
                    "USDT",
                    "avax-usdt-0xdac17f958d2ee523a2206206994597c13d831ec7",
                    isNativeToken = false,
                )

            source()
                .fetch(
                    SwapQuoteRequest(
                        srcToken = btc,
                        dstToken = securedUsdt,
                        tokenValue =
                            TokenValue(value = BigInteger.valueOf(100_000_000), token = btc),
                        dstAddress = "thor1dst",
                    )
                )

            assertEquals(
                "avax-usdt-0xdac17f958d2ee523a2206206994597c13d831ec7",
                requestSlot.captured.toAsset,
            )
        }

    @Test
    fun `a poolless pair is refused without paying for the streaming round-trip`() = runTest {
        coEvery { thorChainApi.getSwapQuotes(any()) } returns
            THORChainSwapQuoteDeserialized.Error(
                THORChainSwapQuoteError(
                    "failed to calculate min swap amount: fail to convert dest fee to src asset " +
                        "pool does not exist"
                )
            )

        val rune = coin(Chain.ThorChain, "RUNE", "", isNativeToken = true)
        val kuji = coin(Chain.ThorChain, "KUJI", "thor.kuji", isNativeToken = false)

        assertThrows<SwapException.SwapRouteNotAvailable> {
            source()
                .fetch(
                    SwapQuoteRequest(
                        srcToken = rune,
                        dstToken = kuji,
                        tokenValue =
                            TokenValue(value = BigInteger.valueOf(100_000_000), token = rune),
                        dstAddress = "thor1dst",
                    )
                )
        }

        coVerify(exactly = 1) { thorChainApi.getSwapQuotes(any()) }
    }

    @Test
    fun `an amount the rapid path refuses still reaches the streaming request`() = runTest {
        // Streaming splits the swap, so a minimum the rapid interval cannot meet is not the pair's
        // final answer — unlike a missing pool, this one is worth asking again.
        coEvery { thorChainApi.getSwapQuotes(any()) } returns
            THORChainSwapQuoteDeserialized.Error(
                THORChainSwapQuoteError("amount less than min swap amount")
            )

        val btc = coin(Chain.Bitcoin, "BTC", "", isNativeToken = true)
        val rune = coin(Chain.ThorChain, "RUNE", "", isNativeToken = true)

        assertThrows<SwapException.SmallSwapAmount> {
            source()
                .fetch(
                    SwapQuoteRequest(
                        srcToken = btc,
                        dstToken = rune,
                        tokenValue = TokenValue(value = BigInteger.valueOf(1_000), token = btc),
                        dstAddress = "thor1dst",
                    )
                )
        }

        coVerify(exactly = 2) { thorChainApi.getSwapQuotes(any()) }
    }
}
