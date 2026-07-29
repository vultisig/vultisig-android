package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.SuiApiImpl
import com.vultisig.wallet.data.api.models.SuiExecutionStatus
import com.vultisig.wallet.data.api.models.SuiTransactionBlockEffects
import com.vultisig.wallet.data.api.models.SuiTransactionBlockResponse
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class SuiStatusProviderTest {

    private val suiApi = mockk<SuiApi>()
    private val provider = SuiStatusProvider(suiApi)

    @Test
    fun `null response returns NotFound`() = runTest {
        coEvery { suiApi.checkStatus(any()) } returns null

        assertEquals(TransactionResult.NotFound, provider.checkStatus("h", Chain.Sui))
    }

    @Test
    fun `missing checkpoint returns Pending`() = runTest {
        coEvery { suiApi.checkStatus(any()) } returns
            SuiTransactionBlockResponse(digest = "d", checkpoint = null)

        assertEquals(TransactionResult.Pending, provider.checkStatus("h", Chain.Sui))
    }

    @Test
    fun `success status returns Confirmed`() = runTest {
        coEvery { suiApi.checkStatus(any()) } returns
            SuiTransactionBlockResponse(
                digest = "d",
                checkpoint = 10,
                effects =
                    SuiTransactionBlockEffects(status = SuiExecutionStatus(status = "success")),
            )

        assertEquals(TransactionResult.Confirmed, provider.checkStatus("h", Chain.Sui))
    }

    @Test
    fun `failure status returns Failed with the execution error`() = runTest {
        coEvery { suiApi.checkStatus(any()) } returns
            SuiTransactionBlockResponse(
                digest = "d",
                checkpoint = 10,
                effects =
                    SuiTransactionBlockEffects(
                        status = SuiExecutionStatus(status = "failure", error = "InsufficientGas")
                    ),
            )

        assertEquals(
            TransactionResult.Failed("InsufficientGas"),
            provider.checkStatus("h", Chain.Sui),
        )
    }

    // A generic transport/network exception must still be retried, not treated as terminal — only
    // a classified SuiRpcException (a real JSON-RPC application error) is terminal.
    @Test
    fun `generic api exception returns Pending`() = runTest {
        coEvery { suiApi.checkStatus(any()) } throws RuntimeException("net")

        assertEquals(TransactionResult.Pending, provider.checkStatus("h", Chain.Sui))
    }

    // The core regression test for #5444: a terminal RPC error (any code other than the
    // not-found one) must surface immediately as Failed, not be retried until the poll timeout.
    @Test
    fun `terminal RPC error surfaces as Failed, not endless retry`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body =
                    """{"id":1,"result":null,"error":{"code":-32000,"message":"indexer outage"}}""",
            )
        val realApi = SuiApiImpl(client, Json { ignoreUnknownKeys = true })
        val providerWithRealApi = SuiStatusProvider(realApi)

        assertEquals(
            TransactionResult.Failed("indexer outage"),
            providerWithRealApi.checkStatus("digest", Chain.Sui),
        )
    }

    // The mirror case of the terminal-error test above, driven through the same real SuiApiImpl:
    // the not-found code must still resolve to the retryable NotFound outcome, proving the two
    // RPC error codes are not collapsed into the same result.
    @Test
    fun `not-found RPC code surfaces as NotFound through the real api`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"id":1,"result":null,"error":{"code":-32602,"message":"not found"}}""",
            )
        val realApi = SuiApiImpl(client, Json { ignoreUnknownKeys = true })
        val providerWithRealApi = SuiStatusProvider(realApi)

        assertEquals(
            TransactionResult.NotFound,
            providerWithRealApi.checkStatus("digest", Chain.Sui),
        )
    }
}
