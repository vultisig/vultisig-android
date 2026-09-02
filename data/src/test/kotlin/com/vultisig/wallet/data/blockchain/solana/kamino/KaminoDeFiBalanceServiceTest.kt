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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
        assertEquals(2, balances.single().positionCount)
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
    fun `a share price of zero counts as unknown rather than as an empty position`() = runTest {
        coEvery { kaminoApi.getUserPositions(ADDRESS) } returns
            listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "1000"))
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "0")
        coEvery { positionCache.getPositions(VAULT_ID) } returns
            mapOf(STEAKHOUSE.address to BigInteger("500000000"))

        val balance = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

        assertEquals(BigInteger("500000000"), balance.amount)
        // The zero must not reach the snapshot either, or it would erase the real position until a
        // later good read.
        coVerify(exactly = 0) {
            positionCache.savePositions(VAULT_ID, mapOf(STEAKHOUSE.address to BigInteger.ZERO))
        }
    }

    @Test
    fun `a share count that will not parse keeps the position at its last known size`() = runTest {
        coEvery { kaminoApi.getUserPositions(ADDRESS) } returns
            listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "n/a"))
        coEvery { positionCache.getPositions(VAULT_ID) } returns
            mapOf(STEAKHOUSE.address to BigInteger("500000000"))

        val balance = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

        assertEquals(BigInteger("500000000"), balance.amount)
        // The last known size is what goes back into the snapshot, so a run of unreadable answers
        // cannot walk the position down to zero.
        coVerify {
            positionCache.savePositions(
                VAULT_ID,
                mapOf(STEAKHOUSE.address to BigInteger("500000000")),
            )
        }
        // Unknown shares are not worth a share price either.
        coVerify(exactly = 0) { kaminoApi.getVaultMetrics(any()) }
    }

    @Test
    fun `a share count below zero counts as unknown rather than as an empty position`() = runTest {
        coEvery { kaminoApi.getUserPositions(ADDRESS) } returns
            listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "-1000"))
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.05")
        coEvery { positionCache.getPositions(VAULT_ID) } returns
            mapOf(STEAKHOUSE.address to BigInteger("500000000"))

        val balance = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

        assertEquals(BigInteger("500000000"), balance.amount)
        coVerify {
            positionCache.savePositions(
                VAULT_ID,
                mapOf(STEAKHOUSE.address to BigInteger("500000000")),
            )
        }
    }

    @Test
    fun `a position with no shares field is not read as an emptied vault`() = runTest {
        // The field is optional on the wire, so an entry arriving without it says nothing about the
        // size — and an entry only exists at all for a vault the wallet is in.
        coEvery { kaminoApi.getUserPositions(ADDRESS) } returns
            listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = null))
        coEvery { positionCache.getPositions(VAULT_ID) } returns
            mapOf(STEAKHOUSE.address to BigInteger("500000000"))

        val balance = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

        assertEquals(BigInteger("500000000"), balance.amount)
    }

    @Test
    fun `a share count of exactly zero is a real emptied vault`() = runTest {
        // The other half of the rule: an answered zero must still erase the old figure, or a
        // withdrawn position would linger on the portfolio for good.
        coEvery { kaminoApi.getUserPositions(ADDRESS) } returns
            listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "0"))
        coEvery { positionCache.getPositions(VAULT_ID) } returns
            mapOf(STEAKHOUSE.address to BigInteger("500000000"))

        assertTrue(service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).isEmpty())
        coVerify {
            positionCache.savePositions(VAULT_ID, mapOf(STEAKHOUSE.address to BigInteger.ZERO))
        }
    }

    @Test
    fun `a cancelled share-price read stops the load rather than falling back`() = runTest {
        coEvery { kaminoApi.getUserPositions(ADDRESS) } returns
            listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "1000"))
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } throws
            CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            runBlocking { service().getRemoteDeFiBalance(ADDRESS, VAULT_ID) }
        }
    }

    @Test
    fun `an opt-in read that fails leaves the rest of the chain's load standing`() = runTest {
        // The Solana provider awaits this service and staking side by side in one scope, so a throw
        // escaping here would cancel the healthy staking figure too.
        every { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flow { throw RuntimeException("datastore") }

        assertTrue(service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).isEmpty())
    }

    @Test
    fun `a snapshot the store refuses to keep does not discard the balance just read`() = runTest {
        coEvery { kaminoApi.getUserPositions(ADDRESS) } returns
            listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "1000"))
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.05")
        coEvery { positionCache.savePositions(any(), any()) } throws RuntimeException("disk full")

        val balance = service().getRemoteDeFiBalance(ADDRESS, VAULT_ID).single().balances.single()

        assertEquals(BigInteger("1050000000"), balance.amount)
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
