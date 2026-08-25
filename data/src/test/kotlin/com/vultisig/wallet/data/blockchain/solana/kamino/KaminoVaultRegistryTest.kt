package com.vultisig.wallet.data.blockchain.solana.kamino

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * The registry is what every Kamino transaction is checked against, so these guard the pinned
 * values themselves. Each address, mint and scale below was read from `api.kamino.finance`; a typo
 * in any of them would send a deposit somewhere else or size it by the wrong power of ten, and no
 * downstream check would catch it.
 */
class KaminoVaultRegistryTest {

    @Test
    fun `the allow-list is exactly the three launch vaults`() {
        assertEquals(3, KaminoVaultRegistry.ALLOW_LIST.size)
        assertEquals(
            listOf("Steakhouse USDC", "Allez SOL", "RWA USDC"),
            KaminoVaultRegistry.ALLOW_LIST.map { it.fallbackName },
        )
    }

    @Test
    fun `the private-credit vault is never the first one offered`() {
        // Its risk is materially different from plain lending, so it does not lead the list.
        val tiers = KaminoVaultRegistry.ALLOW_LIST.map { it.riskTier }
        assertEquals(KaminoRiskTier.CONSERVATIVE, tiers.first())
        assertEquals(
            KaminoRiskTier.PRIVATE_CREDIT,
            KaminoVaultRegistry.RWA_USDC.riskTier,
            "RWA USDC lends against tokenized private credit and must be tiered above conservative",
        )
        assertEquals(1, tiers.count { it == KaminoRiskTier.PRIVATE_CREDIT })
    }

    @Test
    fun `vault addresses and share mints are all distinct`() {
        val addresses = KaminoVaultRegistry.ALLOW_LIST.map { it.address }
        val sharesMints = KaminoVaultRegistry.ALLOW_LIST.map { it.sharesMint }
        val farms = KaminoVaultRegistry.ALLOW_LIST.map { it.farm }

        assertEquals(addresses.size, addresses.distinct().size, "duplicate vault address")
        assertEquals(sharesMints.size, sharesMints.distinct().size, "duplicate shares mint")
        assertEquals(farms.size, farms.distinct().size, "duplicate farm")
        assertEquals(sharesMints.toSet(), KaminoVaultRegistry.SHARES_MINTS)
    }

    @Test
    fun `both dollar vaults share the USDC mint and the SOL vault uses wrapped SOL`() {
        assertEquals(KaminoVaultRegistry.USDC_MINT, KaminoVaultRegistry.STEAKHOUSE_USDC.tokenMint)
        assertEquals(KaminoVaultRegistry.USDC_MINT, KaminoVaultRegistry.RWA_USDC.tokenMint)
        // Native SOL is not the underlying here, which is why a deposit has to wrap first.
        assertEquals(KaminoVaultRegistry.WRAPPED_SOL_MINT, KaminoVaultRegistry.ALLEZ_SOL.tokenMint)
        assertNotEquals(
            KaminoVaultRegistry.STEAKHOUSE_USDC.address,
            KaminoVaultRegistry.RWA_USDC.address,
            "two vaults on the same mint must still be distinguished by address",
        )
    }

    @Test
    fun `the SOL vault's share scale differs from its token scale`() {
        // Guards against anyone "tidying" these to match. They are genuinely 6 against 9.
        assertEquals(9, KaminoVaultRegistry.ALLEZ_SOL.tokenDecimals)
        assertEquals(6, KaminoVaultRegistry.ALLEZ_SOL.sharesDecimals)
        assertNotEquals(
            KaminoVaultRegistry.ALLEZ_SOL.tokenDecimals,
            KaminoVaultRegistry.ALLEZ_SOL.sharesDecimals,
        )
    }

    @Test
    fun `both dollar vaults use six decimals for token and shares alike`() {
        listOf(KaminoVaultRegistry.STEAKHOUSE_USDC, KaminoVaultRegistry.RWA_USDC).forEach { vault ->
            assertEquals(6, vault.tokenDecimals, "${vault.fallbackName} token decimals")
            assertEquals(6, vault.sharesDecimals, "${vault.fallbackName} shares decimals")
        }
    }

    @Test
    fun `every launch vault names a curator and a farm`() {
        // A deposit stakes into the farm, so a vault without one would strand the shares.
        KaminoVaultRegistry.ALLOW_LIST.forEach { vault ->
            assertTrue(
                vault.curator.displayName.isNotBlank(),
                "${vault.fallbackName} has no curator",
            )
            assertTrue(vault.farm.isNotBlank(), "${vault.fallbackName} has no farm")
        }
        assertEquals(
            KaminoCurator.STEAKHOUSE_FINANCIAL,
            KaminoVaultRegistry.STEAKHOUSE_USDC.curator,
        )
        assertEquals(KaminoCurator.ROCKAWAYX, KaminoVaultRegistry.RWA_USDC.curator)
        assertEquals(KaminoCurator.ALLEZ_LABS, KaminoVaultRegistry.ALLEZ_SOL.curator)
    }

    @Test
    fun `lookup resolves curated vaults and refuses everything else`() {
        KaminoVaultRegistry.ALLOW_LIST.forEach { vault ->
            assertEquals(vault, KaminoVaultRegistry.vaultFor(vault.address))
            assertTrue(KaminoVaultRegistry.isAllowed(vault.address))
        }

        // A vault Kamino also runs, but one the app does not offer.
        val uncuratedVault = "2Z6C84pCc2ri8t39jvRCXnTGFQqUJf1mMpUMtpeFfhyB"
        assertNull(KaminoVaultRegistry.vaultFor(uncuratedVault))
        assertFalse(KaminoVaultRegistry.isAllowed(uncuratedVault))
        assertFalse(KaminoVaultRegistry.isAllowed(""))
    }

    @Test
    fun `programs are pinned to the kVault and farms program ids`() {
        assertEquals("KvauGMspG5k6rtzrqqn7WNn3oZdyKqLKwK2XWQ8FLjd", KaminoVaultRegistry.PROGRAM_ID)
        assertEquals(
            "FarmsPZpWu9i7Kky8tPN37rs2TpmMrAZrC7S7vJa91Hr",
            KaminoVaultRegistry.FARMS_PROGRAM_ID,
        )
    }
}
