package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteDeserialized
import com.vultisig.wallet.data.api.swapAggregators.OneInchApi
import com.vultisig.wallet.data.api.swapAggregators.OneInchApiImpl
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.OneInchSwapQuoteResponseJsonSerializer
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Characterization test for [OneInchApiImpl.getSwapQuote]'s non-2xx branch. It reads the error body
 * and surfaces [EVMSwapQuoteDeserialized.Error] with the parsed `description`. This pins the
 * behavior that must be preserved when the body read switches from `body<T>()` to a non-throwing
 * `json.decodeFromString(bodyAsText())` (the error body is read on a non-2xx response, so it must
 * not throw before parsing).
 */
class OneInchApiErrorPathTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun newApi(status: HttpStatusCode, body: String): OneInchApi =
        OneInchApiImpl(
            httpClient = MockHttpClient.respondingWith(status, body),
            oneInchSwapQuoteResponseJsonSerializer =
                mockk<OneInchSwapQuoteResponseJsonSerializer>(),
            json = json,
        )

    @Test
    fun `getSwapQuote returns Error with description on non-2xx response`() = runTest {
        val body =
            """
            {
              "statusCode": 400,
              "error": "Bad Request",
              "description": "insufficient liquidity"
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.BadRequest, body)

        val result =
            api.getSwapQuote(
                chain = Chain.Ethereum,
                srcTokenContractAddress = "0xsrc",
                dstTokenContractAddress = "0xdst",
                srcAddress = "0xfrom",
                amount = "1000",
                isAffiliate = false,
                bpsDiscount = 0,
            )

        assertTrue(result is EVMSwapQuoteDeserialized.Error)
        assertEquals("insufficient liquidity", (result as EVMSwapQuoteDeserialized.Error).error)
    }
}
