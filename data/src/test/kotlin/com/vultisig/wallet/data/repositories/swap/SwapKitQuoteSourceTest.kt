package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.api.models.quotes.SwapKitQuoteRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitQuoteResponseJson
import com.vultisig.wallet.data.api.models.quotes.SwapKitRoute
import com.vultisig.wallet.data.api.models.quotes.SwapKitSwapRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitSwapResponseJson
import com.vultisig.wallet.data.api.models.quotes.SwapKitTxMeta
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pins the SwapKit quote-source contract: feature-flag and provider-cache gates short-circuit
 * before network I/O, multi-hop / THORChain / Maya routes are filtered client-side, ranking picks
 * the highest [SwapKitRoute.expectedBuyAmount], and Phase 1 EVM/Solana `meta.txType` responses are
 * unwrapped into [com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson]. Anything else
 * surfaces as a typed [SwapKitError] so the swap picker can fall back to another aggregator.
 */
internal class SwapKitQuoteSourceTest {

    private val api: SwapKitApi = mockk()
    private val cache: SwapKitProviderCache = mockk()
    private val config: SwapKitConfig = mockk()
    private val json = Json { ignoreUnknownKeys = true }

    private fun source(): SwapKitQuoteSource = SwapKitQuoteSource(api, config, cache, json)

    @Test
    fun `fetch throws NoRoutes when feature flag is disabled`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(false)

