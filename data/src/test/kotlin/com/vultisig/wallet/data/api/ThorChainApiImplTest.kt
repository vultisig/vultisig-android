package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.ThorChainSwapQuoteResponseJsonSerializer
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the two methods that had correctness bugs caught in #4244:
 * - `getTransactionDetail` previously wrote HTTP metadata into the DTO instead of parsing the
 *   Cosmos `tx_response` envelope.
 * - `broadcastTransaction` previously double-read the response body.
 */
class ThorChainApiImplTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun newApi(httpStatus: HttpStatusCode, body: String): ThorChainApi =
        ThorChainApiImpl(
            httpClient = MockHttpClient.respondingWith(httpStatus, body),
            thorChainSwapQuoteResponseJsonSerializer =
                mockk<ThorChainSwapQuoteResponseJsonSerializer>(),
            json = json,
        )

    @Test
    fun `getTransactionDetail parses tx_response code, codespace, and raw_log on success`() =
        runBlocking {
            val body =
                """
                {
                  "tx_response": {
                    "code": 0,
                    "codespace": "",
                    "raw_log": "[]"
                  }
                }
                """
                    .trimIndent()
            val api = newApi(HttpStatusCode.OK, body)

            val result = api.getTransactionDetail(tx = "ABC123")

            assertEquals(0, result.code)
            assertEquals("", result.codeSpace)
            assertEquals("[]", result.rawLog)
        }

    @Test
    fun `getTransactionDetail surfaces non-zero tx_response code and codespace`() = runBlocking {
        val body =
            """
            {
              "tx_response": {
                "code": 11,
                "codespace": "sdk",
                "raw_log": "out of gas"
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.getTransactionDetail(tx = "ABC123")

        assertEquals(11, result.code)
        assertEquals("sdk", result.codeSpace)
        assertEquals("out of gas", result.rawLog)
    }

    @Test
    fun `getTransactionDetail returns pending DTO with null code on 404`() = runBlocking {
        val api = newApi(HttpStatusCode.NotFound, "tx not found yet")

        val result = api.getTransactionDetail(tx = "ABC123")

        assertNull(result.code)
        assertNull(result.codeSpace)
        assertEquals("tx not found yet", result.rawLog)
    }

    @Test
    fun `getTransactionDetail throws on non-2xx non-404 statuses`() {
        val api = newApi(HttpStatusCode.InternalServerError, "boom")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { api.getTransactionDetail(tx = "ABC123") }
        }
    }

    @Test
    fun `broadcastTransaction returns txhash on success code zero`() = runBlocking {
        val body =
            """
            {
              "tx_response": {
                "txhash": "DEADBEEF",
                "code": 0
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.broadcastTransaction(tx = "{}")

        assertEquals("DEADBEEF", result)
    }

    @Test
    fun `broadcastTransaction treats ErrTxInMempoolCache (code 19) as success`() = runBlocking {
        val body =
            """
            {
              "tx_response": {
                "txhash": "CAFEBABE",
                "code": 19
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.broadcastTransaction(tx = "{}")

        assertEquals("CAFEBABE", result)
    }

    @Test
    fun `broadcastTransaction throws on non-zero non-mempool-cache code`() {
        val body =
            """
            {
              "tx_response": {
                "txhash": "BAADF00D",
                "code": 5
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val ex =
            assertThrows(IllegalStateException::class.java) {
                runBlocking { api.broadcastTransaction(tx = "{}") }
            }
        // The full raw body is preserved in the exception message so callers can debug.
        assertTrue(ex.message?.contains("\"code\": 5") == true, "actual: ${ex.message}")
    }
}
