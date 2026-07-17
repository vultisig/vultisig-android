package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [EvmApiImp] balance reads around the failure vs. empty-account distinction
 * (issue #5308): a failed read must propagate / be omitted rather than collapse into a fake `0`
 * that gets persisted over the last-known balance, while a genuine on-chain zero still resolves to
 * zero.
 */
class EvmApiBalanceTest {

    private val nativeCoin =
        Coin(
            chain = Chain.Hyperliquid,
            ticker = "ETH",
            logo = "",
            address = "0x0000000000000000000000000000000000000001",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    // #5308: a transport/HTTP failure (here HyperEVM's 403, cf. #4414) must surface instead of
    // being
    // swallowed into a zero balance, so the balance layer keeps the last-known value rather than
    // persisting a fake $0.00 that looks like the funds disappeared.
    @Test
    fun `getBalance propagates an RPC failure instead of swallowing it into zero`() = runTest {
        val client = MockHttpClient.respondingWith(HttpStatusCode.Forbidden, body = "")
        val api = EvmApiImp(client, "https://api.vultisig.com/hyperevm/", Chain.Hyperliquid)

        assertFailsWith<NetworkException> { api.getBalance(nativeCoin) }
    }

    @Test
    fun `getBalance returns parsed amount on 200`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0x5","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/hyperevm/", Chain.Hyperliquid)

        assertEquals(BigInteger.valueOf(5), api.getBalance(nativeCoin))
    }

    // A genuinely empty account (healthy node returning 0x0) must still resolve to a real zero
    // without throwing — the one legitimate zero, distinct from a failed read.
    @Test
    fun `getBalance returns zero for a genuine on-chain zero without throwing`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0x0","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/hyperevm/", Chain.Hyperliquid)

        assertEquals(BigInteger.ZERO, api.getBalance(nativeCoin))
    }

    @Test
    fun `getBalances batches native and token via one multicall on a supported chain`() = runTest {
        val response = aggregate3Response(listOf(true to 5L, true to 10L))
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"$response","error":null}""",
            )
        // Ethereum is in the canonical Multicall3 allowlist, so this goes through aggregate3.
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        val balances = api.getBalances(ADDRESS, listOf("", TOKEN))

        assertEquals(BigInteger.valueOf(5), balances[""]) // native via getEthBalance
        assertEquals(BigInteger.valueOf(10), balances[TOKEN]) // ERC-20 via balanceOf
    }

    // #5308: a Multicall3 partial failure (one sub-call reports success == false) must be OMITTED,
    // not mapped to ZERO — so its cached balance is kept and the other tokens still resolve.
    @Test
    fun `getBalances omits a failed sub-call instead of zeroing it`() = runTest {
        val response = aggregate3Response(listOf(false to null, true to 10L))
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"$response","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        val balances = api.getBalances(ADDRESS, listOf("", TOKEN))

        assertNull(balances[""]) // failed sub-call -> omitted, cached value preserved
        assertEquals(BigInteger.valueOf(10), balances[TOKEN]) // sibling unaffected
    }

    // A genuine zero sub-call (success == true, returnData 0) stays a real zero in the map.
    @Test
    fun `getBalances keeps a genuine zero sub-call as zero`() = runTest {
        val response = aggregate3Response(listOf(true to 0L, true to 10L))
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"$response","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        val balances = api.getBalances(ADDRESS, listOf("", TOKEN))

        assertEquals(BigInteger.ZERO, balances[""])
        assertEquals(BigInteger.valueOf(10), balances[TOKEN])
    }

    // #5308: a sub-call that reports success == true but carries empty/undecodable data (a
    // non-standard token, a proxy that returns nothing without reverting) is a failed read, not a
    // zero — it must be OMITTED so its cached balance is kept, not persisted as a fake 0.
    @Test
    fun `getBalances omits a success sub-call with empty return data instead of zeroing it`() =
        runTest {
            val response = aggregate3Response(listOf(true to null, true to 10L))
            val client =
                MockHttpClient.respondingWith(
                    HttpStatusCode.OK,
                    body = """{"id":1,"result":"$response","error":null}""",
                )
            val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

            val balances = api.getBalances(ADDRESS, listOf("", TOKEN))

            assertNull(balances[""]) // undecodable success -> omitted, cached value preserved
            assertEquals(BigInteger.valueOf(10), balances[TOKEN]) // sibling unaffected
        }

    @Test
    fun `getBalances falls back to per-token native fetch on a chain without Multicall3`() =
        runTest {
            val client =
                MockHttpClient.respondingWith(
                    HttpStatusCode.OK,
                    body = """{"id":1,"result":"0x7","error":null}""",
                )
            // zkSync has no canonical Multicall3 -> per-token eth_getBalance fallback.
            val api = EvmApiImp(client, "https://api.vultisig.com/zksync/", Chain.ZkSync)

            val balances = api.getBalances(ADDRESS, listOf(""))

            assertEquals(BigInteger.valueOf(7), balances[""])
        }

    // #5308: on the per-token fallback path a failed read is omitted, not zeroed.
    @Test
    fun `getBalances omits a failed per-token read on a chain without Multicall3`() = runTest {
        val client = MockHttpClient.respondingWith(HttpStatusCode.Forbidden, body = "")
        val api = EvmApiImp(client, "https://api.vultisig.com/zksync/", Chain.ZkSync)

        val balances = api.getBalances(ADDRESS, listOf(""))

        assertNull(balances[""])
    }

    private companion object {
        const val ADDRESS = "0x1111111111111111111111111111111111111111"
        const val TOKEN = "0x2222222222222222222222222222222222222222"

        private fun word(value: Long): String =
            BigInteger.valueOf(value).toString(16).padStart(64, '0')

        /** Builds the ABI-encoded `(bool,bytes)[]` payload Multicall3's aggregate3 returns. */
        fun aggregate3Response(entries: List<Pair<Boolean, Long?>>): String {
            val elements =
                entries.map { (success, value) ->
                    buildString {
                        append(word(if (success) 1L else 0L))
                        append(word(64L))
                        if (value != null) {
                            append(word(32L))
                            append(word(value))
                        } else {
                            append(word(0L))
                        }
                    }
                }
            val header = word(32L) + word(entries.size.toLong())
            val heads = StringBuilder()
            val tails = StringBuilder()
            var offsetBytes = entries.size * 32
            for (element in elements) {
                heads.append(word(offsetBytes.toLong()))
                tails.append(element)
                offsetBytes += element.length / 2
            }
            return "0x" + header + heads + tails
        }
    }
}
