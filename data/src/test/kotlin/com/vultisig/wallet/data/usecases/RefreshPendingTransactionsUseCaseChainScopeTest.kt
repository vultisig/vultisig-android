package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.txstatus.SwapKitTrackingService
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A chain-scoped sweep polls the rows that chain's history lists, so an in-flight swap paying out
 * on that chain settles there instead of showing "In progress" until the source chain is opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshPendingTransactionsUseCaseChainScopeTest {

    private val historyRepository: TransactionHistoryRepository = mockk(relaxed = true)
    private val statusRepository: TransactionStatusRepository = mockk()
    private val trackingService: SwapKitTrackingService = mockk()
    private val recordPaidFee: RecordPaidNetworkFeeUseCase = mockk(relaxed = true)

    private val ethToBtc =
        entity(
            hash = "eth-btc",
            chain = Chain.Ethereum,
            type = TransactionType.SWAP,
            payload = swapPayload(fromChain = Chain.Ethereum, toChain = Chain.Bitcoin),
        )
    private val solSend =
        entity(
            hash = "sol-send",
            chain = Chain.Solana,
            type = TransactionType.SEND,
            payload = sendPayload(),
        )

    private fun useCase(): RefreshPendingTransactionsUseCase =
        RefreshPendingTransactionsUseCaseImpl(
            transactionHistoryRepository = historyRepository,
            transactionStatusRepository = statusRepository,
            swapKitTrackingService = trackingService,
            recordPaidNetworkFee = recordPaidFee,
            dispatcher = UnconfinedTestDispatcher(),
            clock = Clock.System,
        )

    @BeforeEach
    fun setUp() {
        coEvery { historyRepository.getPendingTransactions(VAULT) } returns
            listOf(ethToBtc, solSend)
        coEvery { statusRepository.checkTransactionStatus(any(), any()) } returns
            TransactionResult.Pending
    }

    @Test
    fun `destination chain sweep polls the swap on its source chain`() = runTest {
        useCase().invoke(VAULT, Chain.Bitcoin.raw)

        coVerify(exactly = 1) { statusRepository.checkTransactionStatus("eth-btc", Chain.Ethereum) }
        coVerify(exactly = 0) { statusRepository.checkTransactionStatus("sol-send", any()) }
    }

    @Test
    fun `source chain sweep still polls the swap`() = runTest {
        useCase().invoke(VAULT, Chain.Ethereum.raw)

        coVerify(exactly = 1) { statusRepository.checkTransactionStatus("eth-btc", Chain.Ethereum) }
        coVerify(exactly = 0) { statusRepository.checkTransactionStatus("sol-send", any()) }
    }

    @Test
    fun `an unrelated chain sweep leaves the swap alone`() = runTest {
        useCase().invoke(VAULT, Chain.Solana.raw)

        coVerify(exactly = 0) { statusRepository.checkTransactionStatus("eth-btc", any()) }
        coVerify(exactly = 1) { statusRepository.checkTransactionStatus("sol-send", Chain.Solana) }
    }

    private fun swapPayload(fromChain: Chain, toChain: Chain) =
        SwapTransactionHistoryData(
            fromToken = "ETH",
            fromAmount = "1",
            fromChain = fromChain.raw,
            fromTokenLogo = "",
            toToken = "BTC",
            toAmount = "0.05",
            toChain = toChain.raw,
            toTokenLogo = "",
            provider = "THORChain",
            fiatValue = "0",
        )

    private fun sendPayload() =
        SendTransactionHistoryData(
            fromAddress = "",
            toAddress = "",
            amount = "1",
            token = "SOL",
            tokenLogo = "",
            feeEstimate = "",
            memo = "",
            fiatValue = "",
        )

    private fun entity(
        hash: String,
        chain: Chain,
        type: TransactionType,
        payload: TransactionHistoryData,
    ) =
        TransactionHistoryEntity(
            id = "${chain.raw}:$hash",
            vaultId = VAULT,
            type = type,
            status = TransactionStatus.BROADCASTED,
            chain = chain.raw,
            timestamp = 0L,
            txHash = hash,
            explorerUrl = "",
            payload = payload,
            confirmedAt = null,
            failureReason = null,
            lastCheckedAt = null,
        )

    private companion object {
        const val VAULT = "vault-id"
    }
}
