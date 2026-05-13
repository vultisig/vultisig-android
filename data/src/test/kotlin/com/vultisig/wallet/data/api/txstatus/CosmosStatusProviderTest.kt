package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.CosmosApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.models.cosmos.CosmosTxStatusJson
import com.vultisig.wallet.data.api.models.cosmos.TxResponse
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CosmosStatusProviderTest {

    private val cosmosApi = mockk<CosmosApi>()
    private val cosmosApiFactory =
        mockk<CosmosApiFactory> { every { createCosmosApi(any()) } returns cosmosApi }
    private val provider = CosmosStatusProvider(cosmosApiFactory)

    @Test
    fun `404 response returns NotFound`() = runTest {
        coEvery { cosmosApi.getTxStatus(any()) } returns null

        val result = provider.checkStatus("DEADBEEF", Chain.GaiaChain)

        assertEquals(TransactionResult.NotFound, result)
    }

    @Test
    fun `confirmed transaction returns Confirmed`() = runTest {
        coEvery { cosmosApi.getTxStatus(any()) } returns
            CosmosTxStatusJson(
                txResponse = TxResponse(height = "12345", txHash = "DEADBEEF", code = 0)
            )

        val result = provider.checkStatus("DEADBEEF", Chain.GaiaChain)

        assertEquals(TransactionResult.Confirmed, result)
    }

    @Test
    fun `failed transaction returns Failed with rawLog`() = runTest {
        coEvery { cosmosApi.getTxStatus(any()) } returns
            CosmosTxStatusJson(
                txResponse =
                    TxResponse(
                        height = "12345",
                        txHash = "DEADBEEF",
                        code = 5,
                        rawLog = "insufficient funds",
                    )
            )

        val result = provider.checkStatus("DEADBEEF", Chain.GaiaChain)

        assertEquals(TransactionResult.Failed("insufficient funds"), result)
    }

    @Test
    fun `zero height returns Pending`() = runTest {
        coEvery { cosmosApi.getTxStatus(any()) } returns
            CosmosTxStatusJson(txResponse = TxResponse(height = "0", txHash = "DEADBEEF", code = 0))

        val result = provider.checkStatus("DEADBEEF", Chain.GaiaChain)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `null txResponse returns Pending`() = runTest {
        coEvery { cosmosApi.getTxStatus(any()) } returns CosmosTxStatusJson(txResponse = null)

        val result = provider.checkStatus("DEADBEEF", Chain.GaiaChain)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `network error returns Pending`() = runTest {
        coEvery { cosmosApi.getTxStatus(any()) } throws RuntimeException("network error")

        val result = provider.checkStatus("DEADBEEF", Chain.GaiaChain)

        assertEquals(TransactionResult.Pending, result)
    }
}
