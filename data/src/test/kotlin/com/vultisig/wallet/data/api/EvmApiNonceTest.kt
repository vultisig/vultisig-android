package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [EvmApiImp.getNonce] around issue #5571: an RPC failure must propagate
 * instead of collapsing into a fake nonce 0. The method intentionally keeps the `latest` block tag
 * so resend/replace flows can reuse the mined nonce.
 */
class EvmApiNonceTest {

    private fun api(client: io.ktor.client.HttpClient) =
        EvmApiImp(client, "https://api.vultisig.com/ethereum/", Chain.Ethereum)

    @Test
    fun `getNonce propagates an RPC failure instead of swallowing it into zero`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":null,"error":{"code":-32005,"message":"rate limited"}}""",
            )

        assertFailsWith<NetworkException> {
            api(client).getNonce("0x1111111111111111111111111111111111111111")
        }
    }

    @Test
    fun `getNonce returns parsed nonce and keeps latest block tag`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequest(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0x5","error":null}""",
                capture = capture,
            )

        val nonce = api(client).getNonce("0x1111111111111111111111111111111111111111")

        assertEquals(BigInteger.valueOf(5L), nonce)
        assertTrue(
            capture.lastBody.contains(
                """"params":["0x1111111111111111111111111111111111111111","latest"]"""
            ),
            "expected getNonce to keep the latest block tag, got ${capture.lastBody}",
        )
    }
}
