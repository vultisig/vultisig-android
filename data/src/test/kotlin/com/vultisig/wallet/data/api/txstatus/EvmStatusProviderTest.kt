package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.models.EvmRpcResponseJson
import com.vultisig.wallet.data.api.models.EvmTxStatusJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * A reverted EVM receipt has to arrive at history carrying *why* it reverted, not just that it did
 * (#5802) — the reason is what later lets a min-output failure be explained as a price move rather
 * than reported as a bare error.
 */
class EvmStatusProviderTest {

    private val evmApi: EvmApi = mockk()
    private val factory: EvmApiFactory = mockk { every { createEvmApi(any()) } returns evmApi }
    private val provider = EvmStatusProvider(factory)

    private fun stubReceipt(status: String?) {
        coEvery { evmApi.getTxStatus(any()) } returns
            EvmRpcResponseJson(
                id = 1,
                result = status?.let { EvmTxStatusJson(status = it) },
                error = null,
            )
    }

    @Test
    fun `a successful receipt is Confirmed and asks for no reason`() = runTest {
        stubReceipt("0x1")

        assertEquals(TransactionResult.Confirmed, provider.checkStatus("0xh", Chain.Ethereum))
        coVerify(exactly = 0) { evmApi.getRevertReason(any()) }
    }

    @Test
    fun `a reverted receipt carries the reason read back from the chain`() = runTest {
        stubReceipt("0x0")
        coEvery { evmApi.getRevertReason("0xh") } returns "Insufficient output"

        assertEquals(
            TransactionResult.Failed("Insufficient output"),
            provider.checkStatus("0xh", Chain.Ethereum),
        )
    }

    @Test
    fun `a reverted receipt the node will not explain keeps the generic reason`() = runTest {
        stubReceipt("0x0")
        coEvery { evmApi.getRevertReason("0xh") } returns null

        assertEquals(
            TransactionResult.Failed("Transaction reverted"),
            provider.checkStatus("0xh", Chain.Ethereum),
        )
    }

    @Test
    fun `a receipt the node has not seen yet stays Pending`() = runTest {
        stubReceipt(null)

        assertEquals(TransactionResult.Pending, provider.checkStatus("0xh", Chain.Ethereum))
    }

    /**
     * A reason lookup that blows up must cost only the explanation. Letting it escape would turn a
     * settled failure back into a pending row, which the poller would then never stop retrying.
     */
    @Test
    fun `a failing reason lookup still reports the revert`() = runTest {
        stubReceipt("0x0")
        coEvery { evmApi.getRevertReason("0xh") } throws RuntimeException("boom")

        assertEquals(
            TransactionResult.Failed("Transaction reverted"),
            provider.checkStatus("0xh", Chain.Ethereum),
        )
    }
}
