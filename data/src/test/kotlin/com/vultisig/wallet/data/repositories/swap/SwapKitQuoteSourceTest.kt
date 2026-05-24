package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.api.models.quotes.SwapKitFee
import com.vultisig.wallet.data.api.models.quotes.SwapKitQuoteRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitQuoteResponseJson
import com.vultisig.wallet.data.api.models.quotes.SwapKitRoute
import com.vultisig.wallet.data.api.models.quotes.SwapKitSwapRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitSwapResponseJson
import com.vultisig.wallet.data.api.models.quotes.SwapKitTxMeta
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pins the SwapKit quote-source contract: feature-flag gating, multi-hop / THORChain / Maya
 * filtering with the wire-format provider ids, ranking by [SwapKitRoute.expectedBuyAmount], Solana
 * base64-as-JsonPrimitive decoding (+ legacy object fallback), `SERIALIZED_BASE64` txType aliasing,
 * refusal of EVM routes missing `tx.gas`, inbound-fee extraction from `route.fees[]`, sub-provider
 * passthrough into [SwapQuoteResult.Evm.subProvider], and exception wrapping. No provider-cache
 * gate — `/v3/quote` is the authority on unsupported chains (mirrors iOS' fail-open cache).
 */
internal class SwapKitQuoteSourceTest {

    private val api: SwapKitApi = mockk()
    private val config: SwapKitConfig = mockk()
    private val json = Json { ignoreUnknownKeys = true }

    private fun source(): SwapKitQuoteSource = SwapKitQuoteSource(api, config, json)

    @Test
    fun `fetch throws NoRoutes when feature flag is disabled`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(false)

        val error = assertThrows<SwapKitError.NoRoutes> { source().fetch(request()) }
        assertTrue(error.message?.contains("flag", ignoreCase = true) == true)
    }

    @Test
    fun `fetch filters out multi-hop and Thor and Maya routes then picks highest expected amount`() =
        runTest {
            every { config.isFeatureEnabled } returns flowOf(true)

            val swapRequest = slot<SwapKitSwapRequest>()
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "skip-thor",
                                providers = listOf("THORCHAIN"),
                                expectedBuy = "200",
                            ),
                            route(
                                routeId = "skip-thor-streaming",
                                providers = listOf("THORCHAIN_STREAMING"),
                                expectedBuy = "250",
                            ),
                            route(
                                routeId = "skip-maya",
                                providers = listOf("MAYACHAIN"),
                                expectedBuy = "300",
                            ),
                            route(
                                routeId = "skip-maya-streaming",
                                providers = listOf("MAYACHAIN_STREAMING"),
                                expectedBuy = "400",
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
    fun `fetch throws NoRoutes when every route is filtered out including streaming variants`() =
        runTest {
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(routeId = "thor", providers = listOf("THORCHAIN")),
                            route(
                                routeId = "thor-streaming",
                                providers = listOf("THORCHAIN_STREAMING"),
                            ),
                            route(routeId = "maya", providers = listOf("MAYACHAIN")),
                            route(
                                routeId = "maya-streaming",
                                providers = listOf("MAYACHAIN_STREAMING"),
                            ),
                            route(routeId = "multi", providers = listOf("CHAINFLIP", "NEAR")),
                        )
                )

            assertThrows<SwapKitError.NoRoutes> { source().fetch(request()) }
        }

    @Test
    fun `fetch maps EVM tx response and surfaces sub-provider from swap response`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
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
        coEvery { api.swap(any()) } returns
            evmSwapResponse(
                gas = "200000",
                gasPrice = "20",
                from = "0xfrom",
                to = "0xto",
                data = "0xdeadbeef",
                value = "1",
                expectedBuy = "42",
                providers = listOf("CHAINFLIP"),
            )

        val result = source().fetch(request()) as SwapQuoteResult.Evm

        assertEquals("42000000000", result.data.dstAmount)
        assertEquals("0xfrom", result.data.tx.from)
        assertEquals("0xto", result.data.tx.to)
        assertEquals("0xdeadbeef", result.data.tx.data)
        assertEquals("1", result.data.tx.value)
        assertEquals("20", result.data.tx.gasPrice)
        assertEquals(200000L, result.data.tx.gas)
        assertEquals("CHAINFLIP", result.subProvider)
    }

    @Test
    fun `fetch refuses EVM route when tx_gas is missing`() = runTest {
        // Hard-coding a 600k L1 default would over-estimate Network Fee on L2s by multiples.
        // Refuse the route and let the picker rank another aggregator with a real gas estimate.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns evmSwapResponse(gas = null)

        assertThrows<SwapKitError.Decoding> { source().fetch(request()) }
    }

    @Test
    fun `fetch decodes Solana base64 from JsonPrimitive at the tx root`() = runTest {
        // SwapKit V3 returns the Solana tx as a bare base64 string on `tx`, not an object with
        // swapTransaction/message. The legacy wrapper-object decoder masked this bug in earlier
        // tests; this case pins the V3 wire shape explicitly.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r-sol", providers = listOf("NEAR"), expectedBuy = "9"))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                swapId = "swap-1",
                tx = JsonPrimitive("BASE64SOLANA"),
                meta = SwapKitTxMeta(txType = "SOLANA"),
                expectedBuyAmount = "9",
                providers = listOf("NEAR"),
            )

        val result = source().fetch(request()) as SwapQuoteResult.Evm

        assertEquals("BASE64SOLANA", result.data.tx.data)
        assertEquals("9000000000", result.data.dstAmount)
        assertEquals("NEAR", result.subProvider)
    }

    @Test
    fun `fetch decodes legacy Solana object shape with swapTransaction field`() = runTest {
        // Transitional wire shape — SwapKit flipped this once before. Source stays permissive.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r-sol", providers = listOf("NEAR"), expectedBuy = "9"))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = buildJsonObject { put("swapTransaction", JsonPrimitive("BASE64LEGACY")) },
                meta = SwapKitTxMeta(txType = "SOLANA"),
                expectedBuyAmount = "9",
            )

        val result = source().fetch(request()) as SwapQuoteResult.Evm

        assertEquals("BASE64LEGACY", result.data.tx.data)
    }

    @Test
    fun `fetch accepts SERIALIZED_BASE64 txType as Solana`() = runTest {
        // SwapKit upstream has flipped the Solana discriminator between SOLANA and
        // SERIALIZED_BASE64 before (per iOS commit 382b28f5f). Both must dispatch to the Solana
        // decoder; otherwise every real Solana route lands on UnsupportedTxType.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r-sol", providers = listOf("NEAR"), expectedBuy = "9"))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonPrimitive("BASE64SERIAL"),
                meta = SwapKitTxMeta(txType = "SERIALIZED_BASE64"),
                expectedBuyAmount = "9",
            )

        val result = source().fetch(request()) as SwapQuoteResult.Evm

        assertEquals("BASE64SERIAL", result.data.tx.data)
    }

    @Test
    fun `fetch surfaces inbound fee from route fees list in raw source-chain units`() = runTest {
        // Canonical SwapKit source-chain fee — iOS surfaces this via SwapKitService.inboundFee.
        // Without parsing fees[], Solana routes display $0 Network Fee while real Chainflip /
        // NEAR routes debit a real source-chain charge.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(
                        route(
                            routeId = "r-sol",
                            providers = listOf("NEAR"),
                            expectedBuy = "9",
                            fees =
                                listOf(
                                    SwapKitFee(
                                        type = "inbound",
                                        chain = "SOL",
                                        amount = "0.000005",
                                    ),
                                    SwapKitFee(type = "outbound", chain = "ETH", amount = "0.0001"),
                                ),
                        )
                    )
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonPrimitive("BASE64"),
                meta = SwapKitTxMeta(txType = "SOLANA"),
                expectedBuyAmount = "9",
            )

        val result = source().fetch(request(srcToken = solanaCoin())) as SwapQuoteResult.Evm

        // 0.000005 SOL * 10^9 = 5000 raw lamports. Embedded in tx.swapFee with an empty
        // tokenContract so resolveSwapFee falls back to srcNativeToken (SOL).
        assertEquals("5000", result.data.tx.swapFee)
        assertEquals("", result.data.tx.swapFeeTokenContract)
    }

    @Test
    fun `fetch zero inbound fee when fees list lacks an inbound entry for the source chain`() =
        runTest {
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "r",
                                providers = listOf("CHAINFLIP"),
                                expectedBuy = "42",
                                fees =
                                    listOf(
                                        SwapKitFee(
                                            type = "outbound",
                                            chain = "ETH",
                                            amount = "0.01",
                                        )
                                    ),
                            )
                        )
                )
            coEvery { api.swap(any()) } returns evmSwapResponse()

            val result = source().fetch(request()) as SwapQuoteResult.Evm

            assertEquals("0", result.data.tx.swapFee)
        }

    @Test
    fun `fetch throws Decoding when expectedBuyAmount is missing`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns evmSwapResponse(expectedBuy = null)

        assertThrows<SwapKitError.Decoding> { source().fetch(request()) }
    }

    @Test
    fun `fetch throws Decoding when expectedBuyAmount is not a decimal`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns evmSwapResponse(expectedBuy = "not-a-number")

        assertThrows<SwapKitError.Decoding> { source().fetch(request()) }
    }

    @Test
    fun `fetch throws UnsupportedTxType for non-EVM, non-Solana txType`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-psbt", providers = listOf("CHAINFLIP")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonObject(emptyMap()),
                meta = SwapKitTxMeta(txType = "PSBT"),
                expectedBuyAmount = "1",
            )

        assertThrows<SwapKitError.UnsupportedTxType> { source().fetch(request()) }
    }

    @Test
    fun `fetch sends decimal sellAmount and dotted CHAIN-TICKER asset identifiers`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)

        val captured = slot<SwapKitQuoteRequest>()
        coEvery { api.quote(capture(captured)) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns evmSwapResponse()

        val src = ethCoin().copy(isNativeToken = true)
        source()
            .fetch(
                request(
                    srcToken = src,
                    tokenValue = TokenValue(value = BigInteger("8600000000000000"), token = src),
                )
            )

        assertEquals("ETH.ETH", captured.captured.sellAsset)
        assertEquals("0.0086", captured.captured.sellAmount)
    }

    @Test
    fun `fetch encodes ERC-20 token asset as CHAIN-TICKER-CONTRACT`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)

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
    fun `fetch throws NoRoutes when source chain has no SwapKit prefix mapping`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        val unmapped = ethCoin().copy(chain = Chain.Hyperliquid)

        assertThrows<SwapKitError.NoRoutes> { source().fetch(request(srcToken = unmapped)) }
    }

    @Test
    fun `fetch falls back to meta_subProvider when providers list is empty`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns
            evmSwapResponse(providers = emptyList(), metaSubProvider = "GARDEN")

        val result = source().fetch(request()) as SwapQuoteResult.Evm

        assertEquals("GARDEN", result.subProvider)
    }

    @Test
    fun `fetch leaves subProvider null when neither providers list nor meta subProvider are present`() =
        runTest {
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1")
                        )
                )
            coEvery { api.swap(any()) } returns
                evmSwapResponse(providers = emptyList(), metaSubProvider = null)

            val result = source().fetch(request()) as SwapQuoteResult.Evm

            assertNull(result.subProvider)
        }

    @Test
    fun `fetch wraps unexpected exceptions as SwapKitError Network preserving the cause`() =
        runTest {
            every { config.isFeatureEnabled } returns flowOf(true)
            val boom = RuntimeException("DNS failure")
            coEvery { api.quote(any()) } throws boom

            val error = assertThrows<SwapKitError.Network> { source().fetch(request()) }
            assertEquals(boom, error.cause)
        }

    @Test
    fun `fetch returns SwapQuoteResult SwapKit for TON txType with canonical bytes on payload`() =
        runTest {
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "r-ton",
                                providers = listOf("CHAINFLIP"),
                                expectedBuy = "0.0125",
                                fees =
                                    listOf(
                                        SwapKitFee(
                                            type = "inbound",
                                            chain = "TON",
                                            amount = "0.005",
                                        )
                                    ),
                            )
                        )
                )
            coEvery { api.swap(any()) } returns
                tonSwapResponse(transfers = listOf("EQAvault" to "1000000000"))

            val result =
                source().fetch(request(srcToken = tonCoin(), dstToken = ethCoin()))
                    as SwapQuoteResult.SwapKit

            assertEquals("TON", result.quote.data.txType)
            assertEquals("EQAvault", result.quote.data.targetAddress)
            assertEquals(BigInteger("1000000000"), result.quote.data.fromAmount)
            // Inbound fee 0.005 TON × 10^9 = 5_000_000 nano-TON.
            assertEquals(BigInteger("5000000"), result.quote.fees.value)
            // toAmountDecimal 0.0125 × 10^18 = 0.0125 ETH in wei.
            assertEquals(BigInteger("12500000000000000"), result.quote.expectedDstValue.value)
            assertEquals("CHAINFLIP", result.quote.subProvider)
            // Canonical bytes round-trip: decode and confirm structural equivalence.
            val decoded =
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<
                        List<com.vultisig.wallet.data.api.models.quotes.SwapKitTonTransfer>
                    >(
                        result.quote.data.txPayload.decodeToString()
                    )
            assertEquals(1, decoded.size)
            assertEquals("EQAvault", decoded[0].address)
            assertEquals("1000000000", decoded[0].amount)
        }

    @Test
    fun `fetch carries multiple TON transfers verbatim in canonical bytes`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns
            tonSwapResponse(
                transfers = listOf("EQAvault1" to "500000000", "EQAvault2" to "500000000")
            )

        val result =
            source().fetch(request(srcToken = tonCoin(), dstToken = ethCoin()))
                as SwapQuoteResult.SwapKit

        // First transfer drives targetAddress + fromAmount (matches iOS: the deposit address is
        // the leading vault, the on-chain debit is the sum of transfer amounts handled by the
        // signer via the full transfer list).
        assertEquals("EQAvault1", result.quote.data.targetAddress)
        assertEquals(BigInteger("500000000"), result.quote.data.fromAmount)
        val decoded =
            Json { ignoreUnknownKeys = true }
                .decodeFromString<
                    List<com.vultisig.wallet.data.api.models.quotes.SwapKitTonTransfer>
                >(
                    result.quote.data.txPayload.decodeToString()
                )
        assertEquals(2, decoded.size)
        assertEquals("EQAvault2", decoded[1].address)
    }

    @Test
    fun `fetch throws Decoding when TON tx is an empty array`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns tonSwapResponse(transfers = emptyList())

        assertThrows<SwapKitError.Decoding> {
            source().fetch(request(srcToken = tonCoin(), dstToken = ethCoin()))
        }
    }

    @Test
    fun `fetch throws Decoding when TON transfer amount is not an integer`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns tonSwapResponse(transfers = listOf("EQAvault" to "1.5"))

        assertThrows<SwapKitError.Decoding> {
            source().fetch(request(srcToken = tonCoin(), dstToken = ethCoin()))
        }
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

    private fun route(
        routeId: String,
        providers: List<String>,
        expectedBuy: String? = "1",
        fees: List<SwapKitFee> = emptyList(),
    ) =
        SwapKitRoute(
            routeId = routeId,
            providers = providers,
            expectedBuyAmount = expectedBuy,
            fees = fees,
        )

    private fun evmSwapResponse(
        gas: String? = "200000",
        gasPrice: String? = "10",
        from: String? = "0xfrom",
        to: String = "0xto",
        data: String = "0xdata",
        value: String = "0",
        expectedBuy: String? = "1",
        providers: List<String> = listOf("CHAINFLIP"),
        metaSubProvider: String? = null,
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
            meta = SwapKitTxMeta(txType = "EVM", subProvider = metaSubProvider),
            expectedBuyAmount = expectedBuy,
            providers = providers,
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

    private fun tonCoin() =
        Coin(
            chain = Chain.Ton,
            ticker = "TON",
            logo = "",
            address = "EQAuser",
            decimal = 9,
            hexPublicKey = "pub",
            priceProviderID = "the-open-network",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun tonSwapResponse(
        transfers: List<Pair<String, String>>,
        expectedBuy: String? = "0.0125",
        providers: List<String> = listOf("CHAINFLIP"),
    ): SwapKitSwapResponseJson {
        val txArray =
            kotlinx.serialization.json.buildJsonArray {
                transfers.forEach { (address, amount) ->
                    add(
                        buildJsonObject {
                            put("address", JsonPrimitive(address))
                            put("amount", JsonPrimitive(amount))
                        }
                    )
                }
            }
        return SwapKitSwapResponseJson(
            swapId = "ton-swap-1",
            tx = txArray,
            meta = SwapKitTxMeta(txType = "TON"),
            expectedBuyAmount = expectedBuy,
            providers = providers,
            targetAddress = transfers.firstOrNull()?.first,
        )
    }
}
