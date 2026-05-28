package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Characterization tests for [CardanoApiImpl.broadcastTransaction]'s Ogmios response handling. They
 * pin the behavior that must be preserved across the body-read changes:
 * - **200 OK**: parsed via `body<OgmiosTransactionResponse>()` → `bodyOrThrow<...>()` — returns the
 *   transaction id.
 * - **400 BadRequest**: the error body is parsed (must not throw) to recover an already-known
 *   output reference — switches from `body<...>()` to a non-throwing
 *   `json.decodeFromString(bodyAsText())`.
 * - **400 BadRequest without recoverable reference**: surfaces an [IllegalStateException].
 */
class CardanoApiBroadcastTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun newApi(status: HttpStatusCode, body: String): CardanoApi =
        CardanoApiImpl(httpClient = MockHttpClient.respondingWith(status, body), json = json)

    @Test
    fun `broadcastTransaction returns transaction id on success`() = runTest {
        val body =
            """
            {
              "result": { "transaction": { "id": "abc123" } }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.OK, body)

        val result = api.broadcastTransaction(chain = "cardano", signedTransaction = "00")

        assertEquals("abc123", result)
    }

    @Test
    fun `broadcastTransaction recovers id from unknownOutputReferences on BadRequest`() = runTest {
        val body =
            """
            {
              "error": {
                "data": {
                  "unknownOutputReferences": [ { "transaction": { "id": "def456" } } ]
                }
              }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.BadRequest, body)

        val result = api.broadcastTransaction(chain = "cardano", signedTransaction = "00")

        assertEquals("def456", result)
    }

    @Test
    fun `broadcastTransaction throws on BadRequest error without output references`() {
        val body =
            """
            {
              "error": { "message": "invalid transaction" }
            }
            """
                .trimIndent()
        val api = newApi(HttpStatusCode.BadRequest, body)

        assertThrows(IllegalStateException::class.java) {
            runTest { api.broadcastTransaction(chain = "cardano", signedTransaction = "00") }
        }
    }
}
