package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Covers the `eth_call` replay behind [EvmApi.getRevertReason] (#5802).
 *
 * A receipt only carries a pass/fail bit, so the reason a swap reverted has to be read back by
 * replaying the transaction. These tests pin what the replay sends — the transaction's own fields,
 * at its own block — and that a node with nothing useful to say leaves the reason unknown rather
 * than inventing one.
 */
class EvmApiRevertReasonTest {

    private val txHash = "0x7d71f95c095a79b26aae0dd1b602c5d62b848959976233d2a5b7717891fe2b13"
    private val sender = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb1"
    private val router = "0x1231DEB6f5749EF6cE6943a275A1D3E7486F4EaE"

    /** `Error(string)` carrying LI.FI's min-output revert, as the LiFiDiamond emits it. */
    private val insufficientOutput =
        "0x08c379a0" +
            "0000000000000000000000000000000000000000000000000000000000000020" +
            "0000000000000000000000000000000000000000000000000000000000000013" +
            "496e73756666696369656e74206f757470757400000000000000000000000000"

    private val minedTx =
        """
        {"jsonrpc":"2.0","id":1,"result":{
          "from":"$sender","to":"$router","input":"0x12aa3caf","value":"0x0",
          "gas":"0x53e2b","blockNumber":"0x18a5f0d"
        }}
        """
            .trimIndent()

    private fun api(client: io.ktor.client.HttpClient) =
        EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

    @Test
    fun `decodes the revert reason a replay reports`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequestSequence(
                capture,
                HttpStatusCode.OK to minedTx,
                HttpStatusCode.OK to
                    """{"jsonrpc":"2.0","id":1,"error":{"code":3,
                       "message":"execution reverted","data":"$insufficientOutput"}}""",
            )

        assertEquals("Insufficient output", api(client).getRevertReason(txHash))
    }

    @Test
    fun `replays the transaction's own fields at its own block`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequestSequence(
                capture,
                HttpStatusCode.OK to minedTx,
                HttpStatusCode.OK to
                    """{"jsonrpc":"2.0","id":1,"error":{"code":3,
                       "message":"execution reverted: Insufficient output"}}""",
            )

        api(client).getRevertReason(txHash)

        val lookup = capture.bodies.first()
        assertTrue(lookup.contains("eth_getTransactionByHash"), lookup)
        assertTrue(lookup.contains(txHash), lookup)

        val replay = capture.bodies.last()
        assertTrue(replay.contains("eth_call"), replay)
        assertTrue(replay.contains(sender), replay)
        assertTrue(replay.contains(router), replay)
        assertTrue(replay.contains("0x12aa3caf"), replay)
        assertTrue(replay.contains("0x53e2b"), replay)
        // The block that mined it, not its predecessor: an approval in the same block has to be
        // visible or the replay reports an allowance failure instead of the real reason.
        assertTrue(replay.contains("0x18a5f0d"), replay)
    }

    @Test
    fun `a node that only says it reverted yields no reason`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequestSequence(
                capture,
                HttpStatusCode.OK to minedTx,
                HttpStatusCode.OK to
                    """{"jsonrpc":"2.0","id":1,"error":{"code":3,"message":"execution reverted"}}""",
            )

        assertNull(api(client).getRevertReason(txHash))
    }

    @Test
    fun `a replay that succeeds yields no reason`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequestSequence(
                capture,
                HttpStatusCode.OK to minedTx,
                HttpStatusCode.OK to """{"jsonrpc":"2.0","id":1,"result":"0x"}""",
            )

        assertNull(api(client).getRevertReason(txHash))
    }

    /**
     * A node too far behind to hold the block's state has no reason to give — and must not throw.
     */
    @Test
    fun `a replay the node refuses yields no reason`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequestSequence(
                capture,
                HttpStatusCode.OK to minedTx,
                HttpStatusCode.InternalServerError to
                    """{"jsonrpc":"2.0","id":1,"error":{"code":-32000,
                       "message":"missing trie node"}}""",
            )

        assertNull(api(client).getRevertReason(txHash))
    }

    @Test
    fun `a transaction still in the mempool is not replayed`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequestSequence(
                capture,
                HttpStatusCode.OK to
                    """{"jsonrpc":"2.0","id":1,"result":{
                       "from":"$sender","to":"$router","input":"0x12aa3caf","blockNumber":null}}""",
            )

        assertNull(api(client).getRevertReason(txHash))
        assertEquals(1, capture.bodies.size)
    }

    @Test
    fun `an unknown transaction hash yields no reason`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                """{"jsonrpc":"2.0","id":1,"result":null}""",
            )

        assertNull(api(client).getRevertReason(txHash))
    }
}
