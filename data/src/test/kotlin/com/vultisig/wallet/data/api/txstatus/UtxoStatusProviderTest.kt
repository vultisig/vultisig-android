package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.models.BlockChainStatusDeserialized
import com.vultisig.wallet.data.api.models.BlockChairStatusResponse
import com.vultisig.wallet.data.api.models.ContextData
import com.vultisig.wallet.data.api.models.TransactionData
import com.vultisig.wallet.data.api.models.TransactionInfo
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class UtxoStatusProviderTest {

    private val blockChairApi = mockk<BlockChairApi>()
    private val provider = UtxoStatusProvider(blockChairApi)

    @Test
    fun `confirmed bitcoin cash tx returns Confirmed`() = runTest {
        coEvery { blockChairApi.getTsStatus(Chain.BitcoinCash, TX_HASH) } returns
            result(blockId = 951320, state = 951460)

        val result = provider.checkStatus(TX_HASH, Chain.BitcoinCash)

        assertEquals(TransactionResult.Confirmed, result)
    }

    @Test
    fun `mempool bitcoin cash tx returns Pending`() = runTest {
        coEvery { blockChairApi.getTsStatus(Chain.BitcoinCash, TX_HASH) } returns
            result(blockId = -1, state = 951460)

        val result = provider.checkStatus(TX_HASH, Chain.BitcoinCash)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `null api response returns Pending`() = runTest {
        coEvery { blockChairApi.getTsStatus(Chain.BitcoinCash, TX_HASH) } returns null

        val result = provider.checkStatus(TX_HASH, Chain.BitcoinCash)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `error response returns Pending`() = runTest {
        coEvery { blockChairApi.getTsStatus(Chain.BitcoinCash, TX_HASH) } returns
            BlockChainStatusDeserialized.Error("")

        val result = provider.checkStatus(TX_HASH, Chain.BitcoinCash)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `empty data map returns Pending`() = runTest {
        coEvery { blockChairApi.getTsStatus(Chain.BitcoinCash, TX_HASH) } returns
            BlockChainStatusDeserialized.Result(
                BlockChairStatusResponse(data = emptyMap(), context = ContextData(state = 951460))
            )

        val result = provider.checkStatus(TX_HASH, Chain.BitcoinCash)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `network error returns Pending`() = runTest {
        coEvery { blockChairApi.getTsStatus(Chain.BitcoinCash, TX_HASH) } throws
            RuntimeException("network error")

        val result = provider.checkStatus(TX_HASH, Chain.BitcoinCash)

        assertEquals(TransactionResult.Pending, result)
    }

    private fun result(blockId: Int, state: Int) =
        BlockChainStatusDeserialized.Result(
            BlockChairStatusResponse(
                data =
                    mapOf(
                        TX_HASH to TransactionData(transaction = TransactionInfo(blockId = blockId))
                    ),
                context = ContextData(state = state),
            )
        )

    private companion object {
        const val TX_HASH = "11f35b7131e85ac5197b0634e1bb4a8ae9f08a883ad7694f06f874da438f0a74"
    }
}
