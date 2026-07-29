package com.vultisig.wallet.data.api.chains

import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [SuiApiImpl] around surfacing the JSON-RPC error envelope (issue #5444):
 * every read/write RPC call must include the real `error.message`/`error.code` in its thrown
 * exception instead of a single hardcoded generic string, and `checkStatus` must distinguish a
 * genuine not-found digest from a terminal RPC failure.
 */
class SuiApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun api(body: String, status: HttpStatusCode = HttpStatusCode.OK): SuiApiImpl =
        SuiApiImpl(MockHttpClient.respondingWith(status, body), json)

    @Test
    fun `getBalance throws with the real RPC error message and code`() = runTest {
        val api =
            api("""{"id":1,"result":null,"error":{"code":-32000,"message":"insufficient gas"}}""")

        val e = assertFailsWith<IllegalStateException> { api.getBalance("0xabc", "") }
        assert(e.message!!.contains("insufficient gas")) { "message was: ${e.message}" }
        assert(e.message!!.contains("-32000")) { "message was: ${e.message}" }
    }

    @Test
    fun `getBalance returns parsed amount on success`() = runTest {
        val api = api("""{"id":1,"result":{"totalBalance":"42"},"error":null}""")

        assertEquals(BigInteger.valueOf(42), api.getBalance("0xabc", ""))
    }

    @Test
    fun `getReferenceGasPrice throws with the real RPC error message and code`() = runTest {
        val api =
            api("""{"id":1,"result":null,"error":{"code":-32603,"message":"node overloaded"}}""")

        val e = assertFailsWith<IllegalStateException> { api.getReferenceGasPrice() }
        assert(e.message!!.contains("node overloaded")) { "message was: ${e.message}" }
        assert(e.message!!.contains("-32603")) { "message was: ${e.message}" }
    }

    @Test
    fun `getAllCoins throws with the real RPC error message and code`() = runTest {
        val api =
            api("""{"id":1,"result":null,"error":{"code":-32602,"message":"invalid address"}}""")

        val e = assertFailsWith<IllegalStateException> { api.getAllCoins("0xabc") }
        assert(e.message!!.contains("invalid address")) { "message was: ${e.message}" }
        assert(e.message!!.contains("-32602")) { "message was: ${e.message}" }
    }

    @Test
    fun `executeTransactionBlock throws with the real RPC error message and code`() = runTest {
        val api =
            api("""{"id":1,"result":null,"error":{"code":-32000,"message":"GasBalanceTooLow"}}""")

        val e =
            assertFailsWith<IllegalStateException> {
                api.executeTransactionBlock("tx-bytes", "sig")
            }
        assert(e.message!!.contains("GasBalanceTooLow")) { "message was: ${e.message}" }
        assert(e.message!!.contains("-32000")) { "message was: ${e.message}" }
    }

    @Test
    fun `dryRunTransaction throws with the real RPC error message and code`() = runTest {
        val api =
            api(
                """{"id":1,"result":null,"error":{"code":-32602,"message":"invalid transaction bytes"}}"""
            )

        val e = assertFailsWith<IllegalStateException> { api.dryRunTransaction("tx-bytes") }
        assert(e.message!!.contains("invalid transaction bytes")) { "message was: ${e.message}" }
        assert(e.message!!.contains("-32602")) { "message was: ${e.message}" }
    }

    @Test
    fun `checkStatus returns null for the not-found RPC code`() = runTest {
        val api =
            api(
                """{"id":1,"result":null,"error":{"code":-32602,"message":"Could not find the referenced transaction"}}"""
            )

        assertNull(api.checkStatus("digest"))
    }

    @Test
    fun `checkStatus throws SuiRpcException for a terminal RPC error`() = runTest {
        val api =
            api("""{"id":1,"result":null,"error":{"code":-32000,"message":"indexer outage"}}""")

        val e = assertFailsWith<SuiRpcException> { api.checkStatus("digest") }
        assertEquals(-32000, e.rpcError.code)
        assertEquals("indexer outage", e.rpcError.message)
    }

    @Test
    fun `checkStatus returns the parsed response on success`() = runTest {
        val api = api("""{"id":1,"result":{"digest":"abc","checkpoint":10},"error":null}""")

        assertEquals("abc", api.checkStatus("digest")?.digest)
    }
}
