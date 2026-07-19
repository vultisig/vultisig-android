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
import org.junit.jupiter.api.assertThrows

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

    @Test
    fun `broadcastTransaction resolves duplicate to null via numeric code 1013`() = runTest {
        // Bittensor previously matched only the case-sensitive "Already Imported" string and
        // ignored the numeric code on the wire; the shared classifier now recognizes code 1013.
        val body =
            """
            {
              "jsonrpc": "2.0",
              "error": {
                "code": 1013,
                "message": "Transaction is already imported",
                "data": null
              },
              "id": 1
            }
            """
                .trimIndent()
        val api = newApi(body)

        assertNull(api.broadcastTransaction(tx = "0x01"))
    }

    @Test
    fun `broadcastTransaction throws on temporarily-banned code 1012`() = runTest {
        // 1012 is TemporarilyBanned, not a harmless duplicate: it must throw so the caller verifies
        // on-chain rather than fabricating a success hash.
        val body =
            """
            {
              "jsonrpc": "2.0",
              "error": {
                "code": 1012,
                "message": "Transaction is temporarily banned",
                "data": null
              },
              "id": 1
            }
            """
                .trimIndent()
        val api = newApi(body)

        assertThrows<Exception> { api.broadcastTransaction(tx = "0x01") }
    }

    @Test
    fun `broadcastTransaction resolves duplicate to null on a non-2xx idempotent response`() =
        runTest {
            // The relay may surface the idempotent "already imported" error with a non-2xx HTTP
            // status; broadcastTransaction reads the body regardless of status and must still
            // resolve the duplicate rebroadcast to null rather than throw.
            val body =
                """
                {
                  "jsonrpc": "2.0",
                  "error": {
                    "code": 1013,
                    "message": "Transaction is already imported",
                    "data": null
                  },
                  "id": 1
                }
                """
                    .trimIndent()
            val api =
                BittensorApiImp(
                    httpClient = MockHttpClient.respondingWith(HttpStatusCode.BadRequest, body)
                )

            assertNull(api.broadcastTransaction(tx = "0x01"))
        }

    @Test
    fun `broadcastTransaction throws on a truncated body with neither result nor error`() =
        runTest {
            // (null, null) under explicitNulls=false must not be reported as a successful
            // broadcast.
            val body =
                """
            {
              "jsonrpc": "2.0",
              "id": 1
            }
            """
                    .trimIndent()
            val api = newApi(body)

            assertThrows<Exception> { api.broadcastTransaction(tx = "0x01") }
        }
}
