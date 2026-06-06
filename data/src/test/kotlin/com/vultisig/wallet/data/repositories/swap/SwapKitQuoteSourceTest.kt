package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.api.models.quotes.SwapKitApprovalTx
import com.vultisig.wallet.data.api.models.quotes.SwapKitFee
import com.vultisig.wallet.data.api.models.quotes.SwapKitQuoteRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitQuoteResponseJson
import com.vultisig.wallet.data.api.models.quotes.SwapKitRoute
import com.vultisig.wallet.data.api.models.quotes.SwapKitSwapRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitSwapResponseJson
import com.vultisig.wallet.data.api.models.quotes.SwapKitTonTransfer
import com.vultisig.wallet.data.api.models.quotes.SwapKitTxMeta
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    // ERC20 approve(address,uint256) calldata fragments for the approval-spender decode tests.
    private val SPENDER_HEX = "6c0ad82f9721a6dc986381d19338601a2e6370e5"
    private val SPENDER_WORD = "0".repeat(24) + SPENDER_HEX // 32-byte left-padded spender word
    private val MAX_AMOUNT_WORD = "f".repeat(64) // 32-byte amount word
    private val APPROVE_SELECTOR = "095ea7b3"

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
    fun `fetch throws RouteFiltered when every route is filtered out including streaming variants`() =
        runTest {
            // Upstream returned candidates but the client Thor/Maya/multi-hop filter emptied them.
            // That client-side gate surfaces RouteFiltered (distinct localized copy), reserving
            // NoRoutes for the "/v3/quote returned zero routes" case.
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

            assertThrows<SwapKitError.RouteFiltered> { source().fetch(request()) }
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
    fun `fetch carries SwapKit meta approvalAddress through as the tx allowanceTarget`() = runTest {
        // Regression: SwapKit's EVM swap entry contract (`tx.to`, which also equals the top-level
        // `targetAddress`) is NOT the ERC20 allowance target — it pulls the sell token through a
        // separate token-transfer proxy reported as `meta.approvalAddress`. Approving `tx.to` made
        // real swaps revert with ERC20InsufficientAllowance(spender=approvalAddress, allowance=0).
        // The proxy must survive into tx.allowanceTarget so the spender is derived from it (matches
        // iOS' `spender = meta.approvalAddress`).
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns
            evmSwapResponse(
                to = "0x9025b8ff35ca44f7018c3a37fe0f69e63dbb0743", // SKWrapGeneric (swap entry)
                approvalAddress =
                    "0x6c0ad82f9721a6dc986381d19338601a2e6370e5", // SKTokenTransferProxy (spender)
            )

        val result = source().fetch(request()) as SwapQuoteResult.Evm

        assertEquals("0x9025b8ff35ca44f7018c3a37fe0f69e63dbb0743", result.data.tx.to)
        assertEquals("0x6c0ad82f9721a6dc986381d19338601a2e6370e5", result.data.tx.allowanceTarget)
    }

    @Test
    fun `fetch leaves allowanceTarget null when meta approvalAddress is absent so callers fall back to tx_to`() =
        runTest {
            // Native-source / non-approval routes: SwapKit omits `meta.approvalAddress`, and the
            // downstream `allowanceTarget ?: to` fallback keeps the legacy behaviour of using `to`.
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(routeId = "r", providers = listOf("ONEINCH"), expectedBuy = "1")
                        )
                )
            coEvery { api.swap(any()) } returns evmSwapResponse(approvalAddress = null)

            val result = source().fetch(request()) as SwapQuoteResult.Evm

            assertNull(result.data.tx.allowanceTarget)
        }

    @Test
    fun `fetch decodes hex-encoded EVM gas, gasPrice, and value into decimal`() = runTest {
        // SwapKit V3 hex-encodes the EVM tx numeric fields (`"gas":"0x55730"`). The downstream
        // pipeline parses gas as a base-10 Long and gasPrice/value with `toBigInteger()` (which
        // throws on hex), so a real hex route must be decoded to decimal at this boundary.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("ONEINCH"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns
            evmSwapResponse(gas = "0x55730", gasPrice = "0x57d86d7", value = "0x2710")

        val result = source().fetch(request()) as SwapQuoteResult.Evm

        assertEquals(350000L, result.data.tx.gas)
        assertEquals("92112599", result.data.tx.gasPrice)
        assertEquals("10000", result.data.tx.value)
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
    fun `fetch decodes legacy Solana object shape with message field when swapTransaction is absent`() =
        runTest {
            // Forward-compat: the legacy object can carry the blob under `message` instead of
            // `swapTransaction`. solanaBase64 falls back to `message` so neither spelling is lost.
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(routeId = "r-sol", providers = listOf("NEAR"), expectedBuy = "9")
                        )
                )
            coEvery { api.swap(any()) } returns
                SwapKitSwapResponseJson(
                    tx = buildJsonObject { put("message", JsonPrimitive("BASE64MESSAGE")) },
                    meta = SwapKitTxMeta(txType = "SOLANA"),
                    expectedBuyAmount = "9",
                )

            val result = source().fetch(request()) as SwapQuoteResult.Evm

            assertEquals("BASE64MESSAGE", result.data.tx.data)
        }

    @Test
    fun `fetch throws Decoding when Solana tx is neither a base64 string nor a known object shape`() =
        runTest {
            // An empty object carries neither swapTransaction nor message, so the Solana blob can't
            // be extracted — refuse with Decoding rather than signing an empty payload.
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(routeId = "r-sol", providers = listOf("NEAR"), expectedBuy = "9")
                        )
                )
            coEvery { api.swap(any()) } returns
                SwapKitSwapResponseJson(
                    tx = JsonObject(emptyMap()),
                    meta = SwapKitTxMeta(txType = "SOLANA"),
                    expectedBuyAmount = "9",
                )

            assertThrows<SwapKitError.Decoding> { source().fetch(request()) }
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
                    listOf(route(routeId = "r-sol", providers = listOf("NEAR"), expectedBuy = "9"))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonPrimitive("BASE64"),
                meta = SwapKitTxMeta(txType = "SOLANA"),
                expectedBuyAmount = "9",
                // Inbound fee comes from the /v3/swap response (fresh), not the /v3/quote route.
                fees =
                    listOf(
                        SwapKitFee(type = "inbound", chain = "SOL", amount = "0.000005"),
                        SwapKitFee(type = "outbound", chain = "ETH", amount = "0.0001"),
                    ),
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
                            )
                        )
                )
            coEvery { api.swap(any()) } returns
                evmSwapResponse(
                    fees = listOf(SwapKitFee(type = "outbound", chain = "ETH", amount = "0.01"))
                )

            val result = source().fetch(request()) as SwapQuoteResult.Evm

            assertEquals("0", result.data.tx.swapFee)
        }

    @Test
    fun `fetch falls back to route fees when the swap response omits the inbound entry`() =
        runTest {
            // A real /v3/swap reply that carries no fees[] for an EVM route must not silently read
            // zero on the verify screen — fall back to the inbound fee from the /v3/quote route.
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
                                            type = "inbound",
                                            chain = "ETH",
                                            amount = "0.0002",
                                        )
                                    ),
                            )
                        )
                )
            coEvery { api.swap(any()) } returns evmSwapResponse(fees = emptyList())

            val result = source().fetch(request()) as SwapQuoteResult.Evm

            // 0.0002 ETH * 10^18 = 200000000000000 raw wei, sourced from the route fee since the
            // swap response carried none.
            assertEquals("200000000000000", result.data.tx.swapFee)
        }

    @Test
    fun `fetch throws MalformedAmount when expectedBuyAmount is missing`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns evmSwapResponse(expectedBuy = null)

        // The developer-only "(missing)" sentinel was dropped: a null upstream amount yields an
        // empty raw so the UI falls back to the generic decoding copy instead of leaking it.
        val error = assertThrows<SwapKitError.MalformedAmount> { source().fetch(request()) }
        assertEquals("", error.raw)
    }

    @Test
    fun `fetch throws MalformedAmount when expectedBuyAmount is not a decimal`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns evmSwapResponse(expectedBuy = "not-a-number")

        val error = assertThrows<SwapKitError.MalformedAmount> { source().fetch(request()) }
        assertEquals("not-a-number", error.raw)
    }

    @Test
    fun `fetch throws UnsupportedTxType for a still-unwired non-EVM txType`() = runTest {
        // PSBT (Bitcoin), TRON, SUI, CARDANO, TON, and XRP are now wired to a Native
        // SwapQuote.SwapKit; a still-unrecognised txType has no signer, so it surfaces as
        // UnsupportedTxType.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-unwired", providers = listOf("CHAINFLIP")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonObject(emptyMap()),
                meta = SwapKitTxMeta(txType = "ALGORAND"),
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
    fun `fetch scales inbound fee by source-chain native decimals not the ERC-20 sell token decimals`() =
        runTest {
            // Regression: the inbound fee asset is the source chain's NATIVE coin (ETH.ETH, 18 dp),
            // even when selling an ERC-20 (USDC, 6 dp). Scaling by srcToken.decimal (6) truncates
            // the fee by 10^12 — 0.00001165876952721 ETH would read "11" wei instead of ~1.16e13.
            every { config.isFeatureEnabled } returns flowOf(true)
            val usdc =
                ethCoin()
                    .copy(
                        ticker = "USDC",
                        isNativeToken = false,
                        contractAddress = "0xa0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                        decimal = 6,
                    )
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(routeId = "r", providers = listOf("ONEINCH"), expectedBuy = "1")
                        )
                )
            coEvery { api.swap(any()) } returns
                evmSwapResponse(
                    fees =
                        listOf(
                            SwapKitFee(
                                type = "inbound",
                                chain = "ETH",
                                amount = "0.00001165876952721",
                            )
                        )
                )

            val result = source().fetch(request(srcToken = usdc)) as SwapQuoteResult.Evm

            assertEquals("11658769527210", result.data.tx.swapFee)
        }

    @Test
    fun `fetch throws MalformedAmount when EVM tx value is not parseable`() = runTest {
        // A silent value=0 coercion would underpay a value-bearing native swap; refuse instead.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("CHAINFLIP"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns evmSwapResponse(value = "not-a-number")

        val error = assertThrows<SwapKitError.MalformedAmount> { source().fetch(request()) }
        assertEquals("not-a-number", error.raw)
    }

    @Test
    fun `fetch still filters THORChain streaming under a Turkish locale`() = runTest {
        // Locale.ROOT guard: a Turkish-locale lowercase() maps THORCHAIN_STREAMING's `I` to dotless
        // `ı`, so the route would miss the filter set and slip through — stacking a second
        // affiliate
        // fee on Vultisig's native Thor integration. Pin that it is still dropped (RouteFiltered).
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "thor-streaming",
                                providers = listOf("THORCHAIN_STREAMING"),
                            )
                        )
                )

            assertThrows<SwapKitError.RouteFiltered> { source().fetch(request()) }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `fetch decodes a Bitcoin PSBT route into a Native SwapQuote SwapKit payload`() = runTest {
        // Phase 2 foundation: a non-EVM (PSBT) route surfaces as SwapQuoteResult.Native wrapping a
        // fully-formed SwapQuote.SwapKit — the base64 PSBT is decoded into txPayload bytes, and the
        // inbound BTC fee scales by 8 native decimals. (Signer is wired separately.)
        every { config.isFeatureEnabled } returns flowOf(true)
        val psbtBytes = byteArrayOf(0x70, 0x73, 0x62, 0x74, 0x01, 0x02) // "psbt" magic + bytes
        val base64 = Base64.getEncoder().encodeToString(psbtBytes)
        val btc = btcCoin()
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(
                        route(routeId = "r-btc", providers = listOf("NEAR"), expectedBuy = "385.24")
                    )
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                swapId = "btc-swap-1",
                tx = JsonPrimitive(base64),
                meta = SwapKitTxMeta(txType = "PSBT"),
                targetAddress = "bc1ptarget",
                expectedBuyAmount = "385.24",
                // Inbound fee comes from the /v3/swap response, not the /v3/quote route.
                fees = listOf(SwapKitFee(type = "inbound", chain = "BTC", amount = "0.000004")),
                providers = listOf("NEAR"),
            )

        val result =
            source().fetch(request(srcToken = btc, dstToken = ethCoin())) as SwapQuoteResult.Native
        val quote = result.quote as SwapQuote.SwapKit

        assertEquals("NEAR", quote.subProvider)
        assertEquals("PSBT", quote.data.txType)
        assertTrue(psbtBytes.contentEquals(quote.data.txPayload))
        assertEquals("bc1ptarget", quote.data.targetAddress)
        assertEquals("btc-swap-1", quote.data.swapId)
        assertEquals("NEAR", quote.data.subProvider)
        assertEquals(btc, quote.data.fromCoin)
        // The fragile scaling line: expectedDstValue + payload amounts must use the right decimals.
        assertEquals(ethCoin(), quote.data.toCoin)
        assertEquals(BigInteger.ONE, quote.data.fromAmount)
        assertEquals(0, BigDecimal("385.24").compareTo(quote.data.toAmountDecimal))
        // 385.24 ETH (dst, 18 dp) = 385.24 × 10^18.
        assertEquals(BigInteger("385240000000000000000"), quote.expectedDstValue.value)
        // 0.000004 BTC inbound fee scaled by 8 native decimals = 400 sats (not 0.000004 × 10^18).
        assertEquals(BigInteger("400"), quote.fees.value)
        assertEquals("BTC", quote.fees.unit)
        assertEquals(8, quote.fees.decimals)
    }

    @Test
    fun `fetch decodes a TRON route into a Native SwapQuote SwapKit with the TronWeb object as txPayload`() =
        runTest {
            // TRON's tx is a TronWeb-shaped JSON object (not base64) — the source UTF-8 encodes it
            // verbatim into txPayload so SwapKitTronSigner can pull raw_data_hex. The inbound TRX
            // fee scales by 6 native decimals.
            every { config.isFeatureEnabled } returns flowOf(true)
            val tronTx = buildJsonObject {
                put("visible", JsonPrimitive(true))
                put("txID", JsonPrimitive("90788bbae2f83d278b5f13a9b39e26a294d9319bf"))
                put("raw_data_hex", JsonPrimitive("0a0289752208deadbeef"))
            }
            // decimal = 18 (not the chain's native 6) so a regression that scaled the inbound fee
            // by srcToken.decimal instead of TRON's native 6 decimals would fail this assertion.
            val tron = tronCoin().copy(decimal = 18)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "r-tron",
                                providers = listOf("NEAR"),
                                expectedBuy = "49.634251",
                            )
                        )
                )
            coEvery { api.swap(any()) } returns
                SwapKitSwapResponseJson(
                    swapId = "tron-swap-1",
                    tx = tronTx,
                    meta = SwapKitTxMeta(txType = "TRON"),
                    targetAddress = "TUh93RjWWSxikbHA2U31pnYJYaP3zL45z5",
                    expectedBuyAmount = "49.634251",
                    fees = listOf(SwapKitFee(type = "inbound", chain = "TRON", amount = "13.3735")),
                    providers = listOf("NEAR"),
                )

            val result =
                source().fetch(request(srcToken = tron, dstToken = ethCoin()))
                    as SwapQuoteResult.Native
            val quote = result.quote as SwapQuote.SwapKit

            assertEquals("TRON", quote.data.txType)
            assertEquals("TUh93RjWWSxikbHA2U31pnYJYaP3zL45z5", quote.data.targetAddress)
            assertEquals("tron-swap-1", quote.data.swapId)
            // txPayload is the UTF-8 JSON object — round-trips back to the same raw_data_hex.
            val decoded =
                Json.parseToJsonElement(quote.data.txPayload.decodeToString()) as JsonObject
            val rawDataHex = decoded["raw_data_hex"]?.jsonPrimitive?.content
            assertEquals("0a0289752208deadbeef", rawDataHex)
            // 13.3735 TRX inbound fee scaled by 6 native decimals = 13_373_500 sun.
            assertEquals(BigInteger("13373500"), quote.fees.value)
            assertEquals("USDT", quote.fees.unit)
        }

    @Test
    fun `fetch decodes a SUI route into a Native SwapQuote SwapKit payload`() = runTest {
        // SUI's tx is a base64 pre-built PTB (same wire shape as PSBT) → decoded into txPayload
        // bytes. The inbound SUI fee scales by 9 native decimals, not the source token's decimals.
        every { config.isFeatureEnabled } returns flowOf(true)
        val ptbBytes = byteArrayOf(0x00, 0x00, 0x02, 0x11, 0x22, 0x33)
        val base64 = Base64.getEncoder().encodeToString(ptbBytes)
        val sui = suiCoin()
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(
                        route(routeId = "r-sui", providers = listOf("NEAR"), expectedBuy = "10.3")
                    )
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                swapId = "sui-swap-1",
                tx = JsonPrimitive(base64),
                meta = SwapKitTxMeta(txType = "SUI"),
                targetAddress =
                    "0x6e894db314206472c3290beaf2b78107fac89154308327206a5c7751c9700d98",
                expectedBuyAmount = "10.3",
                fees = listOf(SwapKitFee(type = "inbound", chain = "SUI", amount = "0.0015")),
                providers = listOf("NEAR"),
            )

        val result =
            source().fetch(request(srcToken = sui, dstToken = ethCoin())) as SwapQuoteResult.Native
        val quote = result.quote as SwapQuote.SwapKit

        assertEquals("SUI", quote.data.txType)
        assertTrue(ptbBytes.contentEquals(quote.data.txPayload))
        assertEquals(
            "0x6e894db314206472c3290beaf2b78107fac89154308327206a5c7751c9700d98",
            quote.data.targetAddress,
        )
        assertEquals("sui-swap-1", quote.data.swapId)
        assertEquals(sui, quote.data.fromCoin)
        // 0.0015 SUI inbound fee scaled by 9 native decimals = 1_500_000 MIST.
        assertEquals(BigInteger("1500000"), quote.fees.value)
        assertEquals("SUI", quote.fees.unit)
    }

    @Test
    fun `fetch decodes a Cardano CBOR route into a Native SwapQuote SwapKit payload`() = runTest {
        // A present `tx` is the pre-built CBOR flow: hex-encoded (NOT base64 like PSBT/SUI) →
        // hex-decoded into txPayload bytes, normalized to the CARDANO_PREBUILT discriminator. The
        // inbound ADA fee scales by 6 native decimals.
        every { config.isFeatureEnabled } returns flowOf(true)
        val cborBytes =
            byteArrayOf(0x84.toByte(), 0xA0.toByte(), 0xA0.toByte(), 0xF5.toByte(), 0xF6.toByte())
        val hex = "84a0a0f5f6"
        val ada = cardanoCoin()
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(
                        route(routeId = "r-ada", providers = listOf("NEAR"), expectedBuy = "12.5")
                    )
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                swapId = "ada-swap-1",
                tx = JsonPrimitive(hex),
                meta = SwapKitTxMeta(txType = "CARDANO"),
                targetAddress = "addr1qtarget",
                expectedBuyAmount = "12.5",
                fees = listOf(SwapKitFee(type = "inbound", chain = "ADA", amount = "0.17")),
                providers = listOf("NEAR"),
            )

        val result =
            source().fetch(request(srcToken = ada, dstToken = ethCoin())) as SwapQuoteResult.Native
        val quote = result.quote as SwapQuote.SwapKit

        assertEquals("NEAR", quote.subProvider)
        assertEquals("CARDANO_PREBUILT", quote.data.txType)
        assertTrue(cborBytes.contentEquals(quote.data.txPayload))
        assertEquals("addr1qtarget", quote.data.targetAddress)
        assertEquals("ada-swap-1", quote.data.swapId)
        assertEquals(ada, quote.data.fromCoin)
        // 0.17 ADA inbound fee scaled by 6 native decimals = 170_000 lovelace.
        assertEquals(BigInteger("170000"), quote.fees.value)
        assertEquals("ADA", quote.fees.unit)
    }

    @Test
    fun `fetch decodes a deposit-only Cardano route (null tx) into a native ADA send payload`() =
        runTest {
            // The two real captured ADA fixtures return `tx: null` — the deposit-only flow. Routing
            // lives entirely in targetAddress; the payload carries an empty txPayload and the
            // CARDANO discriminator (vs CARDANO_PREBUILT), so the keysign side builds a plain ADA
            // send rather than signing pre-built CBOR. Mirrors iOS' `.cardano`.
            every { config.isFeatureEnabled } returns flowOf(true)
            val ada = cardanoCoin()
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "r-ada-dep",
                                providers = listOf("NEAR"),
                                expectedBuy = "12.5",
                            )
                        )
                )
            coEvery { api.swap(any()) } returns
                SwapKitSwapResponseJson(
                    swapId = "ada-swap-2",
                    tx = JsonNull,
                    meta = SwapKitTxMeta(txType = "CARDANO"),
                    targetAddress = "addr1qtarget",
                    expectedBuyAmount = "12.5",
                    fees = listOf(SwapKitFee(type = "inbound", chain = "ADA", amount = "0.17")),
                    providers = listOf("NEAR"),
                )

            val result =
                source().fetch(request(srcToken = ada, dstToken = ethCoin()))
                    as SwapQuoteResult.Native
            val quote = result.quote as SwapQuote.SwapKit

            assertEquals("CARDANO", quote.data.txType)
            assertTrue(quote.data.txPayload.isEmpty())
            assertEquals("addr1qtarget", quote.data.targetAddress)
            assertEquals("ada-swap-2", quote.data.swapId)
        }

    @Test
    fun `fetch throws Decoding when the Cardano tx is not valid hex`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-ada", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonPrimitive("zzz-not-hex"),
                meta = SwapKitTxMeta(txType = "CBOR"),
                targetAddress = "addr1qtarget",
                expectedBuyAmount = "1",
            )

        assertThrows<SwapKitError.Decoding> { source().fetch(request(srcToken = cardanoCoin())) }
    }

    @Test
    fun `fetch decodes an XRP route into a deposit-only Native SwapQuote SwapKit`() = runTest {
        // XRP is deposit-only: SwapKit returns no tx body. The payload carries an empty txPayload
        // plus the deposit r-address; the top-level destinationTag (numeric) rides `memo`. The
        // inbound XRP fee scales by 6 native decimals (drops).
        every { config.isFeatureEnabled } returns flowOf(true)
        val xrp = rippleCoin()
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(
                        route(routeId = "r-xrp", providers = listOf("NEAR"), expectedBuy = "9.1")
                    )
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                swapId = "xrp-swap-1",
                tx = JsonNull, // deposit-only: no body
                meta = SwapKitTxMeta(txType = "XRP"),
                targetAddress = "rDepositAddr123",
                expectedBuyAmount = "9.1",
                destinationTag = JsonPrimitive(123456L),
                fees = listOf(SwapKitFee(type = "inbound", chain = "XRP", amount = "0.1")),
                providers = listOf("NEAR"),
            )

        val result =
            source().fetch(request(srcToken = xrp, dstToken = ethCoin())) as SwapQuoteResult.Native
        val quote = result.quote as SwapQuote.SwapKit

        assertEquals("XRP", quote.data.txType)
        assertEquals(0, quote.data.txPayload.size) // deposit-only → no tx body
        assertEquals("rDepositAddr123", quote.data.targetAddress)
        assertEquals("123456", quote.data.memo) // destination tag → numeric memo
        assertEquals("xrp-swap-1", quote.data.swapId)
        // 0.1 XRP inbound fee scaled by 6 native decimals = 100_000 drops.
        assertEquals(BigInteger("100000"), quote.fees.value)
        assertEquals("XRP", quote.fees.unit)
    }

    @Test
    fun `fetch normalises a RIPPLE wire txType to the canonical XRP discriminator`() = runTest {
        // SwapKit ships XRP as either "XRP" or "RIPPLE". The payload must carry the canonical
        // TX_TYPE_XRP so the signing-side gate (which matches "XRP") accepts a "RIPPLE" route —
        // otherwise pressing Swap throws "Unsupported SwapKit txType for swap: RIPPLE".
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-xrp", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonNull,
                meta = SwapKitTxMeta(txType = "RIPPLE"),
                targetAddress = "rDepositAddr123",
                expectedBuyAmount = "1",
            )

        val result = source().fetch(request(srcToken = rippleCoin())) as SwapQuoteResult.Native
        val quote = result.quote as SwapQuote.SwapKit
        assertEquals("XRP", quote.data.txType)
    }

    @Test
    fun `fetch falls back to meta destinationTag when the top-level tag is absent`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-xrp", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonNull,
                // Tag arrives string-encoded in meta — parsed defensively.
                meta = SwapKitTxMeta(txType = "XRP", destinationTag = JsonPrimitive("77")),
                targetAddress = "rDepositAddr123",
                expectedBuyAmount = "1",
            )

        val result = source().fetch(request(srcToken = rippleCoin())) as SwapQuoteResult.Native
        val quote = result.quote as SwapQuote.SwapKit
        assertEquals("77", quote.data.memo)
        assertEquals("rDepositAddr123", quote.data.targetAddress)
    }

    @Test
    fun `fetch extracts the XRP destination tag from the address suffix and strips it`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-xrp", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonNull,
                meta = SwapKitTxMeta(txType = "XRP"),
                // No top-level / meta tag — the tag is a `?dt=` suffix on the address.
                targetAddress = "rDepositAddr123?dt=42",
                expectedBuyAmount = "1",
            )

        val result = source().fetch(request(srcToken = rippleCoin())) as SwapQuoteResult.Native
        val quote = result.quote as SwapQuote.SwapKit
        assertEquals("42", quote.data.memo)
        // Suffix stripped — only the bare r-address is the payment destination.
        assertEquals("rDepositAddr123", quote.data.targetAddress)
    }

    @Test
    fun `fetch extracts the XRP destination tag from the pipe suffix and strips it`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-xrp", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonNull,
                meta = SwapKitTxMeta(txType = "XRP"),
                // `|N` pipe form of the destination-tag suffix.
                targetAddress = "rDepositAddr123|42",
                expectedBuyAmount = "1",
            )

        val result = source().fetch(request(srcToken = rippleCoin())) as SwapQuoteResult.Native
        val quote = result.quote as SwapQuote.SwapKit
        assertEquals("42", quote.data.memo)
        assertEquals("rDepositAddr123", quote.data.targetAddress)
    }

    @Test
    fun `fetch throws Decoding when the XRP pipe suffix is non-numeric`() = runTest {
        // A `|` announces a tag; a non-numeric suffix is malformed. Fail the route rather than pass
        // an unsignable address (or silently drop a tag the deposit may require) downstream.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-xrp", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonNull,
                meta = SwapKitTxMeta(txType = "XRP"),
                targetAddress = "rDepositAddr123|abc",
                expectedBuyAmount = "1",
            )

        assertThrows<SwapKitError.Decoding> { source().fetch(request(srcToken = rippleCoin())) }
    }

    @Test
    fun `fetch drops an out-of-uint32-range top-level XRP destination tag`() = runTest {
        // XRP's DestinationTag is uint32. A negative (or >2^32-1) wire value must not flow onto the
        // field — emit no tag, matching iOS' UInt64 guard, rather than passing it through `memo`.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-xrp", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonNull,
                meta = SwapKitTxMeta(txType = "XRP"),
                targetAddress = "rDepositAddr123",
                expectedBuyAmount = "1",
                destinationTag = JsonPrimitive(-1L),
            )

        val result = source().fetch(request(srcToken = rippleCoin())) as SwapQuoteResult.Native
        val quote = result.quote as SwapQuote.SwapKit
        assertNull(quote.data.memo)
        assertEquals("rDepositAddr123", quote.data.targetAddress)
    }

    @Test
    fun `fetch accepts the max uint32 XRP destination tag`() = runTest {
        // 2^32-1 is the largest valid DestinationTag — it must survive the range guard.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-xrp", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonNull,
                meta = SwapKitTxMeta(txType = "XRP"),
                targetAddress = "rDepositAddr123",
                expectedBuyAmount = "1",
                destinationTag = JsonPrimitive(4294967295L),
            )

        val result = source().fetch(request(srcToken = rippleCoin())) as SwapQuoteResult.Native
        val quote = result.quote as SwapQuote.SwapKit
        assertEquals("4294967295", quote.data.memo)
    }

    @Test
    fun `fetch throws Decoding when the XRP pipe suffix is out of uint32 range`() = runTest {
        // A `|` announces a tag; an out-of-uint32-range suffix is as malformed as a non-numeric
        // one.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-xrp", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonNull,
                meta = SwapKitTxMeta(txType = "XRP"),
                targetAddress = "rDepositAddr123|4294967296",
                expectedBuyAmount = "1",
            )

        assertThrows<SwapKitError.Decoding> { source().fetch(request(srcToken = rippleCoin())) }
    }

    @Test
    fun `fetch throws Decoding when the TRON tx is not a JSON object`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-tron", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonPrimitive("not-an-object"),
                meta = SwapKitTxMeta(txType = "TRON"),
                targetAddress = "Ttarget",
                expectedBuyAmount = "1",
            )

        assertThrows<SwapKitError.Decoding> { source().fetch(request(srcToken = tronCoin())) }
    }

    @Test
    fun `fetch throws Decoding when the TRON tx has a blank raw_data_hex`() = runTest {
        // A present-but-empty raw_data_hex must be rejected at quote time (not deferred to keysign)
        // so route selection can fall back cleanly.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-tron", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = buildJsonObject { put("raw_data_hex", JsonPrimitive("")) },
                meta = SwapKitTxMeta(txType = "TRON"),
                targetAddress = "Ttarget",
                expectedBuyAmount = "1",
            )

        assertThrows<SwapKitError.Decoding> { source().fetch(request(srcToken = tronCoin())) }
    }

    @Test
    fun `fetch throws Decoding when the PSBT tx is not valid base64`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-btc", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonPrimitive("!!!not-base64!!!"),
                meta = SwapKitTxMeta(txType = "PSBT"),
                targetAddress = "bc1ptarget",
                expectedBuyAmount = "1",
            )

        assertThrows<SwapKitError.Decoding> {
            source().fetch(request(srcToken = btcCoin(), dstToken = ethCoin()))
        }
    }

    @Test
    fun `fetch throws Decoding when the PSBT tx decodes to an empty payload`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-btc", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonPrimitive(""),
                meta = SwapKitTxMeta(txType = "PSBT"),
                targetAddress = "bc1ptarget",
                expectedBuyAmount = "1",
            )

        assertThrows<SwapKitError.Decoding> {
            source().fetch(request(srcToken = btcCoin(), dstToken = ethCoin()))
        }
    }

    @Test
    fun `fetch throws Decoding when a non-EVM response has no targetAddress`() = runTest {
        // Deposit-only chains (Cardano / XRP) route entirely on `targetAddress`, so a null value
        // would stage an unspendable quote. The tx itself is valid base64 here, so this pins the
        // targetAddress guard specifically rather than the decode path.
        every { config.isFeatureEnabled } returns flowOf(true)
        val base64 = Base64.getEncoder().encodeToString(byteArrayOf(0x70, 0x73, 0x62, 0x74, 0x01))
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r-btc", providers = listOf("NEAR")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonPrimitive(base64),
                meta = SwapKitTxMeta(txType = "PSBT"),
                targetAddress = null,
                expectedBuyAmount = "1",
            )

        assertThrows<SwapKitError.Decoding> {
            source().fetch(request(srcToken = btcCoin(), dstToken = ethCoin()))
        }
    }

    @Test
    fun `fetch decodes a TON route into a Native SwapQuote SwapKit with the transfer array as txPayload`() =
        runTest {
            // TON's tx is a [{address, amount}] array of raw nano-TON. The source validates every
            // amount and re-encodes the canonical array into txPayload; the deposit address is
            // targetAddress and the on-chain debit is the user's send amount (fromAmount), matching
            // iOS. Signing reuses the native TonHelper path. The inbound TON fee scales by 9
            // native decimals.
            every { config.isFeatureEnabled } returns flowOf(true)
            val ton = tonCoin()
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "r-ton",
                                providers = listOf("CHAINFLIP"),
                                expectedBuy = "385.24",
                            )
                        )
                )
            coEvery { api.swap(any()) } returns
                tonSwapResponse(
                    transfers = listOf("EQvault" to "5000000000"),
                    expectedBuy = "385.24",
                    fees = listOf(SwapKitFee(type = "inbound", chain = "TON", amount = "0.05")),
                )

            val result =
                source()
                    .fetch(
                        request(
                            srcToken = ton,
                            dstToken = ethCoin(),
                            tokenValue = TokenValue(BigInteger("5000000000"), ton),
                        )
                    ) as SwapQuoteResult.Native
            val quote = result.quote as SwapQuote.SwapKit

            assertEquals("CHAINFLIP", quote.subProvider)
            assertEquals(SwapKitSwapPayloadJson.TX_TYPE_TON, quote.data.txType)
            assertEquals("EQvault", quote.data.targetAddress)
            assertEquals("ton-swap-1", quote.data.swapId)
            assertEquals(ton, quote.data.fromCoin)
            assertEquals(ethCoin(), quote.data.toCoin)
            // fromAmount is the user's send amount (iOS' fromAmountInCoin), not echoed from tx.
            assertEquals(BigInteger("5000000000"), quote.data.fromAmount)
            // txPayload is the canonical transfer array — round-trips to the same address + amount.
            val decoded =
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<List<SwapKitTonTransfer>>(
                        quote.data.txPayload.decodeToString()
                    )
            assertEquals(1, decoded.size)
            assertEquals("EQvault", decoded[0].address)
            assertEquals("5000000000", decoded[0].amount)
            // 385.24 ETH (dst, 18 dp) = 385.24 × 10^18.
            assertEquals(BigInteger("385240000000000000000"), quote.expectedDstValue.value)
            // 0.05 TON inbound fee scaled by 9 native decimals = 50_000_000 nanoTON.
            assertEquals(BigInteger("50000000"), quote.fees.value)
            assertEquals("TON", quote.fees.unit)
            assertEquals(9, quote.fees.decimals)
        }

    @Test
    fun `fetch carries multiple TON transfers verbatim in the canonical txPayload bytes`() =
        runTest {
            every { config.isFeatureEnabled } returns flowOf(true)
            val ton = tonCoin()
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes = listOf(route(routeId = "r", providers = listOf("CHAINFLIP")))
                )
            coEvery { api.swap(any()) } returns
                tonSwapResponse(
                    transfers = listOf("EQvault1" to "500000000", "EQvault2" to "500000000")
                )

            val result =
                source().fetch(request(srcToken = ton, dstToken = ethCoin()))
                    as SwapQuoteResult.Native
            val quote = result.quote as SwapQuote.SwapKit

            // The leading transfer address is the deposit target; the full set is kept in
            // txPayload.
            assertEquals("EQvault1", quote.data.targetAddress)
            val decoded =
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<List<SwapKitTonTransfer>>(
                        quote.data.txPayload.decodeToString()
                    )
            assertEquals(2, decoded.size)
            assertEquals("EQvault2", decoded[1].address)
            assertEquals("500000000", decoded[1].amount)
        }

    @Test
    fun `fetch throws Decoding when a TON transfer amount is not an integer`() = runTest {
        // A non-integer nano-TON amount must be rejected at quote time so route selection falls
        // back
        // cleanly instead of failing at keysign. Covers every transfer, not just the first.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r", providers = listOf("CHAINFLIP")))
            )
        coEvery { api.swap(any()) } returns
            tonSwapResponse(transfers = listOf("EQvault" to "1000000000", "EQvault2" to "1.5"))

        assertThrows<SwapKitError.Decoding> { source().fetch(request(srcToken = tonCoin())) }
    }

    @Test
    fun `fetch throws Decoding when the TON tx is an empty transfer array`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r", providers = listOf("CHAINFLIP")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonArray(emptyList()),
                meta = SwapKitTxMeta(txType = "TON"),
                targetAddress = "EQvault",
                expectedBuyAmount = "1",
            )

        assertThrows<SwapKitError.Decoding> { source().fetch(request(srcToken = tonCoin())) }
    }

    @Test
    fun `fetch throws Decoding when the TON tx is not a transfer array`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes = listOf(route(routeId = "r", providers = listOf("CHAINFLIP")))
            )
        coEvery { api.swap(any()) } returns
            SwapKitSwapResponseJson(
                tx = JsonObject(emptyMap()),
                meta = SwapKitTxMeta(txType = "TON"),
                targetAddress = "EQvault",
                expectedBuyAmount = "1",
            )

        assertThrows<SwapKitError.Decoding> { source().fetch(request(srcToken = tonCoin())) }
    }

    @Test
    fun `fetch throws QuoteDeviation when the buy amount is just below the max-slippage floor`() =
        runTest {
            // A well-formed route has expectedBuyAmount >= expectedBuyAmountMaxSlippage, so a buy
            // amount under the 0.99 * maxSlippage floor signals inconsistent/manipulated data — the
            // client-side guard rejects it even if the server-only outputAmountDeviationTooHigh
            // check was skipped, before the /v3/swap call is made.
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "r",
                                providers = listOf("CHAINFLIP"),
                                expectedBuy = "98",
                                maxSlippage = "100",
                            )
                        )
                )

            // floor = 100 * (1 - 0.01) = 99; 98 < 99 → rejected.
            assertThrows<SwapKitError.QuoteDeviation> { source().fetch(request()) }
        }

    @Test
    fun `fetch accepts a route whose buy amount sits exactly at the max-slippage floor`() =
        runTest {
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "r",
                                providers = listOf("CHAINFLIP"),
                                expectedBuy = "99",
                                maxSlippage = "100",
                            )
                        )
                )
            coEvery { api.swap(any()) } returns evmSwapResponse()

            // floor = 99; 99 < 99 is false → the guard passes and the route is mapped.
            assertTrue(source().fetch(request()) is SwapQuoteResult.Evm)
        }

    @Test
    fun `fetch no-ops the deviation guard when max-slippage is missing or unparseable`() = runTest {
        // The deviation can't be evaluated without a parseable maxSlippage, so the guard is a no-op
        // and the route proceeds rather than being rejected.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.swap(any()) } returns evmSwapResponse()

        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(
                        route(
                            routeId = "r",
                            providers = listOf("CHAINFLIP"),
                            expectedBuy = "1",
                            maxSlippage = null,
                        )
                    )
            )
        assertTrue(source().fetch(request()) is SwapQuoteResult.Evm)

        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(
                        route(
                            routeId = "r",
                            providers = listOf("CHAINFLIP"),
                            expectedBuy = "1",
                            maxSlippage = "not-a-number",
                        )
                    )
            )
        assertTrue(source().fetch(request()) is SwapQuoteResult.Evm)
    }

    @Test
    fun `fetch throws QuoteDeviation when the swap reply buy amount slips below its own floor`() =
        runTest {
            // The signed amount is scaled from the /v3/swap reply, not the /v3/quote route. A proxy
            // that degrades only the swap reply (leaving the quote route clean) must still be
            // caught
            // by re-running the deviation guard against the swap reply's amounts.
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "r",
                                providers = listOf("CHAINFLIP"),
                                expectedBuy = "100",
                                maxSlippage = "100",
                            )
                        )
                )
            // Quote route is clean (100 >= floor 99), but the swap reply degrades to 98 < floor 99.
            coEvery { api.swap(any()) } returns
                evmSwapResponse(expectedBuy = "98", expectedBuyMaxSlippage = "100")

            assertThrows<SwapKitError.QuoteDeviation> { source().fetch(request()) }
        }

    @Test
    fun `fetch no-ops the swap-reply deviation guard when the reply omits the max-slippage floor`() =
        runTest {
            // The swap reply commonly omits expectedBuyAmountMaxSlippage; the re-check is a no-op
            // then and the route proceeds (the /v3/quote route guard already passed above).
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(
                                routeId = "r",
                                providers = listOf("CHAINFLIP"),
                                expectedBuy = "100",
                                maxSlippage = "100",
                            )
                        )
                )
            coEvery { api.swap(any()) } returns
                evmSwapResponse(expectedBuy = "98", expectedBuyMaxSlippage = null)

            assertTrue(source().fetch(request()) is SwapQuoteResult.Evm)
        }

    @Test
    fun `fetch decodes the approval spender from approvalTx data when meta approvalAddress is absent`() =
        runTest {
            // Fallback path: when SwapKit omits meta.approvalAddress, the ERC20 allowance spender
            // is
            // recovered from the approve(spender, amount) calldata — NOT approvalTx.to (the asset
            // contract). Approving the wrong address reverts with ERC20InsufficientAllowance.
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(routeId = "r", providers = listOf("ONEINCH"), expectedBuy = "1")
                        )
                )
            coEvery { api.swap(any()) } returns
                evmSwapResponse(
                    approvalAddress = null,
                    approvalTx =
                        SwapKitApprovalTx(
                            to = "0xa0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", // asset contract
                            data = "0x$APPROVE_SELECTOR$SPENDER_WORD$MAX_AMOUNT_WORD",
                        ),
                )

            val result = source().fetch(request()) as SwapQuoteResult.Evm

            assertEquals("0x$SPENDER_HEX", result.data.tx.allowanceTarget)
        }

    @Test
    fun `fetch decodes the approval spender even when the calldata lacks a 0x prefix`() = runTest {
        // The decoder strips an optional 0x prefix, so a bare-hex approvalTx.data still resolves.
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("ONEINCH"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns
            evmSwapResponse(
                approvalAddress = null,
                approvalTx =
                    SwapKitApprovalTx(data = "$APPROVE_SELECTOR$SPENDER_WORD$MAX_AMOUNT_WORD"),
            )

        val result = source().fetch(request()) as SwapQuoteResult.Evm

        assertEquals("0x$SPENDER_HEX", result.data.tx.allowanceTarget)
    }

    @Test
    fun `fetch leaves allowanceTarget null when the approval calldata has the wrong selector`() =
        runTest {
            // A non-approve selector is not allowance calldata → decode returns null and, with no
            // meta.approvalAddress, allowanceTarget stays null (callers fall back to tx.to).
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(routeId = "r", providers = listOf("ONEINCH"), expectedBuy = "1")
                        )
                )
            coEvery { api.swap(any()) } returns
                evmSwapResponse(
                    approvalAddress = null,
                    approvalTx = SwapKitApprovalTx(data = "0xdeadbeef$SPENDER_WORD$MAX_AMOUNT_WORD"),
                )

            val result = source().fetch(request()) as SwapQuoteResult.Evm

            assertNull(result.data.tx.allowanceTarget)
        }

    @Test
    fun `fetch leaves allowanceTarget null when the approval calldata is too short`() = runTest {
        every { config.isFeatureEnabled } returns flowOf(true)
        coEvery { api.quote(any()) } returns
            SwapKitQuoteResponseJson(
                routes =
                    listOf(route(routeId = "r", providers = listOf("ONEINCH"), expectedBuy = "1"))
            )
        coEvery { api.swap(any()) } returns
            evmSwapResponse(
                approvalAddress = null,
                // Selector present but the spender word is truncated.
                approvalTx = SwapKitApprovalTx(data = "0x${APPROVE_SELECTOR}00"),
            )

        val result = source().fetch(request()) as SwapQuoteResult.Evm

        assertNull(result.data.tx.allowanceTarget)
    }

    @Test
    fun `fetch leaves allowanceTarget null when the approval calldata decodes to the zero address`() =
        runTest {
            // A zero-address spender is meaningless for an allowance → reject it rather than emit a
            // bogus 0x000… target.
            every { config.isFeatureEnabled } returns flowOf(true)
            coEvery { api.quote(any()) } returns
                SwapKitQuoteResponseJson(
                    routes =
                        listOf(
                            route(routeId = "r", providers = listOf("ONEINCH"), expectedBuy = "1")
                        )
                )
            coEvery { api.swap(any()) } returns
                evmSwapResponse(
                    approvalAddress = null,
                    approvalTx =
                        SwapKitApprovalTx(
                            data = "0x$APPROVE_SELECTOR${"0".repeat(64)}$MAX_AMOUNT_WORD"
                        ),
                )

            val result = source().fetch(request()) as SwapQuoteResult.Evm

            assertNull(result.data.tx.allowanceTarget)
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
        maxSlippage: String? = null,
    ) =
        SwapKitRoute(
            routeId = routeId,
            providers = providers,
            expectedBuyAmount = expectedBuy,
            expectedBuyAmountMaxSlippage = maxSlippage,
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
        expectedBuyMaxSlippage: String? = null,
        providers: List<String> = listOf("CHAINFLIP"),
        metaSubProvider: String? = null,
        approvalAddress: String? = null,
        approvalTx: SwapKitApprovalTx? = null,
        fees: List<SwapKitFee> = emptyList(),
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
            meta =
                SwapKitTxMeta(
                    txType = "EVM",
                    subProvider = metaSubProvider,
                    approvalAddress = approvalAddress,
                ),
            expectedBuyAmount = expectedBuy,
            expectedBuyAmountMaxSlippage = expectedBuyMaxSlippage,
            approvalTx = approvalTx,
            fees = fees,
            providers = providers,
        )

    private fun tonSwapResponse(
        transfers: List<Pair<String, String>>,
        swapId: String? = "ton-swap-1",
        targetAddress: String? = transfers.firstOrNull()?.first,
        expectedBuy: String? = "1",
        providers: List<String> = listOf("CHAINFLIP"),
        fees: List<SwapKitFee> = emptyList(),
    ) =
        SwapKitSwapResponseJson(
            swapId = swapId,
            tx =
                JsonArray(
                    transfers.map { (address, amount) ->
                        buildJsonObject {
                            put("address", JsonPrimitive(address))
                            put("amount", JsonPrimitive(amount))
                        }
                    }
                ),
            meta = SwapKitTxMeta(txType = "TON"),
            targetAddress = targetAddress,
            expectedBuyAmount = expectedBuy,
            fees = fees,
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

    private fun btcCoin() =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "",
            address = "bc1qsender",
            decimal = 8,
            hexPublicKey = "pub",
            priceProviderID = "bitcoin",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun tronCoin() =
        Coin(
            chain = Chain.Tron,
            ticker = "USDT",
            logo = "",
            address = "TLBaRhANQoJFTqre9Nf1mjuwNWjCJeYqUL",
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "tether",
            contractAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
            isNativeToken = false,
        )

    private fun suiCoin() =
        Coin(
            chain = Chain.Sui,
            ticker = "SUI",
            logo = "",
            address = "0x6e894db314206472c3290beaf2b78107fac89154308327206a5c7751c9700d98",
            decimal = 9,
            hexPublicKey = "pub",
            priceProviderID = "sui",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun cardanoCoin() =
        Coin(
            chain = Chain.Cardano,
            ticker = "ADA",
            logo = "",
            address = "addr1vtarget",
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "cardano",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun tonCoin() =
        Coin(
            chain = Chain.Ton,
            ticker = "TON",
            logo = "",
            address = "EQsender",
            decimal = 9,
            hexPublicKey = "pub",
            priceProviderID = "the-open-network",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun rippleCoin() =
        Coin(
            chain = Chain.Ripple,
            ticker = "XRP",
            logo = "",
            address = "rSenderAddr",
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "ripple",
            contractAddress = "",
            isNativeToken = true,
        )
}
