package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.models.EvmRpcResponseJson
import com.vultisig.wallet.data.api.models.EvmTxStatusJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.blockTimeMs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

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

    @Nested
    inner class Confirmed {

        @Test
        fun `returns Confirmed on first poll`() = runTest {
            coEvery { evmApi.getTxStatus(TX_HASH) } returns confirmedReceipt()

            val result = useCase(Chain.Ethereum, TX_HASH)

            assertEquals(ApprovalConfirmationResult.Confirmed, result)
            coVerify(exactly = 1) { evmApi.getTxStatus(TX_HASH) }
        }

        @Test
        fun `returns Confirmed after pending polls`() = runTest {
            coEvery { evmApi.getTxStatus(TX_HASH) } returnsMany
                listOf(null, null, confirmedReceipt())

            val result = useCase(Chain.Polygon, TX_HASH)

            assertEquals(ApprovalConfirmationResult.Confirmed, result)
            coVerify(exactly = 3) { evmApi.getTxStatus(TX_HASH) }
        }

        @Test
        fun `returns Confirmed on last attempt`() = runTest {
            val pending = List(4) { null }
            coEvery { evmApi.getTxStatus(TX_HASH) } returnsMany pending + confirmedReceipt()

            val result = useCase(Chain.Arbitrum, TX_HASH)

            assertEquals(ApprovalConfirmationResult.Confirmed, result)
            coVerify(exactly = 5) { evmApi.getTxStatus(TX_HASH) }
        }
    }

    @Nested
    inner class Failed {

        @Test
        fun `returns Failed on first poll`() = runTest {
            coEvery { evmApi.getTxStatus(TX_HASH) } returns failedReceipt()

            val result = useCase(Chain.Base, TX_HASH)

            assertEquals(ApprovalConfirmationResult.Failed, result)
            coVerify(exactly = 1) { evmApi.getTxStatus(TX_HASH) }
        }

        @Test
        fun `returns Failed after pending polls`() = runTest {
            coEvery { evmApi.getTxStatus(TX_HASH) } returnsMany listOf(null, failedReceipt())

            val result = useCase(Chain.Optimism, TX_HASH)

            assertEquals(ApprovalConfirmationResult.Failed, result)
            coVerify(exactly = 2) { evmApi.getTxStatus(TX_HASH) }
        }
    }

    @Nested
    inner class TimedOut {

        @Test
        fun `returns TimedOut after exactly MAX_BLOCKS attempts`() = runTest {
            coEvery { evmApi.getTxStatus(TX_HASH) } answers { null }

            val result = useCase(Chain.BscChain, TX_HASH)

            assertEquals(ApprovalConfirmationResult.TimedOut, result)
            coVerify(exactly = 5) { evmApi.getTxStatus(TX_HASH) }
        }
    }

    @Nested
    inner class ChainRouting {

        @ParameterizedTest
        @EnumSource(
            value = Chain::class,
            names =
                [
                    "Ethereum",
                    "Arbitrum",
                    "Avalanche",
                    "Base",
                    "CronosChain",
                    "BscChain",
                    "Blast",
                    "Optimism",
                    "Polygon",
                    "ZkSync",
                    "Mantle",
                    "Sei",
                    "Hyperliquid",
                ],
        )
        fun `creates api for the given chain`(chain: Chain) = runTest {
            coEvery { evmApi.getTxStatus(TX_HASH) } returns confirmedReceipt()

            useCase(chain, TX_HASH)

            verify { evmApiFactory.createEvmApi(chain) }
        }
    }

    @Nested
    inner class BlockTimeMs {

        @Test
        fun `Ethereum block time is 12s`() {
            assertEquals(12_000L, Chain.Ethereum.blockTimeMs)
        }

        @Test
        fun `BSC block time is 3s`() {
            assertEquals(3_000L, Chain.BscChain.blockTimeMs)
        }

        @Test
        fun `Cronos block time is 3s`() {
            assertEquals(3_000L, Chain.CronosChain.blockTimeMs)
        }

        @Test
        fun `Polygon block time is 2s`() {
            assertEquals(2_000L, Chain.Polygon.blockTimeMs)
        }

        @Test
        fun `Arbitrum block time is 1s`() {
            assertEquals(1_000L, Chain.Arbitrum.blockTimeMs)
        }

        @ParameterizedTest
        @EnumSource(
            value = Chain::class,
            names =
                [
                    "Ethereum",
                    "Arbitrum",
                    "Avalanche",
                    "Base",
                    "CronosChain",
                    "BscChain",
                    "Blast",
                    "Optimism",
                    "Polygon",
                    "ZkSync",
                    "Mantle",
                    "Sei",
                    "Hyperliquid",
                ],
        )
        fun `all EVM chains have a positive block time`(chain: Chain) {
            assertTrue(chain.blockTimeMs > 0)
        }
    }

    private fun confirmedReceipt() =
        EvmRpcResponseJson(id = 1, result = EvmTxStatusJson(status = "0x1"), error = null)

    private fun failedReceipt() =
        EvmRpcResponseJson(id = 1, result = EvmTxStatusJson(status = "0x0"), error = null)

    private companion object {
        const val TX_HASH = "0xabc123def456"
    }
}
