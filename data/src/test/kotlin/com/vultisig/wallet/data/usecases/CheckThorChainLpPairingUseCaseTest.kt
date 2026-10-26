package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainLiquidityProviderJson
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class CheckThorChainLpPairingUseCaseTest {

    private val api: ThorChainApi = mockk()
    private val useCase = CheckThorChainLpPairingUseCaseImpl(api)

    @Test
    fun `no record pairs`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE) } returns null

        assertEquals(ThorChainLpPairing.Pairable, useCase(POOL, RUNE, ASSET))
    }

    @Test
    fun `a zero-unit record with nothing pending takes the address the add names`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE) } returns record(assetAddress = OTHER)

        assertEquals(ThorChainLpPairing.Pairable, useCase(POOL, RUNE, ASSET))
    }

    @Test
    fun `a pending half pairs with the asset address it recorded, any case`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE) } returns
            record(pendingAsset = "20000", pendingTxId = "TX", assetAddress = ASSET.uppercase())

        assertEquals(ThorChainLpPairing.Pairable, useCase(POOL, RUNE, ASSET))
    }

    @Test
    fun `a pending half from another asset address refuses the add`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE) } returns
            record(pendingAsset = "20000", pendingTxId = "TX", assetAddress = OTHER)
        assertEquals(ThorChainLpPairing.AddressMismatch, useCase(POOL, RUNE, ASSET))

        coEvery { api.getLiquidityProvider(POOL, RUNE) } returns
            record(pendingRune = "300000000", assetAddress = OTHER)
        assertEquals(ThorChainLpPairing.AddressMismatch, useCase(POOL, RUNE, ASSET))
    }

    @Test
    fun `a pending half with no recorded asset address never matches a named one`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE) } returns
            record(pendingTxId = "TX", assetAddress = null)

        assertEquals(ThorChainLpPairing.AddressMismatch, useCase(POOL, RUNE, ASSET))
    }

    @Test
    fun `a pending half is not compared when the add names no asset address`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE) } returns
            record(pendingTxId = "TX", assetAddress = OTHER)

        assertEquals(ThorChainLpPairing.Pairable, useCase(POOL, RUNE, null))
    }

    @Test
    fun `a live RUNE-only position takes only a single-sided add`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE) } returns
            record(units = "1005094031", assetAddress = "")

        assertEquals(ThorChainLpPairing.SingleSidedPosition, useCase(POOL, RUNE, ASSET))
        assertEquals(ThorChainLpPairing.SingleSidedPosition, useCase(POOL, RUNE, null))
    }

    @Test
    fun `a live position paired with the vault's asset address pairs again, any case`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE) } returns
            record(units = "27994532404", assetAddress = ASSET.uppercase())

        assertEquals(ThorChainLpPairing.Pairable, useCase(POOL, RUNE, ASSET))
    }

    @Test
    fun `a live position paired with another asset address takes only a single-sided add`() =
        runTest {
            coEvery { api.getLiquidityProvider(POOL, RUNE) } returns
                record(units = "1", assetAddress = OTHER)

            assertEquals(ThorChainLpPairing.PairedWithOtherAddress, useCase(POOL, RUNE, ASSET))
            assertEquals(ThorChainLpPairing.PairedWithOtherAddress, useCase(POOL, RUNE, null))
        }

    @Test
    fun `an unreadable record propagates rather than guessing`() = runTest {
        coEvery { api.getLiquidityProvider(POOL, RUNE) } throws RuntimeException("timeout")

        assertFailsWith<RuntimeException> { useCase(POOL, RUNE, ASSET) }
    }

    private fun record(
        units: String = "0",
        pendingTxId: String? = null,
        pendingRune: String = "0",
        pendingAsset: String = "0",
        assetAddress: String? = null,
    ) =
        ThorChainLiquidityProviderJson(
            asset = POOL,
            runeAddress = RUNE,
            assetAddress = assetAddress,
            units = units,
            pendingRune = pendingRune,
            pendingAsset = pendingAsset,
            pendingTxId = pendingTxId,
        )

    private companion object {
        const val POOL = "ETH.USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7"
        const val RUNE = "thor1runeaddress"
        const val ASSET = "0xabcdef0123456789abcdef0123456789abcdef01"
        const val OTHER = "0x9999999999999999999999999999999999999999"
    }
}
