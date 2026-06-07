package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.txstatus.SwapKitTrackingService
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.getSwapProviderId
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
 * Pins the SwapKit settlement gate in [RefreshPendingTransactionsUseCase] (the Android analogue of
 * iOS' TransactionStatusPollerSwapKitGateTests):
 * - a SwapKit-routed swap on a `/track`-capable chain is gated on `/track` (destination leg), not
 *   the source-chain status check — so a cross-chain swap isn't marked Success on source confirm.
 * - a non-SwapKit transaction keeps the existing source-chain status path.
 * - a SwapKit swap on a chain `/track` can't address falls back to the source-chain path rather
 *   than poll an unknown chainId.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshPendingTransactionsUseCaseSwapKitGateTest {

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
    fun `SwapKit swap on a trackable chain is gated on track`() = runTest {
        val tx = entity(chain = Chain.Ethereum, payload = swapPayload(SWAPKIT))
        coEvery { historyRepository.getPendingTransactions(VAULT) } returns listOf(tx)
        coEvery { trackingService.canTrack(Chain.Ethereum) } returns true
        coEvery { trackingService.checkSettlementStatus(tx.txHash, Chain.Ethereum) } returns
            TransactionResult.Pending
        coEvery { historyRepository.updateTransactionStatus(any(), any(), any()) } just Runs

        useCase().invoke(VAULT)

        coVerify(exactly = 1) { trackingService.checkSettlementStatus(tx.txHash, Chain.Ethereum) }
        coVerify(exactly = 0) { statusRepository.checkTransactionStatus(any(), any()) }
    }

    @Test
    fun `non-SwapKit transaction keeps the source-chain status path`() = runTest {
        val tx =
            entity(
                chain = Chain.Ethereum,
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
                type = TransactionType.SEND,
            )
        coEvery { historyRepository.getPendingTransactions(VAULT) } returns listOf(tx)
        coEvery { statusRepository.checkTransactionStatus(tx.txHash, Chain.Ethereum) } returns
            TransactionResult.Confirmed
        coEvery { historyRepository.updateTransactionStatus(any(), any(), any()) } just Runs

        useCase().invoke(VAULT)

        coVerify(exactly = 1) { statusRepository.checkTransactionStatus(tx.txHash, Chain.Ethereum) }
        coVerify(exactly = 0) { trackingService.checkSettlementStatus(any(), any()) }
    }

    @Test
    fun `non-SwapKit EVM-aggregator swap keeps the source-chain status path`() = runTest {
        val tx = entity(chain = Chain.Ethereum, payload = swapPayload("1inch"))
        coEvery { historyRepository.getPendingTransactions(VAULT) } returns listOf(tx)
        coEvery { statusRepository.checkTransactionStatus(tx.txHash, Chain.Ethereum) } returns
            TransactionResult.Confirmed
        coEvery { historyRepository.updateTransactionStatus(any(), any(), any()) } just Runs

        useCase().invoke(VAULT)

        coVerify(exactly = 1) { statusRepository.checkTransactionStatus(tx.txHash, Chain.Ethereum) }
        coVerify(exactly = 0) { trackingService.checkSettlementStatus(any(), any()) }
    }

    @Test
    fun `SwapKit swap on an untrackable chain falls back to the source-chain path`() = runTest {
        val tx = entity(chain = Chain.Polkadot, payload = swapPayload(SWAPKIT))
        coEvery { historyRepository.getPendingTransactions(VAULT) } returns listOf(tx)
        coEvery { trackingService.canTrack(Chain.Polkadot) } returns false
        coEvery { statusRepository.checkTransactionStatus(tx.txHash, Chain.Polkadot) } returns
            TransactionResult.Confirmed
        coEvery { historyRepository.updateTransactionStatus(any(), any(), any()) } just Runs

        useCase().invoke(VAULT)

        coVerify(exactly = 1) { statusRepository.checkTransactionStatus(tx.txHash, Chain.Polkadot) }
        coVerify(exactly = 0) { trackingService.checkSettlementStatus(any(), any()) }
    }

    private fun swapPayload(provider: String): SwapTransactionHistoryData =
        SwapTransactionHistoryData(
            fromToken = "ETH",
            fromAmount = "1",
            fromChain = "Ethereum",
            fromTokenLogo = "",
            toToken = "SOL",
            toAmount = "10",
            toChain = "Solana",
            toTokenLogo = "",
            provider = provider,
            fiatValue = "100",
            swapId = "swap-123",
        )

    private fun entity(
        chain: Chain,
        payload: TransactionHistoryData,
        type: TransactionType = TransactionType.SWAP,
    ): TransactionHistoryEntity =
        TransactionHistoryEntity(
            id = "${chain.raw}:0xhash",
            vaultId = VAULT,
            type = type,
            status = TransactionStatus.BROADCASTED,
            chain = chain.raw,
            timestamp = 0L,
            txHash = "0xhash",
            explorerUrl = "",
            payload = payload,
            confirmedAt = null,
            failureReason = null,
            lastCheckedAt = null,
            retryCount = 0,
            broadcastBlockNumber = null,
        )

    private companion object {
        const val VAULT = "vault-id"
        val SWAPKIT: String = SwapProvider.SWAPKIT.getSwapProviderId()
    }
}
