package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.txstatus.SwapKitTrackingService
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Pins `refreshOne` — the single-transaction re-check behind the history detail sheet.
 *
 * It exists because the sweep is paced for unattended polling: opening one row is an explicit
 * question about one transaction, and answering it from a row the app has not looked at since the
 * broadcast is the whole defect. So this path skips the backoff, and skips nothing else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshPendingTransactionsUseCaseRefreshOneTest {

    private val historyRepository: TransactionHistoryRepository = mockk(relaxed = true)
    private val statusRepository: TransactionStatusRepository = mockk()
    private val trackingService: SwapKitTrackingService = mockk()

    private fun useCase(): RefreshPendingTransactionsUseCase =
        RefreshPendingTransactionsUseCaseImpl(
            transactionHistoryRepository = historyRepository,
            transactionStatusRepository = statusRepository,
            swapKitTrackingService = trackingService,
            dispatcher = UnconfinedTestDispatcher(),
        )

    @Test
    fun `refreshOne checks the chain and persists the result`() = runTest {
        coEvery { historyRepository.getTransaction(CHAIN, TX_HASH) } returns entity()
        coEvery { statusRepository.checkTransactionStatus(TX_HASH, Chain.Ethereum) } returns
            TransactionResult.Confirmed
        coEvery { historyRepository.updateTransactionStatus(any(), any(), any()) } just Runs

        useCase().refreshOne(CHAIN, TX_HASH)

        coVerify(exactly = 1) { statusRepository.checkTransactionStatus(TX_HASH, Chain.Ethereum) }
        coVerify(exactly = 1) {
            historyRepository.updateTransactionStatus(CHAIN, TX_HASH, TransactionResult.Confirmed)
        }
    }

    @Test
    fun `refreshOne leaves a settled row alone`() = runTest {
        coEvery { historyRepository.getTransaction(CHAIN, TX_HASH) } returns
            entity(status = TransactionStatus.CONFIRMED)

        useCase().refreshOne(CHAIN, TX_HASH)

        coVerify(exactly = 0) { statusRepository.checkTransactionStatus(any(), any()) }
        coVerify(exactly = 0) { historyRepository.updateTransactionStatus(any(), any(), any()) }
    }

    @Test
    fun `refreshOne is a no-op when the row is gone`() = runTest {
        coEvery { historyRepository.getTransaction(CHAIN, TX_HASH) } returns null

        useCase().refreshOne(CHAIN, TX_HASH)

        coVerify(exactly = 0) { statusRepository.checkTransactionStatus(any(), any()) }
    }

    /**
     * The contrast that gives `refreshOne` its reason to exist: the same row, backed off far enough
     * that the sweep skips it, is still checked when the user opens it.
     */
    @Test
    fun `refreshOne ignores the backoff that makes the sweep skip the same row`() = runTest {
        val backedOff = entity(retryCount = 5, lastCheckedAt = System.currentTimeMillis())
        coEvery { historyRepository.getPendingTransactions(VAULT) } returns listOf(backedOff)
        coEvery { historyRepository.getTransaction(CHAIN, TX_HASH) } returns backedOff
        coEvery { statusRepository.checkTransactionStatus(TX_HASH, Chain.Ethereum) } returns
            TransactionResult.Confirmed
        coEvery { historyRepository.updateTransactionStatus(any(), any(), any()) } just Runs

        useCase().invoke(VAULT)
        coVerify(exactly = 0) { statusRepository.checkTransactionStatus(any(), any()) }

        useCase().refreshOne(CHAIN, TX_HASH)
        coVerify(exactly = 1) { statusRepository.checkTransactionStatus(TX_HASH, Chain.Ethereum) }
    }

    private fun entity(
        status: TransactionStatus = TransactionStatus.BROADCASTED,
        retryCount: Int = 0,
        lastCheckedAt: Long? = null,
    ) =
        TransactionHistoryEntity(
            id = "$CHAIN:$TX_HASH",
            vaultId = VAULT,
            type = TransactionType.SEND,
            status = status,
            chain = CHAIN,
            timestamp = 0L,
            txHash = TX_HASH,
            explorerUrl = "",
            payload =
                SendTransactionHistoryData(
                    fromAddress = "",
                    toAddress = "",
                    amount = "",
                    token = "",
                    tokenLogo = "",
                    feeEstimate = "",
                    memo = "",
                    fiatValue = "",
                ),
            confirmedAt = null,
            failureReason = null,
            lastCheckedAt = lastCheckedAt,
            retryCount = retryCount,
        )

    private companion object {
        const val VAULT = "vault-id"
        const val CHAIN = "Ethereum"
        const val TX_HASH = "0xhash"
    }
}
