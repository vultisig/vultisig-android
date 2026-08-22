package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuoteDeserialized
import com.vultisig.wallet.data.api.models.quotes.ThorChainSwapQuoteRequest
import com.vultisig.wallet.data.networkutils.HttpClientConfigurator
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.ThorChainSwapQuoteResponseJsonSerializerImpl
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * Pins the retry policy the swap-quote endpoint runs under.
 *
 * Thornode answers a deterministic rejection — no pool for the pair, a paused pool — with a 500
 * whose body carries the reason. Under the client-wide policy those bodies were retried three times
 * with exponential backoff, which costs more than the caller's whole quote window, so a pair that
 * can never route reached the user as "swap request timed out" instead of the reason.
 */
class ThorChainQuoteRetryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val poollessBody =
        """
        {"code":2,"message":"failed to calculate min swap amount: fail to convert dest fee to src asset pool does not exist","details":[]}
        """
            .trimIndent()

    private fun api(engine: MockEngine): ThorChainApi =
        ThorChainApiImpl(
            httpClient = HttpClient(engine) { HttpClientConfigurator(json).configure(this) },
            thorChainSwapQuoteResponseJsonSerializer =
                ThorChainSwapQuoteResponseJsonSerializerImpl(json),
            json = json,
        )

    private fun request() =
        ThorChainSwapQuoteRequest(
            address = "thor1dst",
            fromAsset = "THOR.RUNE",
            toAsset = "THOR.KUJI",
            amount = "100000000",
            interval = "0",
            referralCode = "",
            bpsDiscount = 0,
        )

    @Test
    fun `a 500 carrying a quote rejection is read, not retried`() = runTest {
        var callCount = 0
        val result =
            api(
                    MockEngine {
                        callCount++
                        respond(poollessBody, HttpStatusCode.InternalServerError, jsonHeaders)
                    }
                )
                .getSwapQuotes(request())

        callCount shouldBe 1
        result.shouldBeInstanceOf<THORChainSwapQuoteDeserialized.Error>()
        result.error.message shouldContain "pool does not exist"
    }

    @Test
    fun `back-pressure on the quote endpoint is still retried`() = runTest {
        var callCount = 0
        api(
                MockEngine {
                    callCount++
                    if (callCount == 1) {
                        respond("", HttpStatusCode.TooManyRequests, jsonHeaders)
                    } else {
                        respond(poollessBody, HttpStatusCode.InternalServerError, jsonHeaders)
                    }
                }
            )
            .getSwapQuotes(request())

        callCount shouldBe 2
    }

    @Test
    fun `a transport failure on the quote endpoint is still retried`() = runTest {
        var callCount = 0
        val api =
            api(
                MockEngine {
                    callCount++
                    throw IOException("Connection failed")
                }
            )

        assertFailsWith<NetworkException> { api.getSwapQuotes(request()) }

        callCount shouldBe 4
    }
}
