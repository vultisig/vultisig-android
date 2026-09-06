package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [EvmApiImp.estimateGasForEthTransaction] around issue #5822: a memo-less
 * native send must estimate against the empty calldata it will actually broadcast (not the
 * `0xffffffff` placeholder, which unrelated contracts revert on), and a failed estimate must
 * propagate instead of collapsing into a floor-absorbed zero.
 */
class EvmApiTest {

    private fun api(client: HttpClient) =
        EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

    @Test
    fun `estimateGasForEthTransaction sends empty calldata when there is no memo`() = runTest {
        var requestBody = ""
        val client =
            HttpClient(
                MockEngine { request ->
                    requestBody = request.body.toByteArray().decodeToString()
                    respond(
                        content = """{"id":1,"result":"0x5208","error":null}""",
                        status = HttpStatusCode.OK,
                        headers = MockHttpClient.JSON_HEADERS,
                    )
                }
            ) {
                install(ContentNegotiation) { json() }
                install(DefaultRequest) {
                    headers.appendIfNameAbsent(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString(),
                    )
                }
            }

        val limit =
            api(client)
                .estimateGasForEthTransaction(
                    senderAddress = "0x1111111111111111111111111111111111111111",
                    recipientAddress = "0x2222222222222222222222222222222222222222",
                    value = BigInteger.TEN,
                    memo = null,
                )

        assertEquals(BigInteger.valueOf(21_000L), limit)
        assertTrue(
            requestBody.contains(""""data":"0x""""),
            "expected empty calldata, got $requestBody",
        )
    }

    @Test
    fun `estimateGasForEthTransaction still encodes a memo when present`() = runTest {
        var requestBody = ""
        val client =
            HttpClient(
                MockEngine { request ->
                    requestBody = request.body.toByteArray().decodeToString()
                    respond(
                        content = """{"id":1,"result":"0x5208","error":null}""",
                        status = HttpStatusCode.OK,
                        headers = MockHttpClient.JSON_HEADERS,
                    )
                }
            ) {
                install(ContentNegotiation) { json() }
                install(DefaultRequest) {
                    headers.appendIfNameAbsent(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString(),
                    )
                }
            }

        api(client)
            .estimateGasForEthTransaction(
                senderAddress = "0x1111111111111111111111111111111111111111",
                recipientAddress = "0x2222222222222222222222222222222222222222",
                value = BigInteger.TEN,
                memo = "hi",
            )

        assertTrue(
            requestBody.contains(""""data":"0x6869""""),
            "expected the memo encoded as calldata, got $requestBody",
        )
    }

    @Test
    fun `estimateGasForEthTransaction propagates an RPC failure instead of swallowing it into zero`() =
        runTest {
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
}
