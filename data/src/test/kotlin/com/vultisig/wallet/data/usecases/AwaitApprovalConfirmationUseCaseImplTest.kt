package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.models.EvmRpcResponseJson
import com.vultisig.wallet.data.api.models.EvmTxStatusJson
import com.vultisig.wallet.data.models.Chain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AwaitApprovalConfirmationUseCaseImplTest {

    private lateinit var evmApi: EvmApi
    private lateinit var evmApiFactory: EvmApiFactory
    private lateinit var useCase: AwaitApprovalConfirmationUseCaseImpl

    @BeforeEach
    fun setUp() {
        evmApi = mockk()
        evmApiFactory = mockk { every { createEvmApi(any()) } returns evmApi }
        useCase = AwaitApprovalConfirmationUseCaseImpl(evmApiFactory)
    }

    @Test
    fun `returns Confirmed when receipt is success`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns confirmedReceipt()

        val result = useCase(CHAIN, TX_HASH)

        assertEquals(ApprovalConfirmationResult.Confirmed, result)
        coVerify(exactly = 1) { evmApi.getTxStatus(TX_HASH) }
    }

    @Test
    fun `creates api for the correct chain`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns confirmedReceipt()

        useCase(CHAIN, TX_HASH)

        verify { evmApiFactory.createEvmApi(CHAIN) }
    }

    @Test
    fun `polls until confirmation after pending results`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returnsMany listOf(null, null, confirmedReceipt())

        val result = useCase(CHAIN, TX_HASH)

        assertEquals(ApprovalConfirmationResult.Confirmed, result)
        coVerify(exactly = 3) { evmApi.getTxStatus(TX_HASH) }
    }

    @Test
    fun `returns Reverted when receipt status is failure`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns revertedReceipt()

        val result = useCase(CHAIN, TX_HASH)

        assertEquals(ApprovalConfirmationResult.Reverted, result)
    }

    @Test
    fun `returns TimedOut when never confirmed`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } answers { null }

        val result = useCase(CHAIN, TX_HASH)

        assertEquals(ApprovalConfirmationResult.TimedOut, result)
    }

    @Test
    fun `returns Reverted immediately even after pending polls`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returnsMany listOf(null, revertedReceipt())

        val result = useCase(CHAIN, TX_HASH)

        assertEquals(ApprovalConfirmationResult.Reverted, result)
        coVerify(exactly = 2) { evmApi.getTxStatus(TX_HASH) }
    }

    private fun confirmedReceipt() =
        EvmRpcResponseJson(id = 1, result = EvmTxStatusJson(status = "0x1"), error = null)

    private fun revertedReceipt() =
        EvmRpcResponseJson(id = 1, result = EvmTxStatusJson(status = "0x0"), error = null)

    private companion object {
        val CHAIN = Chain.Ethereum
        const val TX_HASH = "0xabc123def456"
    }
}
