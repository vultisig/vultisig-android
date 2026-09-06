package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [EvmApiImp.estimateGasForEthTransaction] around issue #5822: a memo-less
 * native send must estimate against the empty calldata it will actually broadcast (not the
 * `0xffffffff` placeholder, which unrelated contracts revert on), and failed estimates must
 * propagate instead of collapsing into a floor-absorbed zero.
 */
class EvmApiTest {

    private fun api(client: HttpClient) =
        EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

    private fun calldata(capture: MockHttpClient.RequestCapture): String =
        Json.parseToJsonElement(capture.lastBody)
            .jsonObject
            .getValue("params")
            .jsonArray[0]
            .jsonObject
            .getValue("data")
            .jsonPrimitive
            .content

    @Test
    fun `estimateGasForEthTransaction sends empty calldata when there is no memo`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequest(
                status = HttpStatusCode.OK,
                body = """{"id":1,"result":"0x5208","error":null}""",
                capture = capture,
            )

        val limit =
            api(client)
                .estimateGasForEthTransaction(
                    senderAddress = "0x1111111111111111111111111111111111111111",
                    recipientAddress = "0x2222222222222222222222222222222222222222",
                    value = BigInteger.TEN,
                    memo = null,
                )

        assertEquals(BigInteger.valueOf(21_000L), limit)
        assertEquals("0x", calldata(capture))
    }

    @Test
    fun `estimateGasForEthTransaction encodes text memo as calldata`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequest(
                status = HttpStatusCode.OK,
                body = """{"id":1,"result":"0x5208","error":null}""",
                capture = capture,
            )

        api(client)
            .estimateGasForEthTransaction(
                senderAddress = "0x1111111111111111111111111111111111111111",
                recipientAddress = "0x2222222222222222222222222222222222222222",
                value = BigInteger.TEN,
                memo = "hi",
            )

        assertEquals("0x6869", calldata(capture))
    }

    @Test
    fun `estimateGasForEthTransaction decodes hex-looking memo like signing path`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequest(
                status = HttpStatusCode.OK,
                body = """{"id":1,"result":"0x5208","error":null}""",
                capture = capture,
            )

        api(client)
            .estimateGasForEthTransaction(
                senderAddress = "0x1111111111111111111111111111111111111111",
                recipientAddress = "0x2222222222222222222222222222222222222222",
                value = BigInteger.TEN,
                memo = "0x1234abcd",
            )

        assertEquals("0x1234abcd", calldata(capture))
    }

    @Test
    fun `estimateGasForEthTransaction propagates an RPC estimate failure`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body =
                    """{"id":1,"result":null,"error":{"code":-32000,"message":"execution reverted"}}""",
            )

        assertFailsWith<NetworkException> {
            api(client)
                .estimateGasForEthTransaction(
                    senderAddress = "0x1111111111111111111111111111111111111111",
                    recipientAddress = "0x2222222222222222222222222222222222222222",
                    value = BigInteger.TEN,
                    memo = null,
                )
        }
    }

    @Test
    fun `estimateGasForEthTransaction propagates a missing RPC estimate result`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":null,"error":null}""",
            )

        assertFailsWith<NetworkException> {
            api(client)
                .estimateGasForEthTransaction(
                    senderAddress = "0x1111111111111111111111111111111111111111",
                    recipientAddress = "0x2222222222222222222222222222222222222222",
                    value = BigInteger.TEN,
                    memo = null,
                )
        }
    }

    @Test
    fun `estimateGasForEthTransaction propagates a malformed RPC estimate result`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"not-a-number","error":null}""",
            )

        assertFailsWith<NetworkException> {
            api(client)
                .estimateGasForEthTransaction(
                    senderAddress = "0x1111111111111111111111111111111111111111",
                    recipientAddress = "0x2222222222222222222222222222222222222222",
                    value = BigInteger.TEN,
                    memo = null,
                )
        }
    }
}