        val error = assertThrows<SwapKitError.NoRoutes> { source().fetch(request()) }
        assertTrue(error.message!!.contains("flag", ignoreCase = true))
    }

    @Test
    fun `fetch throws NoRoutes when source chain is not enabled in cache`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { cache.isEnabled(Chain.Ethereum) } returns false

        val error = assertThrows<SwapKitError.NoRoutes> { source().fetch(request()) }
        assertTrue(error.message!!.contains("source"))
    }

    @Test
    fun `fetch throws NoRoutes when destination chain is not enabled in cache`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { cache.isEnabled(Chain.Ethereum) } returns true
        coEvery { cache.isEnabled(Chain.Base) } returns false

        val error =
            assertThrows<SwapKitError.NoRoutes> { source().fetch(request(dstToken = baseCoin())) }
        assertTrue(error.message!!.contains("destination"))
    }

    @Test
    fun `fetch filters out multi-hop and Thor and Maya routes then picks highest expected amount`() =
        runTest {
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { cache.isEnabled(any()) } returns true

            val quoteRequest = slot<SwapKitQuoteRequest>()
            val swapRequest = slot<SwapKitSwapRequest>()
            coEvery { api.quote(capture(quoteRequest)) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "skip-thor",
                                providers = listOf("THORCHAIN"),
                                expectedBuy = "200",
                            ),
                            route(
                                routeId = "skip-maya",
                                providers = listOf("MAYACHAIN"),
                                expectedBuy = "300",
                            ),
                            route(
                                routeId = "skip-multihop",
                                providers = listOf("CHAINFLIP", "NEAR"),
                                expectedBuy = "500",
                            ),
                            route(
                                routeId = "loser",
                                providers = listOf("NEAR"),
                                expectedBuy = "10",
                            ),
                            route(
                                routeId = "winner",
                                providers = listOf("CHAINFLIP"),
                                expectedBuy = "100",
                            ),
                        )
                )
            coEvery { api.swap(capture(swapRequest)) } returns evmSwapResponse()

            source().fetch(request())

            assertEquals("winner", swapRequest.captured.routeId)
        }

    @Test
    fun `fetch throws NoRoutes when every route is filtered out`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { cache.isEnabled(any()) } returns true
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(
                        route(routeId = "thor", providers = listOf("THORCHAIN")),
                        route(routeId = "maya", providers = listOf("MAYA")),
                        route(routeId = "multi", providers = listOf("CHAINFLIP", "NEAR")),
                    )
            )

        assertThrows<SwapKitError.NoRoutes> { source().fetch(request()) }
    }

    @Test
    fun `fetch maps EVM tx response into EVMSwapQuoteJson with gas defaults`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { cache.isEnabled(any()) } returns true
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(
                        route(
                            routeId = "r-evm",
                            providers = listOf("CHAINFLIP"),
                            expectedBuy = "42",
                        )
                    )
            )
        // gas omitted in EVM tx — fetcher must fall back to the project's default swap gas unit so
        // the downstream EVM signer never broadcasts a 0-gas transaction.
        coEvery { api.swap(any()) } returns
            evmSwapResponse(
                gas = null,
                gasPrice = "20",
                from = "0xfrom",
                to = "0xto",
                data = "0xdeadbeef",
                value = "1",
                expectedBuy = "42",
            )

        val result = source().fetch(request()) as SwapQuoteResult.Evm

        assertEquals("42", result.data.dstAmount)
        assertEquals("0xfrom", result.data.tx.from)
        assertEquals("0xto", result.data.tx.to)
        assertEquals("0xdeadbeef", result.data.tx.data)
        assertEquals("1", result.data.tx.value)
        assertEquals("20", result.data.tx.gasPrice)
        assertEquals(EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT, result.data.tx.gas)
    }

    @Test
    fun `fetch maps Solana tx response into EVMSwapQuoteJson with base64 in data`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { cache.isEnabled(any()) } returns true
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r-sol", providers = listOf("NEAR"), expectedBuy = "9"))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                swapId = "swap-1",
                tx = buildJsonObject { put("swapTransaction", JsonPrimitive("BASE64SOLANA")) },
                meta = SwapKitTxMeta(txType = "SOLANA"),
                expectedBuyAmount = "9",
            )

        val result = source().fetch(request()) as SwapQuoteResult.Evm

        assertEquals("BASE64SOLANA", result.data.tx.data)
        assertEquals("9", result.data.dstAmount)
    }

    @Test
    fun `fetch throws UnsupportedTxType for non-EVM, non-Solana txType`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { cache.isEnabled(any()) } returns true
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-psbt", providers = listOf("CHAINFLIP")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonObject(emptyMap()),
                meta = SwapKitTxMeta(txType = "PSBT"),
            )

        assertThrows<SwapKitError.UnsupportedTxType> { source().fetch(request()) }
    }

    @Test
    fun `fetch sends decimal sellAmount and dotted CHAIN-TICKER asset identifiers`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { cache.isEnabled(any()) } returns true

        val captured = slot<SwapKitQuoteRequest>()
        coEvery { api.quote(capture(captured)) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns evmSwapResponse()

        // 0.0086 ETH = 8_600_000_000_000_000 wei (18 decimals).
        val src = ethCoin().copy(isNativeToken = true)
        source()
            .fetch(
                request(
                    srcToken = src,
                    tokenValue = TokenValue(value = BigInteger("8600000000000000"), token = src),
                )
            )

        assertEquals("ETH.ETH", captured.captured.sellAsset)
        // Trailing-zero stripping verifies the formatter — bug-prone if the wei → decimal
        // conversion accidentally keeps full precision and SwapKit reads it as a larger number.
        assertEquals("0.0086", captured.captured.sellAmount)
    }

    @Test
    fun `fetch encodes ERC-20 token asset as CHAIN-TICKER-CONTRACT`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { cache.isEnabled(any()) } returns true

        val captured = slot<SwapKitQuoteRequest>()
        coEvery { api.quote(capture(captured)) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns evmSwapResponse()

        val usdc =
            ethCoin()
                .copy(
                    ticker = "USDC",
                    isNativeToken = false,
                    contractAddress = "0xa0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                    decimal = 6,
                )
        source()
            .fetch(
                request(
                    srcToken = usdc,
                    tokenValue = TokenValue(value = BigInteger("10000000"), token = usdc),
                )
            )

        assertEquals(
            "ETH.USDC-0xa0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            captured.captured.sellAsset,
        )
        assertEquals("10", captured.captured.sellAmount)
    }

    @Test
    fun `fetch passes affiliateBps from the request through to the SwapKit quote call`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { cache.isEnabled(any()) } returns true

        val captured = slot<SwapKitQuoteRequest>()
        coEvery { api.quote(capture(captured)) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns evmSwapResponse()

        source().fetch(request(affiliateBps = 25))

        assertEquals(25, captured.captured.affiliateFee)
        coVerify(exactly = 1) { api.quote(any()) }
        coVerify(exactly = 1) { api.swap(any()) }
    }

    @Test
    fun `fetch wraps unexpected exceptions as SwapKitError Network preserving the cause`() =
        runTest {
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { cache.isEnabled(any()) } returns true
            val boom = RuntimeException("DNS failure")
            coEvery { api.quote(any()) } throws boom

            val error = assertThrows<SwapKitError.Network> { source().fetch(request()) }
            assertEquals(boom, error.cause)
        }

    // ---- helpers ----

    private fun request(
        srcToken: Coin = ethCoin(),
        dstToken: Coin = solanaCoin(),
        tokenValue: TokenValue = TokenValue(value = BigInteger.ONE, token = srcToken),
        affiliateBps: Int = 0,
    ) =
        SwapQuoteRequest(
            srcToken = srcToken,
            dstToken = dstToken,
            tokenValue = tokenValue,
            srcAddress = "0xsrc",
            dstAddress = "0xdst",
            affiliateBps = affiliateBps,
        )

    private fun route(routeId: String, providers: List<String>, expectedBuy: String? = "1") =
        SwapKitRoute(routeId = routeId, providers = providers, expectedBuyAmount = expectedBuy)

    private fun evmSwapResponse(
        gas: String? = "200000",
        gasPrice: String? = "10",
        from: String? = "0xfrom",
        to: String = "0xto",
        data: String = "0xdata",
        value: String = "0",
        expectedBuy: String? = "1",
    ) =
        SwapKitSwapResponseJson(
            swapId = "swap-id",
            tx =
                buildJsonObject {
                    from?.let { put("from", JsonPrimitive(it)) }
                    put("to", JsonPrimitive(to))
                    put("data", JsonPrimitive(data))
                    put("value", JsonPrimitive(value))
                    gas?.let { put("gas", JsonPrimitive(it)) }
                    gasPrice?.let { put("gasPrice", JsonPrimitive(it)) }
                },
            meta = SwapKitTxMeta(txType = "EVM"),
            expectedBuyAmount = expectedBuy,
        )

    private fun ethCoin() =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xsrc",
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = "eth",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun baseCoin() = ethCoin().copy(chain = Chain.Base)

    private fun solanaCoin() =
        Coin(
            chain = Chain.Solana,
            ticker = "SOL",
            logo = "",
            address = "soldest",
            decimal = 9,
            hexPublicKey = "pub",
            priceProviderID = "solana",
            contractAddress = "",
            isNativeToken = true,
        )
}
