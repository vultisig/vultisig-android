package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.utils.BigIntegerSerializerImpl
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Test

/**
 * Pins [SwapQuote.priceImpact] derivation: THORChain/Maya map the node's `slippage_bps` to a
 * fraction (iOS parity), EVM aggregators report none. Drift here would mis-feed the Price Impact
 * row.
 */
internal class SwapQuotePriceImpactTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule { contextual(BigIntegerSerializerImpl()) }
    }

    @Test
    fun `thorchain reads slippage_bps from inside fees, where the node sends it`() {
        // Trimmed from a live `ETH.ETH -> BTC.BTC` thornode quote: the figure lives in `fees` and
        // nowhere else. Reading it off the top level parsed clean and never rendered a row.
        val body =
            """
            {
              "dust_threshold": "10000",
              "expected_amount_out": "3157340",
              "expiry": 1788000000,
              "fees": {
                "asset": "BTC.BTC",
                "affiliate": "0",
                "outbound": "1161",
                "liquidity": "6382",
                "total": "7543",
                "slippage_bps": 19,
                "total_bps": 23
              },
              "inbound_address": "0xinbound",
              "max_streaming_quantity": 0,
              "notes": "",
              "outbound_delay_blocks": 0,
              "outbound_delay_seconds": 0,
              "recommended_min_amount_in": "1",
              "streaming_swap_blocks": 0,
              "warning": ""
            }
            """
                .trimIndent()

        val quote =
            SwapQuote.ThorChain(
                expectedDstValue = mockk(),
                fees = mockk(),
                expiredAt = mockk(),
                recommendedMinTokenValue = mockk(),
                data = json.decodeFromString<THORChainSwapQuote>(body),
            )

        assertEquals(19, quote.data.fees.slippageBps)
        assertEquals(BigDecimal("0.0019"), quote.priceImpact)
    }

    @Test
    fun `thorchain derives fractional price impact from slippage_bps`() {
        val quote = thorChainQuote(slippageBps = 133)
        assertEquals(BigDecimal("0.0133"), quote.priceImpact)
    }

    @Test
    fun `mayachain derives fractional price impact from slippage_bps`() {
        val quote =
            SwapQuote.MayaChain(
                expectedDstValue = mockk(),
                fees = mockk(),
                expiredAt = mockk(),
                recommendedMinTokenValue = mockk(),
                data = thorSwapQuote(slippageBps = 250),
            )
        assertEquals(BigDecimal("0.0250"), quote.priceImpact)
    }

    @Test
    fun `thorchain price impact is null when node omits slippage_bps`() {
        assertNull(thorChainQuote(slippageBps = null).priceImpact)
    }

    @Test
    fun `oneinch price impact defaults to null`() {
        val quote =
            SwapQuote.OneInch(
                expectedDstValue = mockk(),
                fees = mockk(),
                expiredAt = mockk(),
                data = mockk(),
                provider = "oneinch",
            )
        assertNull(quote.priceImpact)
    }

    @Test
    fun `oneinch carries SwapKit price impact when supplied`() {
        val quote =
            SwapQuote.OneInch(
                expectedDstValue = mockk(),
                fees = mockk(),
                expiredAt = mockk(),
                data = mockk(),
                provider = "swapkit",
                priceImpact = BigDecimal("0.0075"),
            )
        assertEquals(BigDecimal("0.0075"), quote.priceImpact)
    }

    private fun thorChainQuote(slippageBps: Int?) =
        SwapQuote.ThorChain(
            expectedDstValue = mockk(),
            fees = mockk(),
            expiredAt = mockk(),
            recommendedMinTokenValue = mockk(),
            data = thorSwapQuote(slippageBps),
        )

    private fun thorSwapQuote(slippageBps: Int?) =
        THORChainSwapQuote(
            dustThreshold = null,
            expectedAmountOut = "9950",
            expiry = BigInteger.valueOf(9_999_999),
            fees =
                Fees(
                    affiliate = "0",
                    asset = "50",
                    outbound = "0",
                    total = "50",
                    slippageBps = slippageBps,
                ),
            inboundAddress = "thorInbound",
            inboundConfirmationBlocks = null,
            inboundConfirmationSeconds = null,
            maxStreamingQuantity = 0,
            memo = null,
            notes = "",
            outboundDelayBlocks = BigInteger.ZERO,
            outboundDelaySeconds = BigInteger.ZERO,
            recommendedMinAmountIn = "100000",
            streamingSwapBlocks = BigInteger.ZERO,
            totalSwapSeconds = null,
            warning = "",
            router = null,
            error = null,
        )
}
