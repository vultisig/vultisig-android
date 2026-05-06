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
        val api = EvmApiImp(client, "https://api.vultisig.com/hyperevm/")

        assertEquals(BigInteger.ZERO, api.getBalance(nativeCoin))
    }

    @Test
    fun `getBalance returns parsed amount on 200`() = runBlocking {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":"0x5","error":null}""",
            )
        val api = EvmApiImp(client, "https://api.vultisig.com/hyperevm/")

        assertEquals(BigInteger.valueOf(5), api.getBalance(nativeCoin))
    }
}
