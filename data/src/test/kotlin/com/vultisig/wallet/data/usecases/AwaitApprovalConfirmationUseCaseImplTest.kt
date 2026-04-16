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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

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
    fun `returns immediately when already confirmed`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns confirmedReceipt()

        assertDoesNotThrow { useCase(CHAIN, TX_HASH) }

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

        useCase(CHAIN, TX_HASH)

        coVerify(exactly = 3) { evmApi.getTxStatus(TX_HASH) }
    }

    @Test
    fun `throws when approval reverts`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returns revertedReceipt()

        val exception = assertThrows<IllegalStateException> { useCase(CHAIN, TX_HASH) }
        assertTrue(exception.message?.contains("approval step failed") == true)
    }

    @Test
    fun `throws on timeout when never confirmed`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } answers { null }

        val exception = assertThrows<IllegalStateException> { useCase(CHAIN, TX_HASH) }
        assertTrue(exception.message?.contains("taking longer than expected") == true)
    }

    @Test
    fun `throws immediately on revert after pending polls`() = runTest {
        coEvery { evmApi.getTxStatus(TX_HASH) } returnsMany listOf(null, revertedReceipt())

        val exception = assertThrows<IllegalStateException> { useCase(CHAIN, TX_HASH) }
        assertTrue(exception.message?.contains("approval step failed") == true)
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
