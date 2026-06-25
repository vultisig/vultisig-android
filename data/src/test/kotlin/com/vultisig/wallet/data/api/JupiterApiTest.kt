package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.errors.SwapException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JupiterApiTest {

    @Test
    fun `quote request pins the default slippage when on Auto`() {
        val (api, captured) = jupiterApi()

        assertThrows(SwapException.RateLimitExceeded::class.java) {
            runBlocking { api.getSwapQuote(QUOTE_AMOUNT, INPUT_MINT, OUTPUT_MINT, WALLET, null) }
        }

        assertEquals("50", captured.slippageBps)
    }

    @Test
    fun `quote request forwards the user-set slippage override`() {
        val (api, captured) = jupiterApi()

        assertThrows(SwapException.RateLimitExceeded::class.java) {
            runBlocking { api.getSwapQuote(QUOTE_AMOUNT, INPUT_MINT, OUTPUT_MINT, WALLET, 250) }
        }

        assertEquals("250", captured.slippageBps)
    }

    /**
     * The quote GET returns 429 so [JupiterApi.getSwapQuote] short-circuits before the swap POST
     * and its WalletCore compute-budget step, keeping the test on the request it asserts and free
     * of the native JNI.
     */
    private fun jupiterApi(): Pair<JupiterApi, CapturedQuote> {
        val captured = CapturedQuote()
        val api =
            JupiterApiImpl(
                HttpClient(
                    MockEngine { request ->
                        captured.slippageBps = request.url.parameters["slippageBps"]
                        respond(content = "", status = HttpStatusCode.TooManyRequests)
                    }
                ) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                },
                Json { ignoreUnknownKeys = true },
            )
        return api to captured
    }

    private class CapturedQuote(var slippageBps: String? = null)

    private companion object {
        const val QUOTE_AMOUNT = "1000"
        const val INPUT_MINT = "So11111111111111111111111111111111111111112"
        const val OUTPUT_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val WALLET = "Wallet"
    }
}
