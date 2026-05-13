package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.api.TaostatsExtrinsicData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BittensorStatusProviderTest {

    private val bittensorApi = mockk<BittensorApi>()
    private val provider = BittensorStatusProvider(bittensorApi)

    @Test
    fun `success true returns Confirmed`() = runTest {
        coEvery { bittensorApi.getTxStatus(any()) } returns TaostatsExtrinsicData(success = true)

        val result = provider.checkStatus("hash", Chain.Bittensor)

        assertEquals(TransactionResult.Confirmed, result)
    }

    @Test
    fun `success false returns Failed`() = runTest {
        coEvery { bittensorApi.getTxStatus(any()) } returns TaostatsExtrinsicData(success = false)

        val result = provider.checkStatus("hash", Chain.Bittensor)

        assertEquals(TransactionResult.Failed("Transaction failed on Bittensor"), result)
    }

    @Test
    fun `null result returns Pending so polling keeps running until tx is indexed`() = runTest {
        coEvery { bittensorApi.getTxStatus(any()) } returns null

        val result = provider.checkStatus("hash", Chain.Bittensor)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `api exception returns Pending`() = runTest {
        coEvery { bittensorApi.getTxStatus(any()) } throws RuntimeException("network error")

        val result = provider.checkStatus("hash", Chain.Bittensor)

        assertEquals(TransactionResult.Pending, result)
    }
}
