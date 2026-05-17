package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.TransactionHistoryDao
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TransactionHistoryRepositoryImplTest {

    private lateinit var dao: TransactionHistoryDao
    private lateinit var repository: TransactionHistoryRepositoryImpl

    @BeforeEach
    fun setUp() {
        dao = mockk(relaxUnitFun = true)
        repository = TransactionHistoryRepositoryImpl(dao)
    }

    @Test
    fun `updateTransactionStatus Confirmed routes to dao with chain-scoped id`() = runTest {
        val idSlot = slot<String>()
        coEvery { dao.updateToConfirmed(id = capture(idSlot), any(), any()) } just Runs

        repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.Confirmed)

        assertEquals(EXPECTED_ID, idSlot.captured)
        coVerify(exactly = 1) { dao.updateToConfirmed(id = EXPECTED_ID, any(), any()) }
    }

    @Test
    fun `updateTransactionStatus Failed routes to dao with reason and chain-scoped id`() = runTest {
        val idSlot = slot<String>()
        val reasonSlot = slot<String>()
        coEvery {
            dao.updateToFailed(id = capture(idSlot), failureReason = capture(reasonSlot), any())
        } just Runs

        repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.Failed("nope"))

        assertEquals(EXPECTED_ID, idSlot.captured)
        assertEquals("nope", reasonSlot.captured)
    }

    @Test
    fun `updateTransactionStatus Refunded routes to dao with reason and chain-scoped id`() =
        runTest {
            val idSlot = slot<String>()
            val reasonSlot = slot<String>()
            coEvery {
                dao.updateToRefunded(id = capture(idSlot), reason = capture(reasonSlot), any())
            } just Runs

            repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.Refunded("pool"))

            assertEquals(EXPECTED_ID, idSlot.captured)
            assertEquals("pool", reasonSlot.captured)
        }

    @Test
    fun `updateTransactionStatus Pending routes to updateStatus with PENDING`() = runTest {
        val idSlot = slot<String>()
        val statusSlot = slot<TransactionStatus>()
        coEvery { dao.updateStatus(id = capture(idSlot), status = capture(statusSlot), any()) } just
            Runs

        repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.Pending)

        assertEquals(EXPECTED_ID, idSlot.captured)
        assertEquals(TransactionStatus.PENDING, statusSlot.captured)
    }

    @Test
    fun `updateTransactionStatus NotFound routes to updateStatus with NotFound`() = runTest {
        val statusSlot = slot<TransactionStatus>()
        coEvery { dao.updateStatus(id = EXPECTED_ID, status = capture(statusSlot), any()) } just
            Runs

        repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.NotFound)

        assertEquals(TransactionStatus.NotFound, statusSlot.captured)
    }

    @Test
    fun `updateTransactionStatus TimedOut routes to updateStatus with NotFound`() = runTest {
        val statusSlot = slot<TransactionStatus>()
        coEvery { dao.updateStatus(id = EXPECTED_ID, status = capture(statusSlot), any()) } just
            Runs

        repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.TimedOut)

        assertEquals(TransactionStatus.NotFound, statusSlot.captured)
    }

    @Test
    fun `getTransaction looks up by chain-scoped id`() = runTest {
        val expected = entity(CHAIN, TX_HASH)
        coEvery { dao.getById(EXPECTED_ID) } returns expected

        val result = repository.getTransaction(CHAIN, TX_HASH)

        assertEquals(expected, result)
        coVerify(exactly = 1) { dao.getById(EXPECTED_ID) }
    }

    @Test
    fun `demoteForReorg routes to dao with chain-scoped id`() = runTest {
        val idSlot = slot<String>()
        val reasonSlot = slot<String>()
        coEvery {
            dao.demoteForReorg(id = capture(idSlot), reason = capture(reasonSlot), any())
        } just Runs

        repository.demoteForReorg(CHAIN, TX_HASH, "reorg")

        assertEquals(EXPECTED_ID, idSlot.captured)
        assertEquals("reorg", reasonSlot.captured)
    }

    @Test
    fun `incrementRetryCount routes to dao with chain-scoped id`() = runTest {
        val idSlot = slot<String>()
        coEvery { dao.incrementRetryCount(id = capture(idSlot), any()) } just Runs

        repository.incrementRetryCount(CHAIN, TX_HASH)

        assertEquals(EXPECTED_ID, idSlot.captured)
    }

    @Test
    fun `same txHash on different chains resolves to distinct dao ids`() = runTest {
        val idSlot = mutableListOf<String>()
        coEvery { dao.incrementRetryCount(id = capture(idSlot), any()) } just Runs

        repository.incrementRetryCount("Bitcoin", TX_HASH)
        repository.incrementRetryCount("Litecoin", TX_HASH)

        assertEquals(listOf("Bitcoin:$TX_HASH", "Litecoin:$TX_HASH"), idSlot.toList())
    }

    @Test
    fun `upsertFromBackfill delegates to dao with the same entity`() = runTest {
        val entity = entity(CHAIN, TX_HASH)
        coEvery { dao.upsertFromBackfill(entity) } just Runs

        repository.upsertFromBackfill(entity)

        coVerify(exactly = 1) { dao.upsertFromBackfill(entity) }
    }

    private fun entity(chain: String, txHash: String): TransactionHistoryEntity =
        TransactionHistoryEntity(
            id = "$chain:$txHash",
            vaultId = "vault",
            type = TransactionType.SEND,
            status = TransactionStatus.BROADCASTED,
            chain = chain,
            timestamp = 0L,
            txHash = txHash,
            explorerUrl = "",
            payload =
                SendTransactionHistoryData(
                    fromAddress = "",
                    toAddress = "",
                    amount = "0",
                    token = "",
                    tokenLogo = "",
                    feeEstimate = "0",
                    memo = "",
                    fiatValue = "0",
                ),
            confirmedAt = null,
            failureReason = null,
            lastCheckedAt = null,
        )

    private companion object {
        const val CHAIN = "Bitcoin"
        const val TX_HASH = "abc123"
        const val EXPECTED_ID = "$CHAIN:$TX_HASH"
    }
}
