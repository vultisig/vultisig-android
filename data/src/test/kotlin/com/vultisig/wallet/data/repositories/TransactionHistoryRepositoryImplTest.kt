package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.TransactionHistoryDao
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
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
        repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.Confirmed)

        coVerify(exactly = 1) {
            dao.updateToConfirmed(id = EXPECTED_ID, confirmedAt = any(), lastCheckedAt = any())
        }
    }

    @Test
    fun `updateTransactionStatus Failed routes to dao with reason and chain-scoped id`() = runTest {
        repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.Failed("nope"))

        coVerify(exactly = 1) {
            dao.updateToFailed(id = EXPECTED_ID, failureReason = "nope", lastCheckedAt = any())
        }
    }

    @Test
    fun `updateTransactionStatus Refunded routes to dao with reason and chain-scoped id`() =
        runTest {
            repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.Refunded("pool"))

            coVerify(exactly = 1) {
                dao.updateToRefunded(id = EXPECTED_ID, reason = "pool", lastCheckedAt = any())
            }
        }

    @Test
    fun `updateTransactionStatus Pending routes to updateStatus with PENDING`() = runTest {
        repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.Pending)

        coVerify(exactly = 1) {
            dao.updateStatus(
                id = EXPECTED_ID,
                status = TransactionStatus.PENDING,
                lastCheckedAt = any(),
            )
        }
    }

    @Test
    fun `updateTransactionStatus NotFound routes to updateStatus with NotFound`() = runTest {
        repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.NotFound)

        coVerify(exactly = 1) {
            dao.updateStatus(
                id = EXPECTED_ID,
                status = TransactionStatus.NotFound,
                lastCheckedAt = any(),
            )
        }
    }

    @Test
    fun `updateTransactionStatus TimedOut routes to updateStatus with NotFound`() = runTest {
        repository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.TimedOut)

        coVerify(exactly = 1) {
            dao.updateStatus(
                id = EXPECTED_ID,
                status = TransactionStatus.NotFound,
                lastCheckedAt = any(),
            )
        }
    }

    @Test
    fun `getTransaction looks up by chain-scoped id`() = runTest {
        val expected = entity(CHAIN, TX_HASH)
        coEvery { dao.getById(EXPECTED_ID) } returns expected

        assertEquals(expected, repository.getTransaction(CHAIN, TX_HASH))
    }

    @Test
    fun `demoteForReorg routes to dao with chain-scoped id and reason`() = runTest {
        repository.demoteForReorg(CHAIN, TX_HASH, "reorg")

        coVerify(exactly = 1) {
            dao.demoteForReorg(id = EXPECTED_ID, reason = "reorg", lastCheckedAt = any())
        }
    }

    @Test
    fun `incrementRetryCount routes to dao with chain-scoped id`() = runTest {
        repository.incrementRetryCount(CHAIN, TX_HASH)

        coVerify(exactly = 1) { dao.incrementRetryCount(id = EXPECTED_ID, lastCheckedAt = any()) }
    }

    @Test
    fun `same txHash on different chains resolves to distinct dao ids`() = runTest {
        repository.incrementRetryCount("Bitcoin", TX_HASH)
        repository.incrementRetryCount("Litecoin", TX_HASH)

        coVerifyOrder {
            dao.incrementRetryCount(id = "Bitcoin:$TX_HASH", lastCheckedAt = any())
            dao.incrementRetryCount(id = "Litecoin:$TX_HASH", lastCheckedAt = any())
        }
    }

    @Test
    fun `upsertFromBackfill delegates the entity unchanged to dao`() = runTest {
        val entity = entity(CHAIN, TX_HASH)

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
