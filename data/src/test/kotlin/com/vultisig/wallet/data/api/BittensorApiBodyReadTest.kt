package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.BigIntegerSerializerImpl
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Characterization tests for [BittensorApiImp] that call `.body<T>()` or go through the shared
 * [com.vultisig.wallet.data.api.utils.postRpc] helper. They pin the success-path (HTTP 200)
 * behavior so it is preserved when the call sites are migrated to `bodyOrThrow<T>()`.
 *
 * Covered call sites:
 * - [BittensorApiImp.getNonce] — via `postRpc<PolkadotGetNonceJson>` (pins the shared Rpc.kt path
 *   for Bittensor-specific parameters)
 * - [BittensorApiImp.broadcastTransaction] — direct
 *   `response.body<PolkadotBroadcastTransactionJson>()`
 *
 * Note: [BittensorApi.getTxStatus] is already covered by [BittensorApiTest].
 */
class BittensorApiBodyReadTest {

    private val jsonWithBigInteger = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule { contextual(BigIntegerSerializerImpl()) }
    }

    private fun newApi(body: String): BittensorApi =
        BittensorApiImp(httpClient = MockHttpClient.respondingWith(HttpStatusCode.OK, body))

    private fun newApiWithBigIntegerJson(body: String): BittensorApi =
        BittensorApiImp(
            httpClient = MockHttpClient.respondingWith(HttpStatusCode.OK, body, jsonWithBigInteger)
        )

    // -------------------------------------------------------------------------
    // getNonce — postRpc<PolkadotGetNonceJson>().result (@Contextual BigInteger)
    // Exercises the shared postRpc helper in Rpc.kt so the Bittensor-specific path is pinned.
    // -------------------------------------------------------------------------

    @Test
    fun `getNonce returns parsed BigInteger result via postRpc`() = runTest {
        val body =
            """
            {
              "jsonrpc": "2.0",
              "result": 7,
              "id": 1
            }
            """
                .trimIndent()
        val api = newApiWithBigIntegerJson(body)

        val result = api.getNonce(address = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY")

        assertEquals(BigInteger.valueOf(7), result)
    }

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
