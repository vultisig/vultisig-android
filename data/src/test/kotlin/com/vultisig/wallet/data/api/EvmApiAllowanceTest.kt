package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [EvmApiImp.getAllowance] around the failure vs. zero-allowance distinction
 * (#5424): an in-band JSON-RPC error must propagate rather than collapse into a `0` that reads as
 * "no allowance" and forces a needless — and for USDT-style tokens, on-chain-reverting — approve.
 * [EvmApiImp.doesErc20ApproveRevert] is the follow-on probe for that revert and holds to the same
 * rule: a node that did not answer is not a node that said no.
 */
class EvmApiAllowanceTest {

    @Test
    fun `getAllowance propagates an RPC-level error instead of swallowing it into zero`() =
        runTest {
            val client =
                MockHttpClient.respondingWith(
                    HttpStatusCode.OK,
                    body =
                        """{"id":1,"result":null,"error":{"code":-32000,"message":"rate limited"}}""",
                )
            val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

            assertFailsWith<NetworkException> { api.getAllowance(CONTRACT, OWNER, SPENDER) }
        }

    @Test
    fun `getAllowance returns parsed amount on success`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0x64","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertEquals(BigInteger.valueOf(100), api.getAllowance(CONTRACT, OWNER, SPENDER))
    }

    // A genuinely unapproved spender (healthy node returning 0x0) must still resolve to a real
    // zero without throwing — the one legitimate zero, distinct from a failed read.
    @Test
    fun `getAllowance returns zero for a genuine on-chain zero without throwing`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0x0","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertEquals(BigInteger.ZERO, api.getAllowance(CONTRACT, OWNER, SPENDER))
    }

    // A null result with no explicit `error` is still a failed read, not a zero — a healthy node
    // always returns "0x0" for a real one.
    @Test
    fun `getAllowance propagates a null result instead of swallowing it into zero`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":null,"error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertFailsWith<NetworkException> { api.getAllowance(CONTRACT, OWNER, SPENDER) }
    }

    // doesErc20ApproveRevert simulates the approve the initiator is about to sign. Only the node
    // saying "reverted" may turn into the extra approve(0) leg; every other outcome is either a
    // clean success or no answer at all.

    @Test
    fun `doesErc20ApproveRevert simulates approve(spender, amount) from the owner`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequest(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0x","error":null}""",
                capture = capture,
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        api.doesErc20ApproveRevert(CONTRACT, OWNER, SPENDER, BigInteger.valueOf(5_000_000))

        val call = Json.parseToJsonElement(capture.lastBody).jsonObject
        assertEquals("eth_call", call.getValue("method").jsonPrimitive.content)
        val tx = call.getValue("params").jsonArray[0].jsonObject
        assertEquals(OWNER, tx.getValue("from").jsonPrimitive.content)
        assertEquals(CONTRACT, tx.getValue("to").jsonPrimitive.content)
        assertEquals(
            "0x095ea7b3" +
                "000000000000000000000000" +
                SPENDER.removePrefix("0x") +
                "00000000000000000000000000000000000000000000000000000000004c4b40",
            tx.getValue("data").jsonPrimitive.content,
        )
    }

    // USDT's approve returns nothing, so a successful simulation is an empty "0x", not a bool.
    @Test
    fun `doesErc20ApproveRevert reads a no-data success as not reverting`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0x","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertFalse(api.doesErc20ApproveRevert(CONTRACT, OWNER, SPENDER, BigInteger.ONE))
    }

    // A standard ERC-20 approve returns `true`; that word is a success too.
    @Test
    fun `doesErc20ApproveRevert reads a bool success as not reverting`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body =
                    """{"id":1,"result":"0x0000000000000000000000000000000000000000000000000000000000000001","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertFalse(api.doesErc20ApproveRevert(CONTRACT, OWNER, SPENDER, BigInteger.ONE))
    }

    // A bare revert() carries no data, so geth reports it under the generic -32000 and only the
    // message says it was the call that failed.
    @Test
    fun `doesErc20ApproveRevert reads an execution reverted error as a revert`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"error":{"code":-32000,"message":"execution reverted"}}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertTrue(api.doesErc20ApproveRevert(CONTRACT, OWNER, SPENDER, BigInteger.ONE))
    }

    @Test
    fun `doesErc20ApproveRevert reads an EIP-1474 execution error as a revert`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"error":{"code":3,"message":"execution reverted","data":"0x"}}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertTrue(api.doesErc20ApproveRevert(CONTRACT, OWNER, SPENDER, BigInteger.ONE))
    }

    // A node that could not run the call has not answered; treating that as "no reset needed"
    // would sign the approve that reverts, and as "reset needed" would sign a needless leg.
    @Test
    fun `doesErc20ApproveRevert propagates a non-revert RPC error`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"error":{"code":-32005,"message":"rate limited"}}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertFailsWith<NetworkException> {
            api.doesErc20ApproveRevert(CONTRACT, OWNER, SPENDER, BigInteger.ONE)
        }
    }

    // A result that is not return data is a node that did not run the call either.
    @Test
    fun `doesErc20ApproveRevert propagates a malformed result with no error`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0xabc","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertFailsWith<NetworkException> {
            api.doesErc20ApproveRevert(CONTRACT, OWNER, SPENDER, BigInteger.ONE)
        }
    }

    @Test
    fun `doesErc20ApproveRevert propagates a null result with no error`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":null,"error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertFailsWith<NetworkException> {
            api.doesErc20ApproveRevert(CONTRACT, OWNER, SPENDER, BigInteger.ONE)
        }
    }

    private companion object {
        const val CONTRACT = "0x2222222222222222222222222222222222222222"
        const val OWNER = "0x1111111111111111111111111111111111111111"
        const val SPENDER = "0x3333333333333333333333333333333333333333"
    }
}
