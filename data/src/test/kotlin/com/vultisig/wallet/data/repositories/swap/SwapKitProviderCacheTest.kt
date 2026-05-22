package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.models.quotes.SwapKitProviderEntry
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.models.Chain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pins the 24h TTL, fail-closed, and refresh-after-invalidate behaviour of
 * [SwapKitProviderCacheImpl]. The cache is the only guard between us and a hot loop on
 * `/providers`, so the TTL/refresh contract is worth a regression test.
 */
internal class SwapKitProviderCacheTest {

    private val api: SwapKitApi = mockk()

    /**
     * Default `now` is non-zero so `fetchedAtMillis` after the first fetch never collides with
     * `SwapKitProviderCache`'s `fetchedAtMillis == 0L` "never fetched" sentinel — otherwise a test
     * that intends to read from the cache would silently re-hit the API instead.
     */
    private class FakeClock(var now: Long = 1_000L) : SwapKitProviderCacheImpl.Clock {
        override fun nowMillis(): Long = now
    }

    private fun cache(clock: FakeClock = FakeClock()) =
        SwapKitProviderCacheImpl(api).also { it.clock = clock }

    private fun providersResponse(vararg entries: Pair<String, List<String>>) =
        entries.map { (provider, chains) ->
            SwapKitProviderEntry(provider = provider, supportedChainIds = chains)
        }

    @Test
    fun `isEnabled returns true for cached chain id`() = runTest {
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("1", "solana"))

        val cache = cache()

