package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.TonStatusResult
import com.vultisig.wallet.data.api.chains.ton.TonTransactionDescriptionJson
import com.vultisig.wallet.data.api.chains.ton.TransactionJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TonStatusProviderTest {

    private val tonApi = mockk<TonApi>()
    private val provider = TonStatusProvider(tonApi)

    @Test
    fun `empty transactions returns NotFound`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns TonStatusResult(transactions = emptyList())

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.NotFound, result)
    }

    @Test
    fun `transaction with aborted true returns Failed`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            TonStatusResult(
                transactions =
                    listOf(
                        TransactionJson(description = TonTransactionDescriptionJson(aborted = true))
                    )
            )

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.Failed("Transaction aborted"), result)
    }

    @Test
    fun `transaction with aborted false returns Confirmed`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            TonStatusResult(
                transactions =
                    listOf(
                        TransactionJson(
                            description = TonTransactionDescriptionJson(aborted = false)
                        )
                    )
            )

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.Confirmed, result)
    }

    @Test
    fun `transaction with null description returns Pending`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            TonStatusResult(transactions = listOf(TransactionJson(description = null)))

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `transaction present without aborted field returns Confirmed`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } returns
            TonStatusResult(
                transactions =
                    listOf(
                        TransactionJson(description = TonTransactionDescriptionJson(aborted = null))
                    )
            )

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.Confirmed, result)
    }

    @Test
    fun `api exception returns Pending`() = runTest {
        coEvery { tonApi.getTsStatus(any()) } throws RuntimeException("network error")

        val result = provider.checkStatus("hash", Chain.Ton)

        assertEquals(TransactionResult.Pending, result)
    }
}
