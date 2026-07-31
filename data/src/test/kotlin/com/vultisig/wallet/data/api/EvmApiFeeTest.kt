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
 * Behavioural tests for [EvmApiImp]'s EIP-1559 fee reads around the same failure-vs-zero
 * distinction as [EvmApiBalanceTest] (issue #5400): an RPC error must propagate rather than
 * collapse into a `0` that lets a signed transaction underprice itself below the real network fee.
 */
class EvmApiFeeTest {

    @Test
    fun `getBaseFee propagates an RPC failure instead of returning zero`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":null,"error":{"code":-32000,"message":"rate limited"}}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertFailsWith<NetworkException> { api.getBaseFee() }
    }

    @Test
    fun `getBaseFee returns parsed base fee on 200`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":{"baseFeePerGas":"0x3b9aca00"},"error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/eth/", Chain.Ethereum)

        assertEquals(BigInteger.valueOf(1_000_000_000), api.getBaseFee())
    }

    @Test
    fun `getMaxPriorityFeePerGas propagates an RPC failure instead of returning zero`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":null,"error":{"code":-32000,"message":"rate limited"}}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/avax/", Chain.Avalanche)

        assertFailsWith<NetworkException> { api.getMaxPriorityFeePerGas() }
    }

    @Test
    fun `getMaxPriorityFeePerGas returns parsed value on 200`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0x77359400","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/avax/", Chain.Avalanche)

        assertEquals(BigInteger.valueOf(2_000_000_000), api.getMaxPriorityFeePerGas())
    }

    @Test
    fun `zkEstimateFee propagates an RPC failure instead of returning zero`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body =
                    """{"id":1,"result":null,"error":{"code":-32601,"message":"method not found"}}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/zksync/", Chain.ZkSync)

        assertFailsWith<NetworkException> {
            api.zkEstimateFee(srcAddress = "0xSender", dstAddress = "0xRecipient", data = "0x")
        }
    }

    @Test
    fun `zkEstimateFee propagates a missing result instead of returning zero`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":null,"error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/zksync/", Chain.ZkSync)

        assertFailsWith<NetworkException> {
            api.zkEstimateFee(srcAddress = "0xSender", dstAddress = "0xRecipient", data = "0x")
        }
    }

    @Test
    fun `zkEstimateFee returns parsed fee on 200`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body =
                    """{"id":1,"result":{"gas_limit":"0x30d40","gas_per_pubdata_limit":"0x14",""" +
                        """"max_fee_per_gas":"0x77359400","max_priority_fee_per_gas":"0x0"},""" +
                        """"error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/zksync/", Chain.ZkSync)

        val fee =
            api.zkEstimateFee(srcAddress = "0xSender", dstAddress = "0xRecipient", data = "0x")

        assertEquals(BigInteger.valueOf(200_000), fee.gasLimit)
        assertEquals(BigInteger.valueOf(2_000_000_000), fee.maxFeePerGas)
    }

    // Avalanche is the one chain whose EIP-1559 calc (EthereumFeeService) reads BOTH getBaseFee()
    // and getMaxPriorityFeePerGas() directly, so a shared RPC outage could previously zero both
    // components at once and sign a tx priced near nothing. Both calls on the same failing
    // endpoint must now fail closed instead of silently combining into an underpriced tx.
    @Test
    fun `Avalanche base fee and priority fee both fail closed on the same RPC outage`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body =
                    """{"id":1,"result":null,"error":{"code":-32000,"message":"node unavailable"}}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/avax/", Chain.Avalanche)

        assertFailsWith<NetworkException> { api.getBaseFee() }
        assertFailsWith<NetworkException> { api.getMaxPriorityFeePerGas() }
    }
}
