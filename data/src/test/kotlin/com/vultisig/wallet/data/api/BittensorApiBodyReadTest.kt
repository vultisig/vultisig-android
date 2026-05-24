package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Characterization tests for [BittensorApiImp.broadcastTransaction] which calls
 * `response.body<PolkadotBroadcastTransactionJson>()`. They pin the success-path (HTTP 200)
 * behavior so it is preserved when the call site is migrated to `bodyOrThrow<T>()`.
 *
 * Note: [BittensorApi.getTxStatus] is already covered by [BittensorApiTest]; this class only covers
 * the one uncovered `.body<>()` call site.
 */
class BittensorApiBodyReadTest {

    private fun newApi(body: String): BittensorApi =
        BittensorApiImp(httpClient = MockHttpClient.respondingWith(HttpStatusCode.OK, body))

    // -------------------------------------------------------------------------
    // broadcastTransaction — body<PolkadotBroadcastTransactionJson>()
    // -------------------------------------------------------------------------

    @Test
    fun `broadcastTransaction returns result hash on success when error is absent`() = runTest {
        val body =
            """
            {
              "jsonrpc": "2.0",
              "result": "0xcafe0001",
              "id": 1
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.broadcastTransaction(tx = "0x01")

        assertEquals("0xcafe0001", result)
    }

    @Test
    fun `broadcastTransaction returns null when error message contains Already Imported`() =
        runTest {
            val body =
                """
                {
                  "jsonrpc": "2.0",
                  "error": {
                    "code": 1013,
                    "message": "Already Imported",
                    "data": null
                  },
                  "id": 1
                }
                """
                    .trimIndent()
            val api = newApi(body)

            val result = api.broadcastTransaction(tx = "0x01")

            assertNull(result)
        }
}
