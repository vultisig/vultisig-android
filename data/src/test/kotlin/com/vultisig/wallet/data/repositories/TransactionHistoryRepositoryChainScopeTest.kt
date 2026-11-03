package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.TransactionHistoryDao
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.db.models.TransactionType
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.TransactionHistoryData
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TransactionHistoryRepositoryChainScopeTest {

    private val ethToBtc = swap(hash = "eth-btc", fromChain = "Ethereum", toChain = "Bitcoin")
    private val btcToEth = swap(hash = "btc-eth", fromChain = "Bitcoin", toChain = "Ethereum")
    private val ethToUsdc = swap(hash = "eth-usdc", fromChain = "Ethereum", toChain = "Ethereum")
    private val btcSend = send(hash = "btc-send", chain = "Bitcoin")

    private val swaps = listOf(ethToBtc, btcToEth, ethToUsdc)
    private val all = swaps + btcSend

    private lateinit var dao: TransactionHistoryDao
    private lateinit var repository: TransactionHistoryRepositoryImpl

    @BeforeEach
    fun setUp() {
        dao = mockk()
        every { dao.observeAllByVault(VAULT) } returns flowOf(all)
        every { dao.observeSwapByVault(VAULT) } returns flowOf(swaps)
        repository = TransactionHistoryRepositoryImpl(dao)
    }

    @Test
    fun `a swap shows on its destination chain and still on its source chain`() = runTest {
        assertEquals(
            listOf(ethToBtc, btcToEth),
            observe(TransactionHistoryType.SWAPS, "Bitcoin"),
        )
        assertEquals(
            listOf(ethToBtc, btcToEth, ethToUsdc),
            observe(TransactionHistoryType.SWAPS, "Ethereum"),
        )
    }

    @Test
    fun `overview of a chain carries its sends and the swaps on either leg`() = runTest {
        assertEquals(
            listOf(ethToBtc, btcToEth, btcSend),
            observe(TransactionHistoryType.OVERVIEW, "Bitcoin"),
        )
        assertEquals(
            listOf(ethToBtc, btcToEth, ethToUsdc),
            observe(TransactionHistoryType.OVERVIEW, "Ethereum"),
        )
    }

    @Test
    fun `history with no chain lists every row once`() = runTest {
        assertEquals(all, observe(TransactionHistoryType.OVERVIEW, null))
        assertEquals(all, observe(TransactionHistoryType.OVERVIEW, ""))
        assertEquals(swaps, observe(TransactionHistoryType.SWAPS, null))
    }

    @Test
    fun `a chain that is neither leg gets none of the swaps`() = runTest {
        assertEquals(emptyList(), observe(TransactionHistoryType.SWAPS, "Solana"))
    }

    @Test
    fun `send tab stays scoped by the chain column`() = runTest {
        every { dao.observeSendByVault(VAULT, "Bitcoin") } returns flowOf(listOf(btcSend))

        assertEquals(listOf(btcSend), observe(TransactionHistoryType.SEND, "Bitcoin"))
    }

    private suspend fun observe(
        type: TransactionHistoryType,
        chain: String?,
    ): List<TransactionHistoryEntity> = repository.observeTransactions(VAULT, type, chain).first()

    private fun swap(hash: String, fromChain: String, toChain: String) =
        entity(
            hash = hash,
            chain = fromChain,
            type = TransactionType.SWAP,
            payload =
                SwapTransactionHistoryData(
                    fromToken = "FROM",
                    fromAmount = "1",
                    fromChain = fromChain,
                    fromTokenLogo = "",
                    toToken = "TO",
                    toAmount = "1",
                    toChain = toChain,
                    toTokenLogo = "",
                    provider = "THORChain",
                    fiatValue = "0",
                ),
        )

    private fun send(hash: String, chain: String) =
        entity(
            hash = hash,
            chain = chain,
            type = TransactionType.SEND,
            payload =
                SendTransactionHistoryData(
                    fromAddress = "",
                    toAddress = "",
                    amount = "0",
                    token = "BTC",
                    tokenLogo = "",
                    feeEstimate = "0",
                    memo = "",
                    fiatValue = "0",
                ),
        )

    private fun entity(
        hash: String,
        chain: String,
        type: TransactionType,
        payload: TransactionHistoryData,
    ) =
        TransactionHistoryEntity(
            id = "$chain:$hash",
            vaultId = VAULT,
            type = type,
            status = TransactionStatus.CONFIRMED,
            chain = chain,
            timestamp = 0L,
            txHash = hash,
            explorerUrl = "",
            payload = payload,
            confirmedAt = null,
            failureReason = null,
            lastCheckedAt = null,
        )

    private companion object {
        const val VAULT = "vault"
    }
}
