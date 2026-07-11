package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Broadcast dedup/recovery behavior for [EvmApiImp.sendTransaction] (issue #5250): a dedup
 * rejection must not be reported as success unless our actual tx is confirmed on-chain, and the old
 * bare "known" substring (which also matched "unknown …") must no longer swallow rejections.
 */
class EvmApiBroadcastTest {

    // Arbitrary signed-tx bytes; sendTransaction returns keccak256 of these on a dedup hit.
    private val signedTx = "02f8"

    private fun api(client: io.ktor.client.HttpClient) =
        EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

    private fun errorResponse(message: String) =
        HttpStatusCode.OK to """{"result":null,"error":{"message":"$message"}}"""

    private fun receiptResponse(result: String) =
        HttpStatusCode.OK to """{"id":1,"result":$result}"""

    @Test
    fun `already known is treated as success without an on-chain check`() = runTest {
        val client =
            MockHttpClient.respondingWith(HttpStatusCode.OK, errorResponse("already known").second)
        val hash = api(client).sendTransaction(signedTx)
        assert(hash.startsWith("0x")) { "expected a keccak hash, got $hash" }
    }

    @Test
    fun `unknown sender rejection is surfaced, not swallowed`() = runTest {
        // "unknown sender" contains "known" — the old bare match wrongly reported it as success.
        val client =
            MockHttpClient.respondingWith(HttpStatusCode.OK, errorResponse("unknown sender").second)
        assertFailsWith<Exception> { api(client).sendTransaction(signedTx) }
    }

    @Test
    fun `known transaction hash variant is treated as success without an on-chain check`() =
        runTest {
            // geth/Erigon emit "known transaction: 0x…"; the old bare "known" caught it, exact
            // "already known" did not. Must stay a success (our exact bytes are already held).
            val client =
                MockHttpClient.respondingWith(
                    HttpStatusCode.OK,
                    errorResponse("known transaction: 0xabc").second,
                )
            val hash = api(client).sendTransaction(signedTx)
            assert(hash.startsWith("0x")) { "expected a keccak hash, got $hash" }
        }

    @Test
    fun `nonce too low is success only when our hash is actually mined`() = runTest {
        // error, then not-yet-mined (null receipt), then mined — exercises the retry loop so a
        // regression to a single attempt or broken backoff fails.
        val client =
            MockHttpClient.respondingWithSequence(
                errorResponse("nonce too low: next nonce 5"),
                receiptResponse("null"),
                receiptResponse("""{"status":"0x1"}"""),
            )
        val hash = api(client).sendTransaction(signedTx)
        assert(hash.startsWith("0x"))
    }

    @Test
    fun `nonce too low is surfaced when our hash never lands (different tx consumed the nonce)`() =
        runTest {
            val client =
                MockHttpClient.respondingWithSequence(
                    errorResponse("nonce too low: next nonce 5"),
                    receiptResponse("null"), // receipt absent on every verify attempt
                )
            assertFailsWith<Exception> { api(client).sendTransaction(signedTx) }
        }

    @Test
    fun `plain success returns the node-provided hash`() = runTest {
        val client =
            MockHttpClient.respondingWith(HttpStatusCode.OK, """{"result":"0xabc","error":null}""")
        assertEquals("0xabc", api(client).sendTransaction(signedTx))
    }
}
