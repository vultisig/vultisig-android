package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.models.EvmRpcResponseJson
import com.vultisig.wallet.data.api.models.EvmTxStatusJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class RecordPaidEvmNetworkFeeUseCaseTest {

    private val evmApi: EvmApi = mockk()
    private val evmApiFactory: EvmApiFactory = mockk {
        every { createEvmApi(Chain.Ethereum) } returns evmApi
    }
    private val historyRepository: TransactionHistoryRepository = mockk(relaxed = true)
    private val useCase = RecordPaidEvmNetworkFeeUseCaseImpl(evmApiFactory, historyRepository)

    /**
     * The swap receipt from the issue: 1,032,950 gas used of a 1,954,299 limit. The ceiling the row
     * was recorded with is the limit at the max fee, so the gas actually used must replace it.
     */
    @Test
    fun `stores gasUsed times effectiveGasPrice of the transaction's own receipt`() = runTest {
        receipt(gasUsed = 1_032_950, effectiveGasPrice = 875_487_000)

        useCase(Chain.Ethereum, TX_HASH)

        coVerify(exactly = 1) {
            historyRepository.recordPaidNetworkFee(
                "Ethereum",
                TX_HASH,
                BigInteger.valueOf(1_032_950L * 875_487_000L),
            )
        }
    }

    @Test
    fun `a receipt without effectiveGasPrice writes nothing`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns
            EvmRpcResponseJson(id = 1, result = EvmTxStatusJson(status = "0x1", gasUsed = "0x5208"))

        useCase(Chain.Ethereum, TX_HASH)

        coVerify(exactly = 0) { historyRepository.recordPaidNetworkFee(any(), any(), any()) }
    }

    @Test
    fun `no receipt yet writes nothing`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns null

        useCase(Chain.Ethereum, TX_HASH)

        coVerify(exactly = 0) { historyRepository.recordPaidNetworkFee(any(), any(), any()) }
    }

    @Test
    fun `a non-EVM chain never reads a receipt`() = runTest {
        useCase(Chain.Bitcoin, TX_HASH)

        coVerify(exactly = 0) { evmApiFactory.createEvmApi(any()) }
        coVerify(exactly = 0) { historyRepository.recordPaidNetworkFee(any(), any(), any()) }
    }

    private fun receipt(gasUsed: Long, effectiveGasPrice: Long) {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns
            EvmRpcResponseJson(
                id = 1,
                result =
                    EvmTxStatusJson(
                        status = "0x1",
                        gasUsed = "0x" + gasUsed.toString(16),
                        effectiveGasPrice = "0x" + effectiveGasPrice.toString(16),
                    ),
            )
    }

    private companion object {
        const val TX_HASH = "0x4de28cc72aa6021f67f9bba3a07fa59620de15ed474377e19102686f9f623bae"
    }
}
