package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.KaminoUserPositionJson
import com.vultisig.wallet.data.api.KaminoVaultMetricsJson
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.KaminoPositionCacheRepository
import com.vultisig.wallet.data.repositories.KaminoVaultSelectionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers what the DeFi portfolio reads for Kamino: a position valued in its underlying token, and
 * the failure paths, where a read that did not happen must never be reported as an empty position.
 */
internal class KaminoDeFiBalanceServiceTest {

    private lateinit var kaminoApi: KaminoApi
    private lateinit var selectionRepository: KaminoVaultSelectionRepository
    private lateinit var positionCache: KaminoPositionCacheRepository

    @BeforeEach
    fun setUp() {
        kaminoApi = mockk(relaxed = true)
        selectionRepository = mockk(relaxed = true)
        positionCache = mockk(relaxed = true)

        coEvery { positionCache.getPositions(VAULT_ID) } returns emptyMap()
        every { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
    }

    @Test
    fun `a position is reported in the vault's underlying token`() = runTest {
        coEvery { kaminoApi.getUserPositions(ADDRESS) } returns
            listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "1000"))
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.05")

        val balances = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances

        val balance = balances.single()
        assertEquals(Coins.Solana.USDC.id, balance.coin.id)
        // 1000 shares × 1.05, in USDC base units.
        assertEquals(BigInteger("1050000000"), balance.amount)
    }

    @Test
    fun `two vaults sharing a token are reported as one balance`() = runTest {
        // The pipeline resolves a chain's DeFi position by coin, so a second USDC entry would go
        // unread and the portfolio would be short by that vault.
        every { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address, RWA.address))
        coEvery { kaminoApi.getUserPositions(ADDRESS) } returns
            listOf(
                KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "100"),
                KaminoUserPositionJson(vaultAddress = RWA.address, totalShares = "50"),
            )
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val balances = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances

        assertEquals(1, balances.size)
        assertEquals(BigInteger("150000000"), balances.single().amount)
    }

    @Test
    fun `a vault the user has not enabled is never read`() = runTest {
        every { selectionRepository.getSelectedVaults(VAULT_ID) } returns flowOf(emptySet())

        assertTrue(service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).isEmpty())

        coVerify(exactly = 0) { kaminoApi.getUserPositions(any()) }
    }

    @Test
    fun `a wallet holding nothing reports nothing`() = runTest {
        coEvery { kaminoApi.getUserPositions(ADDRESS) } returns emptyList()

        assertTrue(service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).isEmpty())
        // An answered call saying "no position" is a real zero, so the snapshot records it and the
        // next cold start does not resurrect the old figure.
        coVerify {
            positionCache.savePositions(VAULT_ID, mapOf(STEAKHOUSE.address to BigInteger.ZERO))
        }
    }

    @Test
    fun `a failed read falls back to the last known position rather than zero`() = runTest {
        coEvery { kaminoApi.getUserPositions(ADDRESS) } throws RuntimeException("503")
        coEvery { positionCache.getPositions(VAULT_ID) } returns
            mapOf(STEAKHOUSE.address to BigInteger("500000000"))

        val balance = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

        assertEquals(BigInteger("500000000"), balance.amount)
    }

    @Test
    fun `a share price that could not be read keeps the position at its last known size`() =
        runTest {
            coEvery { kaminoApi.getUserPositions(ADDRESS) } returns
                listOf(
                    KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "1000")
                )
            coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } throws RuntimeException("503")
            coEvery { positionCache.getPositions(VAULT_ID) } returns
                mapOf(STEAKHOUSE.address to BigInteger("500000000"))

            val balance =
                service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

            assertEquals(BigInteger("500000000"), balance.amount)
        }

    @Test
    fun `the cached read answers from the snapshot without touching the network`() = runTest {
        coEvery { positionCache.getPositions(VAULT_ID) } returns
            mapOf(STEAKHOUSE.address to BigInteger("250000000"))

        val balance = service().getCacheDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

        assertEquals(BigInteger("250000000"), balance.amount)
        coVerify(exactly = 0) { kaminoApi.getUserPositions(any()) }
    }

    @Test
    fun `a vault switched off stops counting even while its snapshot survives`() = runTest {
        every { selectionRepository.getSelectedVaults(VAULT_ID) } returns flowOf(emptySet())
        coEvery { positionCache.getPositions(VAULT_ID) } returns
            mapOf(STEAKHOUSE.address to BigInteger("250000000"))

        assertTrue(service().getCacheDeFiBalance(ADDRESS, VAULT_ID).isEmpty())
    }

    private fun service() =
        KaminoDeFiBalanceService(
            kaminoApi = kaminoApi,
            selectionRepository = selectionRepository,
            positionCache = positionCache,
        )

    private companion object {
        const val VAULT_ID = "vault-id"
        const val ADDRESS = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"

        val STEAKHOUSE = KaminoVaultRegistry.STEAKHOUSE_USDC
        val RWA = KaminoVaultRegistry.RWA_USDC
    }
}
