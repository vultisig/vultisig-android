package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.MayaNodePool
import com.vultisig.wallet.data.api.models.MayaLatestBlockInfoResponse
import com.vultisig.wallet.data.api.models.MayaLiquidityProviderJson
import com.vultisig.wallet.data.utils.NetworkException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class GetMayaChainPendingLpDepositsUseCaseTest {

    private lateinit var api: MayaChainApi
    private lateinit var useCase: GetMayaChainPendingLpDepositsUseCaseImpl

    @BeforeEach
    fun setUp() {
        api = mockk()
        useCase = GetMayaChainPendingLpDepositsUseCaseImpl(api)

        coEvery { api.getMayaNodePools() } returns
            listOf(pool("BTC.BTC", pendingCacao = "3000000000"), pool("ETH.ETH"))
        coEvery { api.getMayaConstants() } returns emptyMap()
        coEvery { api.getLatestBlock() } returns latestBlock(HEIGHT)
        coEvery { api.getLiquidityProvider("BTC.BTC", CACAO_ADDRESS) } returns
            record(
                "BTC.BTC",
                pendingCacao = "3000000000",
                assetAddress = "bc1qself",
                lastAddHeight = HEIGHT - 800,
            )
    }

    @Test
    fun `only pools holding pending liquidity are read`() = runTest {
        val found = useCase(CACAO_ADDRESS)

        assertEquals(listOf("BTC.BTC"), found.map { it.pool })
        coVerify(exactly = 0) { api.getLiquidityProvider("ETH.ETH", any()) }
    }

    @Test
    fun `a pending CACAO half names the asset address it waits on`() = runTest {
        val deposit = useCase(CACAO_ADDRESS).single()

        assertTrue(deposit.isCacaoPending)
        assertEquals(BigInteger("3000000000"), deposit.pendingCacao)
        assertEquals("bc1qself", deposit.pairedAddress)
        assertEquals("bc1qself", deposit.assetAddress)
    }

    @Test
    fun `a pending asset half names the CACAO address it waits on`() = runTest {
        coEvery { api.getMayaNodePools() } returns listOf(pool("ETH.ETH", pendingAsset = "20000"))
        coEvery { api.getLiquidityProvider("ETH.ETH", CACAO_ADDRESS) } returns
            record(
                "ETH.ETH",
                pendingAsset = "20000",
                cacaoAddress = CACAO_ADDRESS,
                assetAddress = "0xself",
            )

        val deposit = useCase(CACAO_ADDRESS).single()

        assertEquals(false, deposit.isCacaoPending)
        assertEquals(CACAO_ADDRESS, deposit.pairedAddress)
        assertEquals("0xself", deposit.assetAddress)
    }

    @Test
    fun `the countdown runs to the pool's cleanup slot after the default age limit`() = runTest {
        // Aged out at 18,100,001; the only cleaned pool is slot 0, so the next multiple of
        // 100,800 — 18,144,000 — is when mayanode commits it.
        val deposit = useCase(CACAO_ADDRESS).single()

        assertEquals(144_000L, deposit.blocksUntilAddedAlone)
    }

    @Test
    fun `a mimir override replaces the default age limit`() = runTest {
        coEvery { api.getMayaConstants() } returns mapOf("PENDINGLIQUIDITYAGELIMIT" to 1_000L)

        assertEquals(1_000L, useCase(CACAO_ADDRESS).single().blocksUntilAddedAlone)
    }

    @Test
    fun `the cleanup slot follows the pool's place among pools holding pending liquidity`() =
        runTest {
            coEvery { api.getMayaConstants() } returns mapOf("PENDINGLIQUIDITYAGELIMIT" to 1_000L)
            coEvery { api.getMayaNodePools() } returns
                listOf(
                    pool("BTC.BTC", pendingCacao = "3000000000"),
                    pool("ARB.ETH", pendingAsset = "1"),
                )
            coEvery { api.getLiquidityProvider("ARB.ETH", CACAO_ADDRESS) } returns null

            // BTC.BTC sorts second of two: slot every 500 blocks, so 18,000,500.
            assertEquals(500L, useCase(CACAO_ADDRESS).single().blocksUntilAddedAlone)
        }

    @Test
    fun `a pool the cleanup pass skips leaves the countdown unknown`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(
                MayaNodePool(
                    asset = "BTC.BTC",
                    status = "Suspended",
                    pendingInboundCacao = "3000000000",
                )
            )

        assertNull(useCase(CACAO_ADDRESS).single().blocksUntilAddedAlone)
    }

    @Test
    fun `an unreadable height leaves the countdown unknown`() = runTest {
        coEvery { api.getLatestBlock() } throws RuntimeException("timeout")

        assertNull(useCase(CACAO_ADDRESS).single().blocksUntilAddedAlone)
    }

    @Test
    fun `a record with nothing pending is not reported`() = runTest {
        coEvery { api.getLiquidityProvider("BTC.BTC", CACAO_ADDRESS) } returns
            record("BTC.BTC", units = "123")

        assertTrue(useCase(CACAO_ADDRESS).isEmpty())
    }

    @Test
    fun `a failed pool scan throws`() = runTest {
        coEvery { api.getMayaNodePools() } throws NetworkException(503, "mayanode down")

        assertFailsWith<NetworkException> { useCase(CACAO_ADDRESS) }
    }

    @Test
    fun `a failed read on one candidate pool throws`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(
                pool("BTC.BTC", pendingCacao = "3000000000"),
                pool("ETH.ETH", pendingAsset = "20000"),
            )
        coEvery { api.getLiquidityProvider("ETH.ETH", CACAO_ADDRESS) } throws
            NetworkException(429, "rate limited")

        assertFailsWith<NetworkException> { useCase(CACAO_ADDRESS) }
    }

    @Test
    fun `a pool with no record for the address is skipped`() = runTest {
        coEvery { api.getMayaNodePools() } returns
            listOf(
                pool("BTC.BTC", pendingCacao = "3000000000"),
                pool("ETH.ETH", pendingAsset = "20000"),
            )
        coEvery { api.getLiquidityProvider("ETH.ETH", CACAO_ADDRESS) } returns null

        assertEquals(listOf("BTC.BTC"), useCase(CACAO_ADDRESS).map { it.pool })
    }

    @Test
    fun `cancellation propagates`() = runTest {
        coEvery { api.getMayaNodePools() } throws CancellationException("cancelled")

        assertFailsWith<CancellationException> { useCase(CACAO_ADDRESS) }
    }

    private fun pool(asset: String, pendingCacao: String = "0", pendingAsset: String = "0") =
        MayaNodePool(
            asset = asset,
            status = "Available",
            pendingInboundCacao = pendingCacao,
            pendingInboundAsset = pendingAsset,
        )

    private fun record(
        pool: String,
        pendingCacao: String = "0",
        pendingAsset: String = "0",
        units: String = "0",
        cacaoAddress: String? = CACAO_ADDRESS,
        assetAddress: String? = null,
        lastAddHeight: Long? = null,
    ) =
        MayaLiquidityProviderJson(
            asset = pool,
            cacaoAddress = cacaoAddress,
            assetAddress = assetAddress,
            units = units,
            pendingCacao = pendingCacao,
            pendingAsset = pendingAsset,
            lastAddHeight = lastAddHeight,
        )

    private fun latestBlock(height: Long): MayaLatestBlockInfoResponse =
        mockk { every { block.header.height } returns height.toString() }

    private companion object {
        const val CACAO_ADDRESS = "maya1self"
        const val HEIGHT = 18_000_000L
    }
}
