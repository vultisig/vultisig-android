package com.vultisig.wallet.data.api.models.quotes

import com.vultisig.wallet.data.api.errors.SwapKitError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the wire shape of SwapKit V3 quote, swap, and providers responses against vendored sample
 * payloads. Drift between Android and the proxy here surfaces as a quote-time crash, so the
 * fixtures are intentionally close to what `https://api.vultisig.com/swapkit-a/v3` actually
 * returns.
 */
class SwapKitQuoteDecodingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `decodes v3 quote response with chainflip route`() {
        val payload =
            """
            {
              "quoteId": "11111111-1111-1111-1111-111111111111",
              "routes": [
                {
                  "routeId": "22222222-2222-2222-2222-222222222222",
                  "providers": ["CHAINFLIP"],
                  "sellAsset": "ETH.USDC-0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                  "buyAsset": "ETH.USDT-0xdAC17F958D2ee523a2206206994597C13D831ec7",
                  "sellAmount": "1000000000",
                  "expectedBuyAmount": "999500000",
                  "expectedBuyAmountMaxSlippage": "989505000",
                  "sourceAddress": "0xSender",
                  "destinationAddress": "0xRecipient",
                  "totalSlippageBps": 10.0,
                  "estimatedTime": { "inbound": 12.0, "swap": 30.0, "outbound": 12.0, "total": 54.0 },
                  "legs": [
                    {
                      "provider": "CHAINFLIP",
                      "sellAsset": "ETH.USDC",
                      "buyAsset": "ETH.USDT",
                      "sellAmount": "1000000000",
                      "buyAmount": "999500000"
                    }
                  ],
                  "fees": [
                    { "type": "network", "amount": "21000", "asset": "ETH.ETH", "chain": "ETH" }
                  ],
                  "warnings": [],
                  "meta": { "tags": ["FASTEST", "RECOMMENDED"], "assets": [] }
                }
              ]
            }
            """
                .trimIndent()

        val decoded = json.decodeFromString<SwapKitQuoteResponseJson>(payload)

