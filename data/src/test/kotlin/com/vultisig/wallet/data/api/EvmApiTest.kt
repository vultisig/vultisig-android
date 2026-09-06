package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [EvmApiImp.estimateGasForEthTransaction] around issue #5822: a memo-less
 * native send must estimate against the empty calldata it will actually broadcast (not the
 * `0xffffffff` placeholder, which unrelated contracts revert on), while a failed estimate still
 * degrades to zero so callers can apply their existing default gas-limit floor.
 */
class EvmApiTest {

    private fun api(client: HttpClient) =
        EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

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
        assertTrue(
            capture.lastBody.contains(""""data":"0x""""),
            "expected empty calldata, got ${capture.lastBody}",
        )
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

        assertTrue(
            capture.lastBody.contains(""""data":"0x6869""""),
            "expected the memo encoded as calldata, got ${capture.lastBody}",
        )
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

        assertTrue(
            capture.lastBody.contains(""""data":"0x1234abcd""""),
            "expected the hex memo decoded as calldata, got ${capture.lastBody}",
        )
    }

    @Test
    fun `estimateGasForEthTransaction returns zero when RPC estimate fails`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body =
                    """{"id":1,"result":null,"error":{"code":-32000,"message":"execution reverted"}}""",
            )

        val limit =
            api(client)
                .estimateGasForEthTransaction(
                    senderAddress = "0x1111111111111111111111111111111111111111",
                    recipientAddress = "0x2222222222222222222222222222222222222222",
                    value = BigInteger.TEN,
                    memo = null,
                )

        assertEquals(BigInteger.ZERO, limit)
    }

    @Test
    fun `estimateGasForEthTransaction returns zero when RPC estimate result is missing`() =
        runTest {
            val client =
                MockHttpClient.respondingWith(
                    HttpStatusCode.OK,
                    body = """{"id":1,"result":null,"error":null}""",
                )

            val limit =
                api(client)
                    .estimateGasForEthTransaction(
                        senderAddress = "0x1111111111111111111111111111111111111111",
                        recipientAddress = "0x2222222222222222222222222222222222222222",
                        value = BigInteger.TEN,
                        memo = null,
                    )

            assertEquals(BigInteger.ZERO, limit)
        }
}
