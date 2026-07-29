package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [EvmApiImp.getAllowance] around the failure vs. zero-allowance distinction
 * (#5424): an in-band JSON-RPC error must propagate rather than collapse into a `0` that reads as
 * "no allowance" and forces a needless — and for USDT-style tokens, on-chain-reverting — approve.
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

    private companion object {
        const val CONTRACT = "0x2222222222222222222222222222222222222222"
        const val OWNER = "0x1111111111111111111111111111111111111111"
        const val SPENDER = "0x3333333333333333333333333333333333333333"
    }
}