        assertEquals("11111111-1111-1111-1111-111111111111", decoded.quoteId)
        assertEquals(1, decoded.routes.size)
        val route = decoded.routes.single()
        assertEquals("22222222-2222-2222-2222-222222222222", route.routeId)
        assertEquals(listOf("CHAINFLIP"), route.providers)
        assertEquals("chainflip", route.primaryProviderId)
        assertEquals("999500000", route.expectedBuyAmount)
        assertEquals("989505000", route.expectedBuyAmountMaxSlippage)
        assertEquals(10.0, route.totalSlippageBps)
        assertEquals(1, route.legs.size)
        assertEquals("CHAINFLIP", route.legs.single().provider)
        assertEquals(54.0, route.estimatedTime?.total)
        assertEquals(listOf("FASTEST", "RECOMMENDED"), route.meta?.tags)
        assertNull(decoded.error)
    }

    @Test
    fun `decodes v3 quote error envelope without routes`() {
        val payload =
            """
            { "routes": [], "error": "NoRoutesFound", "message": "No SwapKit route available" }
            """
                .trimIndent()

        val decoded = json.decodeFromString<SwapKitQuoteResponseJson>(payload)

        assertTrue(decoded.routes.isEmpty())
        assertEquals("NoRoutesFound", decoded.error)
        assertEquals("No SwapKit route available", decoded.message)
    }

    @Test
    fun `route with multi-hop legs decodes without losing providers`() {
        val payload =
            """
            {
              "routes": [
                {
                  "routeId": "abc",
                  "providers": ["CHAINFLIP", "NEAR_INTENTS"],
                  "sellAsset": "BTC.BTC",
                  "buyAsset": "ETH.USDT",
                  "expectedBuyAmount": "1",
                  "legs": [
                    { "provider": "CHAINFLIP", "sellAsset": "BTC.BTC", "buyAsset": "ETH.ETH" },
                    { "provider": "NEAR_INTENTS", "sellAsset": "ETH.ETH", "buyAsset": "ETH.USDT" }
                  ]
                }
              ]
            }
            """
                .trimIndent()

        val route = json.decodeFromString<SwapKitQuoteResponseJson>(payload).routes.single()

        assertEquals(2, route.providers.size)
        assertEquals(2, route.legs.size)
        assertEquals("chainflip", route.primaryProviderId)
    }

    @Test
    fun `primaryProviderId is empty string when route has no providers`() {
        val empty = SwapKitRoute()
        assertEquals("", empty.primaryProviderId)
    }

    @Test
    fun `decodes v3 swap response with evm tx payload`() {
        val payload =
            """
            {
              "swapId": "33333333-3333-3333-3333-333333333333",
              "tx": {
                "from": "0xSender",
                "to": "0xRouter",
                "data": "0xabcdef",
                "value": "0",
                "gas": "210000",
                "gasPrice": "12000000000",
                "chainId": "1",
                "nonce": "7"
              },
              "meta": { "txType": "evm", "tags": ["RECOMMENDED"], "chain": "ETH", "subProvider": "chainflip" },
              "targetAddress": "0xRouter",
              "expectedBuyAmount": "999500000",
              "providers": ["CHAINFLIP"]
            }
            """
                .trimIndent()

        val response = json.decodeFromString<SwapKitSwapResponseJson>(payload)

        assertEquals("33333333-3333-3333-3333-333333333333", response.swapId)
        assertEquals(SwapKitTxMeta.TYPE_EVM, response.meta.type)
        assertEquals("ETH", response.meta.chain)
        assertEquals("chainflip", response.meta.subProvider)
        assertEquals(listOf("RECOMMENDED"), response.meta.tags)
        assertEquals("0xRouter", response.targetAddress)
        assertEquals("999500000", response.expectedBuyAmount)

        // tx is JsonElement until the caller decodes onto the matching DTO
        val evm = json.decodeFromJsonElement(SwapKitEvmTx.serializer(), response.tx)
        assertEquals("0xRouter", evm.to)
        assertEquals("0xabcdef", evm.data)
        assertEquals("210000", evm.gas)
        assertEquals("12000000000", evm.gasPrice)
    }

    @Test
    fun `decodes v3 swap response with solana tx payload`() {
        val payload =
            """
            {
              "tx": { "swapTransaction": "AQID" },
              "meta": { "txType": "SOLANA", "chain": "SOL", "subProvider": "jupiter" }
            }
            """
                .trimIndent()

        val response = json.decodeFromString<SwapKitSwapResponseJson>(payload)

        // type is lower-cased so dispatch is case-insensitive
        assertEquals(SwapKitTxMeta.TYPE_SOLANA, response.meta.type)
        val solana = json.decodeFromJsonElement(SwapKitSolanaTx.serializer(), response.tx)
        assertEquals("AQID", solana.swapTransaction)
    }

    @Test
    fun `swap response with unknown txType still decodes for typed dispatch`() {
        val payload =
            """
            {
              "tx": {},
              "meta": { "txType": "utxo" }
            }
            """
                .trimIndent()

        val response = json.decodeFromString<SwapKitSwapResponseJson>(payload)
        assertEquals("utxo", response.meta.type)
    }

    @Test
    fun `decodes providers response as top-level array with supportedChainIds`() {
        val payload =
            """
            [
              { "provider": "CHAINFLIP", "supportedChainIds": ["1", "56", "bitcoin"] },
              { "provider": "NEAR_INTENTS", "supportedChainIds": ["solana", "1"] }
            ]
            """
                .trimIndent()

        val decoded = json.decodeFromString<SwapKitProvidersResponseJson>(payload)

        assertEquals(2, decoded.size)
        assertEquals("CHAINFLIP", decoded.first().provider)
        assertEquals(listOf("1", "56", "bitcoin"), decoded.first().supportedChainIds)
        assertEquals(listOf("solana", "1"), decoded[1].supportedChainIds)
    }

    @Test
    fun `quote request serializes only documented fields`() {
        val request =
            SwapKitQuoteRequest(
                sellAsset = "ETH.ETH",
                buyAsset = "ETH.USDT-0xdAC17F958D2ee523a2206206994597C13D831ec7",
                sellAmount = "1000000000000000000",
                sourceAddress = "0xSender",
                destinationAddress = "0xRecipient",
            )

        val encoded = Json { encodeDefaults = true }.encodeToString(request)
        val body = Json.parseToJsonElement(encoded).jsonObject

        // Affiliate identifier is server-side via the partner dashboard — no `affiliate` key on the
        // wire. Only `affiliateFee` (basis points) is part of the documented V3 quote request.
        assertFalse(body.containsKey("affiliate"))
        assertEquals("0", body["affiliateFee"]?.jsonPrimitive?.content)
        assertEquals("1", body["slippage"]?.jsonPrimitive?.content?.substringBefore('.'))
    }

    @Test
    fun `quote request omits source and destination when null`() {
        // V3 marks both addresses optional so quote discovery flows can price before the wallet
        // has picked an account.
        val request =
            SwapKitQuoteRequest(sellAsset = "ETH.ETH", buyAsset = "ETH.USDC", sellAmount = "1")

        val encoded =
            Json {
                    encodeDefaults = true
                    explicitNulls = false
                }
                .encodeToString(request)
        val body = Json.parseToJsonElement(encoded).jsonObject

        assertFalse(body.containsKey("sourceAddress"))
        assertFalse(body.containsKey("destinationAddress"))
    }

    @Test
    fun `swap request carries routeId and addresses only`() {
        // POST /v3/swap is identified by routeId from the previous quote, not the full route blob.
        val request =
            SwapKitSwapRequest(
                routeId = "22222222-2222-2222-2222-222222222222",
                sourceAddress = "0xSender",
                destinationAddress = "0xRecipient",
            )

        val encoded = Json.encodeToString(request)
        val body = Json.parseToJsonElement(encoded).jsonObject

        assertEquals(
            "22222222-2222-2222-2222-222222222222",
            body["routeId"]?.jsonPrimitive?.content,
        )
        assertEquals("0xSender", body["sourceAddress"]?.jsonPrimitive?.content)
        assertEquals("0xRecipient", body["destinationAddress"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("route"))
    }

    @Test
    fun `fromCode maps documented codes to typed errors`() {
        assertInstanceOf(SwapKitError.NoRoutes::class.java, SwapKitError.fromCode("NoRoutesFound"))
        assertInstanceOf(
            SwapKitError.NoRoutes::class.java,
            SwapKitError.fromCode("no_routes_found"),
        )
        assertInstanceOf(
            SwapKitError.QuoteDeviation::class.java,
            SwapKitError.fromCode("OutputAmountDeviationTooHigh"),
        )
        assertInstanceOf(
            SwapKitError.QuoteDeviation::class.java,
            SwapKitError.fromCode("output_amount_deviation_too_high"),
        )
    }

    @Test
    fun `fromCode falls back to network error for unknown or missing code`() {
        val unknown = SwapKitError.fromCode("something_else", "boom")
        assertInstanceOf(SwapKitError.Network::class.java, unknown)
        assertEquals("boom", unknown.message)

        val missing = SwapKitError.fromCode(null)
        assertInstanceOf(SwapKitError.Network::class.java, missing)
        assertEquals("SwapKit request failed", missing.message)
    }

    @Test
    fun `route with unknown extra fields decodes safely`() {
        // Defensive: SwapKit V3 has historically added new optional fields without notice.
        val payload =
            """
            {
              "routes": [
                {
                  "routeId": "x",
                  "providers": ["CHAINFLIP"],
                  "expectedBuyAmount": "1",
                  "futureField": { "anything": true }
                }
              ]
            }
            """
                .trimIndent()

        val route = json.decodeFromString<SwapKitQuoteResponseJson>(payload).routes.single()
        assertNotNull(route)
        assertEquals("1", route.expectedBuyAmount)
    }

    @Test
    fun `swap response tx is preserved as JsonElement for lazy downstream decoding`() {
        val payload =
            """
            {
              "tx": { "to": "0xRouter", "data": "0x", "value": "0" },
              "meta": { "txType": "evm" }
            }
            """
                .trimIndent()

        val response = json.decodeFromString<SwapKitSwapResponseJson>(payload)
        // tx stays raw — caller picks the matching DTO based on meta.type
        val obj = response.tx as JsonObject
        assertEquals("0xRouter", obj["to"]?.jsonPrimitive?.content)
    }
}
