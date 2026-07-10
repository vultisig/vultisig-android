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
 * - **400 BadRequest**: a genuine rejection. The txid in `unknownOutputReferences` is the PARENT tx
 *   that created the spent input, not the tx we broadcast, so it must NOT be returned as success —
 *   the error is surfaced ([IllegalStateException]) and `BroadcastTxUseCase` verifies our actual
 *   hash on-chain instead (issue #5250).
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
    fun `broadcastTransaction throws on BadRequest with unknownOutputReferences`() {
        // The txid inside unknownOutputReferences is the parent tx, not ours — returning it would
        // report success under an unrelated hash, so this must surface as a rejection.
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

        assertThrows(IllegalStateException::class.java) {
            runTest { api.broadcastTransaction(chain = "cardano", signedTransaction = "00") }
        }
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
