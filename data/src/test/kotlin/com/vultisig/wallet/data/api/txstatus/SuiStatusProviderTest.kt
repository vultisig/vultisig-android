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

    // The core regression test for #5444, carried onto GraphQL (#5506): a node refusal must
    // surface immediately as Failed, not be retried until the poll timeout.
    @Test
    fun `node refusal surfaces as Failed, not endless retry`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body =
                    """{"data":null,"errors":[{"message":"indexer outage","extensions":{"code":"INTERNAL_SERVER_ERROR"}}]}""",
            )
        val realApi = SuiApiImpl(client, Json { ignoreUnknownKeys = true })
        val providerWithRealApi = SuiStatusProvider(realApi)

        assertEquals(
            TransactionResult.Failed("indexer outage"),
            providerWithRealApi.checkStatus("digest", Chain.Sui),
        )
    }

    // The mirror case of the refusal test above, driven through the same real SuiApiImpl: a digest
    // that hasn't landed resolves to a null transaction with no errors, and must still reach the
    // retryable NotFound outcome rather than being collapsed into the terminal one.
    @Test
    fun `unlanded digest surfaces as NotFound through the real api`() = runTest {
        val client =
            MockHttpClient.respondingWith(
                HttpStatusCode.OK,
                body = """{"data":{"transaction":null}}""",
            )
        val realApi = SuiApiImpl(client, Json { ignoreUnknownKeys = true })
        val providerWithRealApi = SuiStatusProvider(realApi)

        assertEquals(
            TransactionResult.NotFound,
            providerWithRealApi.checkStatus("digest", Chain.Sui),
        )
    }
}
