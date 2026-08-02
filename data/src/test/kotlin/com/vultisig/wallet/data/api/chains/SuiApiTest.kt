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
    fun `getCoinMetadata throws with the real RPC error message and code`() = runTest {
        val api =
            api("""{"id":1,"result":null,"error":{"code":-32602,"message":"bad coin type"}}""")

        val e = assertFailsWith<IllegalStateException> { api.getCoinMetadata(COIN_TYPE) }
        assert(e.message!!.contains("bad coin type")) { "message was: ${e.message}" }
        assert(e.message!!.contains("-32602")) { "message was: ${e.message}" }
    }

    @Test
    fun `getCoinMetadata returns null when the coin publishes no metadata`() = runTest {
        val api = api("""{"id":1,"result":null,"error":null}""")

        assertNull(api.getCoinMetadata(COIN_TYPE))
    }

    @Test
    fun `getCoinMetadata returns the parsed metadata on success`() = runTest {
        val api =
            api(
                """{"id":1,"result":{"decimals":6,"name":"Gold","symbol":"GOLD","description":"","iconUrl":"https://example.test/gold.png","id":"0x9"},"error":null}"""
            )

        val metadata = api.getCoinMetadata(COIN_TYPE)

        assertEquals(6, metadata?.decimals)
        assertEquals("GOLD", metadata?.symbol)
        assertEquals("https://example.test/gold.png", metadata?.iconUrl)
    }

    @Test
    fun `getCoinMetadata leaves an absent iconUrl null`() = runTest {
        val api = api("""{"id":1,"result":{"decimals":9,"symbol":"SILVER"},"error":null}""")

        assertNull(api.getCoinMetadata(COIN_TYPE)?.iconUrl)
    }

    @Test
    fun `getCoinMetadata asks the node for the requested coin type`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val api =
            SuiApiImpl(
                MockHttpClient.capturingRequest(
                    HttpStatusCode.OK,
                    """{"id":1,"result":{"decimals":6,"symbol":"GOLD"},"error":null}""",
                    capture,
                ),
                json,
            )

        api.getCoinMetadata(COIN_TYPE)

        assert(capture.lastBody.contains("suix_getCoinMetadata")) { capture.lastBody }
        assert(capture.lastBody.contains(COIN_TYPE)) { capture.lastBody }
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

    private companion object {
        const val COIN_TYPE =
            "0x0a2b3c4d5e6f7809000000000000000000000000000000000000000000000001::gold::GOLD"
    }
}
