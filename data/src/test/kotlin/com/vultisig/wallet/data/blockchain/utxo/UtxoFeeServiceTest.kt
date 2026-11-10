package com.vultisig.wallet.data.blockchain.utxo

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.MempoolApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.NetworkException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class UtxoFeeServiceTest {

    private val blockChairApi: BlockChairApi = mockk()
    private val mempoolApi: MempoolApi = mockk()
    private val service = UtxoFeeService(blockChairApi, mempoolApi)

    private suspend fun bitcoinRate(blockchair: Long, median: Double?): BigInteger {
        coEvery { blockChairApi.getBlockChairStats(Chain.Bitcoin) } returns
            BigInteger.valueOf(blockchair)
        coEvery { mempoolApi.getNextBlockMedianFee() } returns median
        return service.getDefaultGasFee(Chain.Bitcoin)
    }

    @Test
    fun `Bitcoin follows the next-block rule for every spec example`() = runTest {
        assertEquals(BigInteger.valueOf(3), bitcoinRate(blockchair = 2, median = 2.4))
        assertEquals(BigInteger.valueOf(3), bitcoinRate(blockchair = 2, median = 0.13))
        assertEquals(BigInteger.valueOf(6), bitcoinRate(blockchair = 2, median = 4.17))
        assertEquals(BigInteger.valueOf(2), bitcoinRate(blockchair = 1, median = 0.5))
        assertEquals(BigInteger.valueOf(5), bitcoinRate(blockchair = 2, median = 3.9))
        assertEquals(BigInteger.valueOf(50), bitcoinRate(blockchair = 40, median = 10.0))
    }

    @Test
    fun `Bitcoin rounds the margin up rather than to nearest`() = runTest {
        // 1.6004 x 1.25 = 2.0005, which rounds to 2 but must ceil to 3.
        assertEquals(BigInteger.valueOf(3), bitcoinRate(blockchair = 1, median = 1.6004))
    }

    @Test
    fun `Bitcoin never goes below the 2 sat per vbyte floor`() = runTest {
        // ceil(max(0.5, 0) x 1.25) = 1, so only the floor lifts it to 2.
        assertEquals(BigInteger.valueOf(2), bitcoinRate(blockchair = 0, median = 0.5))
    }

    @Test
    fun `Bitcoin falls back to Blockchair x2_5 when the projection is unusable`() = runTest {
        assertEquals(BigInteger.valueOf(5), bitcoinRate(blockchair = 2, median = null))
        assertEquals(BigInteger.valueOf(5), bitcoinRate(blockchair = 2, median = 0.0))
        assertEquals(BigInteger.valueOf(5), bitcoinRate(blockchair = 2, median = Double.NaN))
        assertEquals(BigInteger.valueOf(5), bitcoinRate(blockchair = 2, median = 6000.0))
    }

    @Test
    fun `Bitcoin falls back to Blockchair x2_5 when the proxy route is missing`() = runTest {
        coEvery { blockChairApi.getBlockChairStats(Chain.Bitcoin) } returns BigInteger.valueOf(2)
        coEvery { mempoolApi.getNextBlockMedianFee() } throws NetworkException(404, "Not Found")

        assertEquals(BigInteger.valueOf(5), service.getDefaultGasFee(Chain.Bitcoin))
    }

    @Test
    fun `Litecoin keeps Blockchair x2_5 and never reads the mempool`() = runTest {
        coEvery { blockChairApi.getBlockChairStats(Chain.Litecoin) } returns BigInteger.valueOf(2)

        assertEquals(BigInteger.valueOf(5), service.getDefaultGasFee(Chain.Litecoin))
        coVerify(exactly = 0) { mempoolApi.getNextBlockMedianFee() }
    }

    @Test
    fun `Bitcoin propagates a Blockchair failure without reading the mempool`() = runTest {
        coEvery { blockChairApi.getBlockChairStats(Chain.Bitcoin) } throws
            NetworkException(500, "blockchair down")

        assertFailsWith<NetworkException> { service.getDefaultGasFee(Chain.Bitcoin) }
        coVerify(exactly = 0) { mempoolApi.getNextBlockMedianFee() }
    }
}
