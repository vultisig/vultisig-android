package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.BigIntegerSerializerImpl
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Characterization tests for [PolkadotApiImp] methods that call `response.body<T>()`. They pin the
 * success-path (HTTP 200) behavior so it is preserved when the call sites are migrated to
 * `bodyOrThrow<T>()`.
 *
 * Methods whose response model contains `@Contextual BigInteger` fields ([getNonce],
 * [getRuntimeVersion]) use a custom [HttpClient] with a [SerializersModule] that registers
 * [BigIntegerSerializerImpl], mirroring the production DI setup.
 */
class PolkadotApiBodyReadTest {

    /** Json with contextual BigInteger support — mirrors production DataModule.provideJson(). */
    private val jsonWithBigInteger = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule { contextual(BigIntegerSerializerImpl()) }
    }

    /**
     * Builds a production-shaped [HttpClient] serving [body] at HTTP 200 with BigInteger support.
     */
    private fun clientWithBigIntegerJson(body: String): HttpClient =
        MockHttpClient.respondingWith(HttpStatusCode.OK, body, jsonWithBigInteger)

    /** Builds a plain [HttpClient] (no contextual BigInteger needed). */
    private fun clientForPlainJson(body: String): HttpClient =
        MockHttpClient.respondingWith(HttpStatusCode.OK, body)

    private fun newApiWith(client: HttpClient): PolkadotApi = PolkadotApiImp(httpClient = client)

    // -------------------------------------------------------------------------
    // getNonce — body<PolkadotGetNonceJson>().result (@Contextual BigInteger)
    // -------------------------------------------------------------------------

    @Test
    fun `getNonce returns parsed BigInteger result`() = runBlocking {
        val body =
            """
            {
              "jsonrpc": "2.0",
              "result": 5,
              "id": 1
            }
            """
                .trimIndent()
        val api = newApiWith(clientWithBigIntegerJson(body))

        val result = api.getNonce(address = "1PjxnhkwLMy4ALhGFa7VHr5Hno9xJb9yNW")

        assertEquals(BigInteger.valueOf(5), result)
    }

    // -------------------------------------------------------------------------
    // getBlockHash — body<PolkadotGetBlockHashJson>().result (plain String)
    // -------------------------------------------------------------------------

    @Test
    fun `getBlockHash returns hash string`() = runBlocking {
        val body =
            """
            {
              "jsonrpc": "2.0",
              "result": "0xabc123def456",
              "id": 1
            }
            """
                .trimIndent()
        val api = newApiWith(clientForPlainJson(body))

        val result = api.getBlockHash(isGenesis = false)

        assertEquals("0xabc123def456", result)
    }

    // -------------------------------------------------------------------------
    // getRuntimeVersion — body<PolkadotGetRunTimeVersionJson>() (@Contextual BigInteger fields)
    // -------------------------------------------------------------------------

    @Test
    fun `getRuntimeVersion returns specVersion and transactionVersion as BigInteger pair`() =
        runBlocking {
            val body =
                """
            {
              "jsonrpc": "2.0",
              "result": {
                "specVersion": 1002006,
                "transactionVersion": 26
              },
              "id": 1
            }
            """
                    .trimIndent()
            val api = newApiWith(clientWithBigIntegerJson(body))

            val (specVersion, transactionVersion) = api.getRuntimeVersion()

            assertEquals(BigInteger.valueOf(1002006), specVersion)
            assertEquals(BigInteger.valueOf(26), transactionVersion)
        }

    // -------------------------------------------------------------------------
    // getBlockHeader — body<PolkadotGetBlockHeaderJson>().result.number → BigInteger from hex
    // -------------------------------------------------------------------------

    @Test
    fun `getBlockHeader parses hex block number and returns BigInteger`() = runBlocking {
        // "0x186a0" = 100000 decimal
        val body =
            """
            {
              "jsonrpc": "2.0",
              "result": {
                "number": "0x186a0"
              },
              "id": 1
            }
            """
                .trimIndent()
        val api = newApiWith(clientForPlainJson(body))

        val result = api.getBlockHeader()

        assertEquals(BigInteger.valueOf(100000), result)
    }

    // -------------------------------------------------------------------------
    // broadcastTransaction — body<PolkadotBroadcastTransactionJson>()
    // -------------------------------------------------------------------------

    @Test
    fun `broadcastTransaction returns result hash when error is absent`() = runBlocking {
        val body =
            """
            {
              "jsonrpc": "2.0",
              "result": "0xdeadbeef",
              "id": 1
            }
            """
                .trimIndent()
        val api = newApiWith(clientForPlainJson(body))

        val result = api.broadcastTransaction(tx = "0x01")

        assertEquals("0xdeadbeef", result)
    }

    @Test
    fun `broadcastTransaction returns null when error code is 1012 already imported`() =
        runBlocking {
            val body =
                """
            {
              "jsonrpc": "2.0",
              "error": {
                "code": 1012,
                "message": "Already Imported",
                "data": null
              },
              "id": 1
            }
            """
                    .trimIndent()
            val api = newApiWith(clientForPlainJson(body))

            val result = api.broadcastTransaction(tx = "0x01")

            assertNull(result)
        }
}
