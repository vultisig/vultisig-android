package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PolkadotStatusProviderTest {

    private val polkadotApi = mockk<PolkadotApi>()
    private val provider = PolkadotStatusProvider(polkadotApi)

    @Test
    fun `extrinsic found in a recent block maps to Confirmed`() = runTest {
        coEvery { polkadotApi.isExtrinsicInChain(TX_HASH, any()) } returns true

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.Confirmed, result)
    }

    @Test
    fun `extrinsic not yet in a block keeps polling as Pending`() = runTest {
        coEvery { polkadotApi.isExtrinsicInChain(TX_HASH, any()) } returns false

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `transient RPC errors keep polling alive as Pending`() = runTest {
        coEvery { polkadotApi.isExtrinsicInChain(TX_HASH, any()) } throws
            RuntimeException("Connection timed out")

        val result = provider.checkStatus(TX_HASH, Chain.Polkadot)

        assertEquals(TransactionResult.Pending, result)
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        coEvery { polkadotApi.isExtrinsicInChain(TX_HASH, any()) } throws
            CancellationException("cancelled")

        assertThrows<CancellationException> { provider.checkStatus(TX_HASH, Chain.Polkadot) }
    }

    private companion object {
        const val TX_HASH = "0xdeadbeef"
    }
}
