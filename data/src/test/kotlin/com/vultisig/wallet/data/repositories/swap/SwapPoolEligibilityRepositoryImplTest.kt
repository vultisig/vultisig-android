package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.MayaNodePool
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolJson
import com.vultisig.wallet.data.models.Chain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the live branch of [SwapPoolEligibilityRepositoryImpl] — the whole point of #4975 —
 * that the table tests never reach because they inject [EmptySwapPoolEligibility]. Pins
 * `normalize()`, the `key()`↔pool-prefix mapping for every gated chain, case-insensitive
 * `Available` filtering, last-good-on-failure, the populate signal, and TTL gating.
 */
internal class SwapPoolEligibilityRepositoryImplTest {

    private val thorChainApi: ThorChainApi = mockk()
    private val mayaChainApi: MayaChainApi = mockk()

    private fun repo() = SwapPoolEligibilityRepositoryImpl(thorChainApi, mayaChainApi)

    private fun thorPool(asset: String, status: String = "Available") =
        ThorChainPoolJson(asset = asset, assetTorPrice = BigInteger.ONE, status = status)

    private fun mayaPool(asset: String, status: String = "Available") =
        MayaNodePool(asset = asset, status = status)

    @Test
    fun `normalizes contract-suffixed asset ids and drops malformed ones`() = runTest {
        coEvery { thorChainApi.getPools() } returns
            listOf(
                thorPool("ETH.USDT-0xdAC17F958D2ee523a2206206994597C13D831ec7"),
                thorPool("ETH.ETH"),
                thorPool(".USDT"), // leading dot — no chain part
                thorPool("ETH."), // trailing dot — no ticker part
                thorPool("NODOT"), // no dot at all
            )
        coEvery { mayaChainApi.getMayaNodePools() } returns emptyList()

        val repo = repo()
        repo.refresh()

        // The contract suffix is stripped and the key is matched case-insensitively.
        assertTrue(repo.isThorEligible(Chain.Ethereum, "usdt"))
        assertTrue(repo.isThorEligible(Chain.Ethereum, "ETH"))
        // Malformed ids never produce a key, so nothing spurious matches.
        assertFalse(repo.isThorEligible(Chain.Ethereum, "NODOT"))
    }

    @Test
    fun `filters pools by Available status case-insensitively`() = runTest {
        coEvery { thorChainApi.getPools() } returns
            listOf(
                thorPool("ETH.USDT", status = "available"),
                thorPool("ETH.USDC", status = "AVAILABLE"),
                thorPool("ETH.WBTC", status = "Staged"),
                thorPool("ETH.DAI", status = "Suspended"),
            )
        coEvery { mayaChainApi.getMayaNodePools() } returns emptyList()

        val repo = repo()
        repo.refresh()

        assertTrue(repo.isThorEligible(Chain.Ethereum, "USDT"))
        assertTrue(repo.isThorEligible(Chain.Ethereum, "USDC"))
        assertFalse(repo.isThorEligible(Chain.Ethereum, "WBTC"))
        assertFalse(repo.isThorEligible(Chain.Ethereum, "DAI"))
    }

    @Test
    fun `key matches the pool prefix for every gated THOR chain`() = runTest {
        coEvery { thorChainApi.getPools() } returns
            listOf(
                thorPool("ETH.USDT"),
                thorPool("BSC.BNB"),
                thorPool("AVAX.USDC"),
                thorPool("BASE.ETH"),
            )
        coEvery { mayaChainApi.getMayaNodePools() } returns emptyList()

        val repo = repo()
        repo.refresh()

        assertTrue(repo.isThorEligible(Chain.Ethereum, "USDT"))
        assertTrue(repo.isThorEligible(Chain.BscChain, "BNB"))
        assertTrue(repo.isThorEligible(Chain.Avalanche, "USDC"))
        assertTrue(repo.isThorEligible(Chain.Base, "ETH"))
        // A pool on a different chain must not leak across chains sharing a ticker.
        assertFalse(repo.isThorEligible(Chain.BscChain, "USDT"))
    }

    @Test
    fun `key matches the pool prefix for the gated Maya chain`() = runTest {
        coEvery { thorChainApi.getPools() } returns emptyList()
        coEvery { mayaChainApi.getMayaNodePools() } returns
            listOf(mayaPool("ARB.ETH"), mayaPool("ETH.USDT-0xdAC17F958D2ee523"))

        val repo = repo()
        repo.refresh()

        assertTrue(repo.isMayaEligible(Chain.Arbitrum, "ETH"))
        // The issue's concrete trigger: ETH.USDT routable via Maya once its pool is Available.
        assertTrue(repo.isMayaEligible(Chain.Ethereum, "USDT"))
        assertFalse(repo.isThorEligible(Chain.Arbitrum, "ETH"))
    }

    @Test
    fun `keeps the last-good snapshot when a later refresh fails`() = runTest {
        coEvery { thorChainApi.getPools() } returns listOf(thorPool("ETH.USDT"))
        coEvery { mayaChainApi.getMayaNodePools() } returns emptyList()

        val repo = repo()
        repo.refresh()
        assertTrue(repo.isThorEligible(Chain.Ethereum, "USDT"))

        // Both endpoints now fail; the previously fetched route must survive.
        coEvery { thorChainApi.getPools() } throws RuntimeException("boom")
        coEvery { mayaChainApi.getMayaNodePools() } throws RuntimeException("boom")
        repo.refresh()

        assertTrue(repo.isThorEligible(Chain.Ethereum, "USDT"))
    }

    @Test
    fun `eligibility version flips to one only after pools first populate`() = runTest {
        coEvery { thorChainApi.getPools() } returns emptyList()
        coEvery { mayaChainApi.getMayaNodePools() } returns emptyList()

        val repo = repo()
        assertEquals(0, repo.eligibilityVersion.value)

        // A successful-but-empty refresh is not a populate transition.
        repo.refresh()
        assertEquals(0, repo.eligibilityVersion.value)

        coEvery { thorChainApi.getPools() } returns listOf(thorPool("ETH.USDT"))
        repo.refresh()
        assertEquals(1, repo.eligibilityVersion.value)

        // A later refresh keeps the version so a displayed quote is not disrupted.
        repo.refresh()
        assertEquals(1, repo.eligibilityVersion.value)
    }

    @Test
    fun `a fresh successful snapshot is not re-fetched by a synchronous read`() = runTest {
        coEvery { thorChainApi.getPools() } returns listOf(thorPool("ETH.USDT"))
        coEvery { mayaChainApi.getMayaNodePools() } returns emptyList()

        val repo = repo()
        repo.refresh()

        // Reads inside the TTL window read the cached snapshot without hitting the endpoint
        // again — the single fetch is the one from refresh() above.
        repeat(5) { repo.isThorEligible(Chain.Ethereum, "USDT") }

        coVerify(exactly = 1) { thorChainApi.getPools() }
    }
}
