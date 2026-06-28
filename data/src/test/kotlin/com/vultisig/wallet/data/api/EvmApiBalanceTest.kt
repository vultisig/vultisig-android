package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

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

    // Reproduces issue #4414: HyperEVM RPC proxy returns 403 with no Content-Type. Before the fix,
    // the raw `.body<RpcResponse>()` call threw NoTransformationFoundException out of
    // getETHBalance,
    // surfacing in keysign as a misleading "Invalid QR code content" error.
    @Test
    fun `getBalance returns zero when RPC returns 403`() = runBlocking {
        val client = MockHttpClient.respondingWith(HttpStatusCode.Forbidden, body = "")
        val api = EvmApiImp(client, "https://api.vultisig.com/hyperevm/", Chain.Hyperliquid)

        assertEquals(BigInteger.ZERO, api.getBalance(nativeCoin))
    }

    @Test
    fun `getBalance returns parsed amount on 200`() = runBlocking {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0x5","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/hyperevm/", Chain.Hyperliquid)

        assertEquals(BigInteger.valueOf(5), api.getBalance(nativeCoin))
    }

    @Test
    fun `getBalances batches native and token via one multicall on a supported chain`() =
        runBlocking {
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

    @Test
    fun `getBalances decodes a failed sub-call as zero without failing siblings`() = runBlocking {
        val response = aggregate3Response(listOf(false to null, true to 10L))
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"$response","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        val balances = api.getBalances(ADDRESS, listOf("", TOKEN))

        assertEquals(BigInteger.ZERO, balances[""]) // failed call -> zero
        assertEquals(BigInteger.valueOf(10), balances[TOKEN])
    }

    @Test
    fun `getBalances falls back to per-token native fetch on a chain without Multicall3`() =
        runBlocking {
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
