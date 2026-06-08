package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.models.KyberSwapRouteResponse
import com.vultisig.wallet.data.api.swapAggregators.KyberApiImpl
import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pins the slippage tolerance sent to KyberSwap's aggregator API. KyberSwap denominates
 * `slippageTolerance` in basis points (10000 = 100%), so the integer 100 (= 1%) must be sent
 * verbatim on both the `/routes` query and the `/route/build` body, matching iOS and the Windows
 * extension. Each test responds with an error so the request — not the (unparsed) response — is the
 * subject under test.
 */
class KyberApiSlippageToleranceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun apiCapturing(onRequest: (HttpRequestData) -> Unit) =
        KyberApiImpl(
            httpClient =
                HttpClient(
                    MockEngine { request ->
                        onRequest(request)
                        respond("""{"message":"stop"}""", HttpStatusCode.BadRequest, jsonHeaders)
                    }
                ),
            kyberSwapQuoteResponseJsonSerializer = mockk(),
            json = json,
        )

    @Test
    fun `getSwapQuote sends 100 basis point slippage tolerance on the routes query`() = runTest {
        var slippageParam: String? = null
        val api = apiCapturing { slippageParam = it.url.parameters["slippageTolerance"] }

        api.getSwapQuote(
            chain = Chain.Ethereum,
            srcTokenContractAddress = "0xsrc",
            dstTokenContractAddress = "0xdst",
            amount = "1000",
            srcAddress = "0xfrom",
            affiliateBps = 0,
        )

        assertEquals("100", slippageParam)
    }

    @Test
    fun `getKyberSwapQuote sends 100 basis point slippage tolerance as an integer in the build body`() =
        runTest {
            var requestBody: String? = null
            val api = apiCapturing { requestBody = (it.body as TextContent).text }

            assertThrows<SwapException> {
                api.getKyberSwapQuote(
                    chain = Chain.Ethereum,
                    routeSummary = routeSummary(),
                    from = "0xfrom",
                    enableGasEstimation = true,
                    affiliateBps = 0,
                )
            }

            val slippage =
                json.parseToJsonElement(requestBody!!).jsonObject.getValue("slippageTolerance")
            assertEquals("100", slippage.jsonPrimitive.content)
        }

    private fun routeSummary() =
        KyberSwapRouteResponse.RouteSummary(
            tokenIn = "0xsrc",
            amountIn = "1000",
            amountInUsd = "1",
            tokenOut = "0xdst",
            amountOut = "990",
            amountOutUsd = "1",
            gas = "21000",
            gasPrice = "1",
            gasUsd = "0",
            route = emptyList(),
            routeID = "route-id",
            checksum = "checksum",
            timestamp = 0,
        )
}
