package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.swapAggregators.OneInchApiImpl
import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the `slippage` query param 1inch receives. 1inch denominates slippage as a PERCENT (`1` ==
 * 1%), so the user's basis-point tolerance must be divided by 100; Auto (null) falls back to
 * 1inch's historical 0.5% default. That percent value is what bounds the min-received, so a wrong
 * magnitude would silently widen or tighten the user's tolerance. Each request short-circuits on a
 * `BadRequest` so the outgoing params — not the response — are the subject under test.
 */
class OneInchApiSlippageTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun apiCapturing(onRequest: (HttpRequestData) -> Unit) =
        OneInchApiImpl(
            httpClient =
                HttpClient(
                    MockEngine { request ->
                        onRequest(request)
                        respond(
                            """{"statusCode":400,"description":"stop"}""",
                            HttpStatusCode.BadRequest,
                            jsonHeaders,
                        )
                    }
                ),
            oneInchSwapQuoteResponseJsonSerializer = mockk(),
            json = json,
        )

    private suspend fun OneInchApiImpl.quoteWith(slippageBps: Int?) =
        getSwapQuote(
            chain = Chain.Ethereum,
            srcTokenContractAddress = "0xsrc",
            dstTokenContractAddress = "0xdst",
            srcAddress = "0xfrom",
            amount = "1000",
            isAffiliate = false,
            bpsDiscount = 0,
            slippageBps = slippageBps,
        )

    @Test
    fun `auto slippage falls back to the 0_5 percent default`() = runTest {
        var slippageParam: String? = null
        val api = apiCapturing { slippageParam = it.url.parameters["slippage"] }

        api.quoteWith(slippageBps = null)

        assertEquals("0.5", slippageParam)
    }

    @Test
    fun `100 bps slippage is sent as 1_0 percent`() = runTest {
        var slippageParam: String? = null
        val api = apiCapturing { slippageParam = it.url.parameters["slippage"] }

        api.quoteWith(slippageBps = 100)

        assertEquals("1.0", slippageParam)
    }

    @Test
    fun `a 300 bps override is sent as 3_0 percent`() = runTest {
        var slippageParam: String? = null
        val api = apiCapturing { slippageParam = it.url.parameters["slippage"] }

        api.quoteWith(slippageBps = 300)

        assertEquals("3.0", slippageParam)
    }
}