        assertTrue(cache.isEnabled(Chain.Ethereum))
        assertTrue(cache.isEnabled(Chain.Solana))
    }

    @Test
    fun `isEnabled returns false for chain not present in providers response`() = runTest {
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("1"))

        val cache = cache()

        assertFalse(cache.isEnabled(Chain.Bitcoin))
        assertFalse(cache.isEnabled(Chain.Tron))
    }

    @Test
    fun `isEnabled unions supportedChainIds across sub-providers`() = runTest {
        coEvery { api.providers() } returns
            providersResponse(
                "CHAINFLIP" to listOf("1", "bitcoin"),
                "NEAR_INTENTS" to listOf("solana", "42161"),
                "GARDEN" to listOf("56"),
            )

        val cache = cache()

        assertTrue(cache.isEnabled(Chain.Ethereum))
        assertTrue(cache.isEnabled(Chain.Solana))
        assertTrue(cache.isEnabled(Chain.Arbitrum))
        assertTrue(cache.isEnabled(Chain.BscChain))
    }

    @Test
    fun `api is hit only once across repeated calls within TTL`() = runTest {
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("1"))

        val clock = FakeClock(now = 1_000L)
        val cache = cache(clock)

        cache.isEnabled(Chain.Ethereum)
        clock.now += 60_000L // +1 min — well within 24h
        cache.isEnabled(Chain.Ethereum)
        clock.now += 23L * 60L * 60L * 1000L // +23h — still within TTL
        cache.isEnabled(Chain.Solana)

        coVerify(exactly = 1) { api.providers() }
    }

    @Test
    fun `api is refetched once TTL has elapsed`() = runTest {
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("1"))

        // Start at a non-zero clock so we exercise the TTL boundary, not the
        // `fetchedAtMillis == 0` sentinel that means "never fetched yet".
        val clock = FakeClock(now = 1_000L)
        val cache = cache(clock)

        cache.isEnabled(Chain.Ethereum) // first fetch
        clock.now += 60_000L // +1 min — still inside TTL, no refetch
        cache.isEnabled(Chain.Ethereum)
        clock.now += 24L * 60L * 60L * 1000L // push past TTL — should refetch
        cache.isEnabled(Chain.Ethereum)

        coVerify(exactly = 2) { api.providers() }
    }

    @Test
    fun `chain set picks up updates after a refetch`() = runTest {
        coEvery { api.providers() } returnsMany
            listOf(
                providersResponse("CHAINFLIP" to listOf("1")),
                providersResponse("CHAINFLIP" to listOf("1", "solana")),
            )

        val clock = FakeClock(now = 1_000L)
        val cache = cache(clock)

        assertFalse(cache.isEnabled(Chain.Solana)) // Solana not present in first fetch
        clock.now += 24L * 60L * 60L * 1000L + 1L // past TTL
        assertTrue(cache.isEnabled(Chain.Solana)) // refetch picks it up
    }

    @Test
    fun `network failure short-circuits to false without caching the failure`() = runTest {
        coEvery { api.providers() } throws RuntimeException("transport boom")

        val cache = cache()

        assertFalse(cache.isEnabled(Chain.Ethereum))

        // failure must not poison the cache — a recovered API call should populate it
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("1"))
        assertTrue(cache.isEnabled(Chain.Ethereum))

        // Pin the retry behaviour itself: a regression that started caching the failure for the
        // TTL window would still produce `true` above if the second call happened to land inside
        // the new TTL, but it wouldn't actually re-hit the API.
        coVerify(exactly = 2) { api.providers() }
    }

    @Test
    fun `cancellation while fetching is re-thrown, not swallowed`() = runTest {
        coEvery { api.providers() } throws CancellationException("scope cancelled")

        val cache = cache()

        assertThrows<CancellationException> { cache.isEnabled(Chain.Ethereum) }
    }

    @Test
    fun `invalidate forces a refetch on next call`() = runTest {
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("1"))

        val clock = FakeClock(now = 1_000L)
        val cache = cache(clock)

        cache.isEnabled(Chain.Ethereum)
        clock.now += 60_000L // still inside TTL
        cache.invalidate()
        cache.isEnabled(Chain.Ethereum)

        coVerify(exactly = 2) { api.providers() }
    }

    @Test
    fun `provider chain id mapping covers Phase 1 EVM and Solana aliases`() {
        val map = SwapKitProviderCacheImpl::class.java
        // Spot-check via the cache itself rather than reflecting the private function — these are
        // the V3 identifier shapes the proxy is allowed to return per the SwapKit docs.
        val aliases =
            mapOf(
                // V3 returns EVM chain ids as decimal strings and lowercase named ids for non-EVM.
                "1" to Chain.Ethereum,
                "ethereum" to Chain.Ethereum,
                "56" to Chain.BscChain,
                "bsc" to Chain.BscChain,
                "bnb" to Chain.BscChain,
                "43114" to Chain.Avalanche,
                "avalanche" to Chain.Avalanche,
                "42161" to Chain.Arbitrum,
                "arbitrum" to Chain.Arbitrum,
                "10" to Chain.Optimism,
                "optimism" to Chain.Optimism,
                "8453" to Chain.Base,
                "base" to Chain.Base,
                "137" to Chain.Polygon,
                "polygon" to Chain.Polygon,
                "matic" to Chain.Polygon,
                "solana" to Chain.Solana,
                // Case-insensitivity sanity check.
                "Ethereum" to Chain.Ethereum,
                "SOLANA" to Chain.Solana,
            )

        assertNotNull(map)
        aliases.forEach { (raw, chain) ->
            assertEquals(
                chain,
                SwapKitProviderCacheImpl.swapKitChainToVultisig(raw),
                "Expected $raw to map to $chain",
            )
            assertEquals(
                chain,
                SwapKitProviderCacheImpl.swapKitChainToVultisig(raw.lowercase()),
                "Expected $raw (lower-cased) to map to $chain",
            )
        }
    }

    @Test
    fun `unsupported chain ids map to null and never enable a chain`() = runTest {
        // Bitcoin / TON / Sui / Tron / Cardano are Phase 2/3 sources — they must NOT light up in
        // Phase 1, regardless of which V3 id format the proxy returns.
        coEvery { api.providers() } returns
            providersResponse("CHAINFLIP" to listOf("bitcoin", "ton", "sui", "tron", "cardano"))

        val cache = cache()

        assertFalse(cache.isEnabled(Chain.Bitcoin))
        assertFalse(cache.isEnabled(Chain.Ton))
        assertFalse(cache.isEnabled(Chain.Sui))
        assertFalse(cache.isEnabled(Chain.Tron))
        assertFalse(cache.isEnabled(Chain.Cardano))

        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig("bitcoin"))
        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig("xrp"))
        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig(""))
    }
}
