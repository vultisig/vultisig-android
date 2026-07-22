package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.ThorChainSwapQuoteRequest
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
