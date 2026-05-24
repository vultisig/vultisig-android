package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.quotes.LiFiSwapQuoteDeserialized
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.LiFiSwapQuoteResponseSerializerImpl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Characterization tests for [LiFiChainApiImpl.getSwapQuote]'s body read. Unlike the other swap
 * APIs, LiFi has **no** explicit `isSuccess` guard: it reads the body text on any status and lets
 * [LiFiSwapQuoteResponseSerializer] branch (an `estimate` key → `Result`, otherwise the body's
 * `message` → `Error`). The body read must therefore stay a non-throwing text read (`bodyAsText()`)
 * — switching it to `bodyOrThrow<String>()` would throw on non-2xx and surface the generic HTTP
 * status description instead of LiFi's own error message. These tests pin that behavior.
 */
class LiFiChainApiBodyReadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun newApi(status: HttpStatusCode, body: String): LiFiChainApi =
        LiFiChainApiImpl(
            httpClient = MockHttpClient.respondingWith(status, body),
            liFiSwapQuoteResponseSerializer = LiFiSwapQuoteResponseSerializerImpl(json),
            json = json,
        )

    private suspend fun LiFiChainApi.quote() =
        getSwapQuote(
            fromChain = "1",
            toChain = "1",
            fromToken = "0xfrom",
            toToken = "0xto",
            fromAmount = "1000",
            fromAddress = "0xaddr",
            toAddress = "0xaddr",
            bpsDiscount = 0,
        )

    @Test
    fun `getSwapQuote surfaces the body error message on a non-2xx response`() = runBlocking {
        val body =
            """
            {
              "message": "insufficient liquidity"
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.BadRequest, body)

        val result = api.quote()

        assertTrue(result is LiFiSwapQuoteDeserialized.Error)
        assertEquals(
            "insufficient liquidity",
            (result as LiFiSwapQuoteDeserialized.Error).error.message,
        )
    }
}
