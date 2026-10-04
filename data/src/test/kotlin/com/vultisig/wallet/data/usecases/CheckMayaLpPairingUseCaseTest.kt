package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.models.MayaLiquidityProviderJson
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class CheckMayaLpPairingUseCaseTest {

    private val api: MayaChainApi = mockk()
    private val useCase = CheckMayaLpPairingUseCaseImpl(api)

    @Test
    fun `no record pairs`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, CACAO) } returns null

        assertEquals(MayaLpPairing.Pairable, useCase(POOL, CACAO, ASSET))
    }

    @Test
    fun `a zero-unit record with nothing pending takes the addresses the add names`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, CACAO) } returns record()

        assertEquals(MayaLpPairing.Pairable, useCase(POOL, CACAO, ASSET))
    }

    @Test
    fun `a live CACAO-only position cannot take an asset address`() = runTest {
        // The shape mainnet refunded with "mismatch of address" (ZEC.ZEC, 2026-10-04).
        coEvery { api.getLiquidityProvider(POOL, CACAO) } returns
            record(units = "1005094031", assetAddress = null)

        assertEquals(MayaLpPairing.SingleSidedPosition, useCase(POOL, CACAO, ASSET))
    }

    @Test
    fun `a live paired position pairs again from the same asset address, any case`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, CACAO) } returns
            record(units = "27994532404", assetAddress = ASSET.lowercase())

        assertEquals(MayaLpPairing.Pairable, useCase(POOL, CACAO, ASSET))
    }

    @Test
    fun `a live position paired to another asset address refunds`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, CACAO) } returns
            record(units = "1", assetAddress = "t1other")

        assertEquals(MayaLpPairing.AddressMismatch, useCase(POOL, CACAO, ASSET))
    }

    @Test
    fun `a pending half pairs only with the addresses it fixed`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, CACAO) } returns
            record(pendingTxId = "TX", cacaoAddress = CACAO, assetAddress = ASSET)
        assertEquals(MayaLpPairing.Pairable, useCase(POOL, CACAO, ASSET))

        coEvery { api.getLiquidityProvider(POOL, CACAO) } returns
            record(pendingTxId = "TX", cacaoAddress = CACAO, assetAddress = "t1other")
        assertEquals(MayaLpPairing.AddressMismatch, useCase(POOL, CACAO, ASSET))
    }

    @Test
    fun `an unreadable record propagates rather than guessing`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, CACAO) } throws RuntimeException("timeout")

        assertFailsWith<RuntimeException> { useCase(POOL, CACAO, ASSET) }
    }

    private fun record(
        units: String = "0",
        pendingTxId: String? = null,
        cacaoAddress: String? = CACAO,
        assetAddress: String? = null,
    ) =
        MayaLiquidityProviderJson(
            asset = POOL,
            cacaoAddress = cacaoAddress,
            assetAddress = assetAddress,
            units = units,
            pendingTxId = pendingTxId,
        )

    private companion object {
        const val POOL = "ZEC.ZEC"
        const val CACAO = "maya12a9rpf9u2ulwuezxkh6uas4au7xnde8um6z3zm"
        const val ASSET = "t1M6wQpBni81cypEEMYmrj241TvtyGgLdCu"
    }
}
