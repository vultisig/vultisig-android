package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.BigIntegerSerializerImpl
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import java.math.BigInteger
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Characterization tests for [TronApiImpl] methods that deserialize a response body. Every call
 * site reads through `bodyOrThrow<T>()`; these pin the success-path (HTTP 200) behavior that the
 * migration off the raw `body<T>()` had to preserve.
 *
 * Methods covered:
 * - [TronApi.broadcastTransaction] — `bodyOrThrow<TronBroadcastTxResponseJson>()` (null code + dup
 *   code)
 * - [TronApi.getSpecific] — `bodyOrThrow<TronSpecificBlockJson>()`
 * - [TronApi.getBalance] — `bodyOrThrow<TronBalanceResponseJson>()` (native, TRC-20, empty), plus
 *   the failure path: a read that fails must throw rather than report real funds as zero.
 * - [TronApi.getTsStatus] — `bodyOrThrow<TronTransactionStatusResponse?>()` (present / absent txId)
 */
class TronApiBodyReadTest {

    // TronApiImpl only takes HttpClient — no Json param.
    private fun newApi(body: String): TronApi =
        TronApiImpl(httpClient = MockHttpClient.respondingWith(HttpStatusCode.OK, body))

    // TronBalanceResponseData.balance is @Contextual BigInteger. We supply a
    // custom Json (with the contextual serializer) to ContentNegotiation so the
    // client can deserialize it. TronApiImpl does not accept Json directly, so we
    // build the HttpClient manually — mirroring the pattern in PolkadotApiBodyReadTest.
    private val jsonWithBigInteger = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule { contextual(BigIntegerSerializerImpl()) }
    }

    private fun newApiWithBigInteger(body: String): TronApi {
        val client =
            HttpClient(
                MockEngine {
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = MockHttpClient.JSON_HEADERS,
                    )
                }
            ) {
                install(ContentNegotiation) { json(jsonWithBigInteger, ContentType.Any) }
            }
        return TronApiImpl(httpClient = client)
    }

    // -------------------------------------------------------------------------
    // broadcastTransaction — bodyOrThrow<TronBroadcastTxResponseJson>()
    // -------------------------------------------------------------------------

    @Test
    fun `broadcastTransaction returns txId when code is null`() = runBlocking {
        val body =
            """
            {
              "txid": "abc123",
              "code": null
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.broadcastTransaction(tx = "{}")

        assertEquals("abc123", result)
    }

    @Test
    fun `broadcastTransaction returns txId when code is DUP_TRANSACTION_ERROR`() = runBlocking {
        val body =
            """
            {
              "txid": "dup456",
              "code": "DUP_TRANSACTION_ERROR"
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.broadcastTransaction(tx = "{}")

        assertEquals("dup456", result)
    }

    // -------------------------------------------------------------------------
    // getSpecific — bodyOrThrow<TronSpecificBlockJson>()
    // -------------------------------------------------------------------------

    @Test
    fun `getSpecific returns parsed block header fields`() = runBlocking {
        val body =
            """
            {
              "block_header": {
                "raw_data": {
                  "number": 67890,
                  "txTrieRoot": "trieRoot",
                  "witness_address": "witnessAddr",
                  "parentHash": "parentHash",
                  "version": 27,
                  "timestamp": 1700000000000
                }
              }
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.getSpecific()

        assertEquals(67890UL, result.blockHeader.rawData.number)
        assertEquals("trieRoot", result.blockHeader.rawData.txTrieRoot)
        assertEquals(27UL, result.blockHeader.rawData.version)
    }

    // -------------------------------------------------------------------------
    // getBalance — bodyOrThrow<TronBalanceResponseJson>()
    // -------------------------------------------------------------------------

    @Test
    fun `getBalance returns native token balance`() = runBlocking {
        val body =
            """
            {
              "data": [
                {
                  "balance": 5000000,
                  "trc20": []
                }
              ]
            }
            """
                .trimIndent()
        val coin =
            Coin(
                chain = Chain.Tron,
                ticker = "TRX",
                logo = "",
                address = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "",
                isNativeToken = true,
            )
        val api = newApiWithBigInteger(body)

        val result = api.getBalance(coin)

        assertEquals(BigInteger.valueOf(5_000_000L), result)
    }

    @Test
    fun `getBalance returns TRC-20 token balance`() = runBlocking {
        val contractAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        val body =
            """
            {
              "data": [
                {
                  "balance": 0,
                  "trc20": [
                    { "$contractAddress": "250000" }
                  ]
                }
              ]
            }
            """
                .trimIndent()
        val coin =
            Coin(
                chain = Chain.Tron,
                ticker = "USDT",
                logo = "",
                address = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = contractAddress,
                isNativeToken = false,
            )
        val api = newApiWithBigInteger(body)

        val result = api.getBalance(coin)

        assertEquals(BigInteger.valueOf(250_000L), result)
    }

    @Test
    fun `getBalance returns zero when data array is empty`() = runBlocking {
        val body =
            """
            {
              "data": []
            }
            """
                .trimIndent()
        val coin =
            Coin(
                chain = Chain.Tron,
                ticker = "TRX",
                logo = "",
                address = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "",
                isNativeToken = true,
            )
        val api = newApiWithBigInteger(body)

        val result = api.getBalance(coin)

        assertEquals(BigInteger.ZERO, result)
    }

    @Test
    fun `getBalance throws on a transport failure instead of reading zero`() {
        val api =
            TronApiImpl(httpClient = MockHttpClient.throwingIOException(UnknownHostException()))

        val error = assertThrows<NetworkException> { runBlocking { api.getBalance(nativeCoin()) } }

        assertEquals(0, error.httpStatusCode)
    }

    @Test
    fun `getBalance throws on an HTTP error instead of reading zero`() {
        val api =
            TronApiImpl(
                httpClient =
                    MockHttpClient.respondingWith(HttpStatusCode.BadGateway, "upstream down")
            )

        val error = assertThrows<NetworkException> { runBlocking { api.getBalance(nativeCoin()) } }

        assertEquals(HttpStatusCode.BadGateway.value, error.httpStatusCode)
    }

    private fun nativeCoin() =
        Coin(
            chain = Chain.Tron,
            ticker = "TRX",
            logo = "",
            address = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    // -------------------------------------------------------------------------
    // getTsStatus — bodyOrThrow<TronTransactionStatusResponse?>()
    // -------------------------------------------------------------------------

    @Test
    fun `getTsStatus returns response when txID is present`() = runBlocking {
        val body =
            """
            {
              "txID": "txabc123",
              "ret": [{ "contractRet": "SUCCESS" }]
            }
            """
                .trimIndent()
        val api = newApi(body)

        val result = api.getTsStatus(chain = Chain.Tron, txHash = "txabc123")

        assertEquals("txabc123", result?.txId)
        assertEquals("SUCCESS", result?.ret?.firstOrNull()?.contractRet)
    }

    @Test
    fun `getTsStatus returns null when txID is absent`() = runBlocking {
        // An empty object is what the node returns for an unknown tx;
        // takeIf { it.txId != null } filters it out.
        val body = """{}"""
        val api = newApi(body)

        val result = api.getTsStatus(chain = Chain.Tron, txHash = "unknown")

        assertNull(result)
    }
}
