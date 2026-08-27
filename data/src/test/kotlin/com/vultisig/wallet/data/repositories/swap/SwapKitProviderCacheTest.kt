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
 * Pins the 24h TTL, retry backoff, fail-closed, and refresh-after-invalidate behaviour of
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

    /**
     * Entries whose chains are live. `supportedChainIds` mirrors them, as the wire usually does.
     */
    private fun providersResponse(vararg entries: Pair<String, List<String>>) =
        entries.map { (provider, chains) ->
            SwapKitProviderEntry(
                provider = provider,
                enabledChainIds = chains,
                supportedChainIds = chains,
            )
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
    fun `isEnabled unions enabledChainIds across sub-providers`() = runTest {
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
    fun `a failed refresh keeps serving the last successful snapshot`() = runTest {
        // Dropping the snapshot on a failed refresh reports every chain disabled, which hides
        // SwapKit app-wide off one bad `/providers` call and shows a $0.00 swap fee on the join
        // screen. iOS serves last-good here; so do we.
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("1", "solana"))

        val clock = FakeClock(now = 1_000L)
        val cache = cache(clock)

        assertTrue(cache.isEnabled(Chain.Ethereum)) // populates the snapshot
        coEvery { api.providers() } throws RuntimeException("transport boom")
        clock.now += 24L * 60L * 60L * 1000L + 1L // past TTL, so the refresh is attempted and fails

        assertTrue(cache.isEnabled(Chain.Ethereum))
        assertTrue(cache.isEnabled(Chain.Solana))
        assertFalse(cache.isEnabled(Chain.Bitcoin)) // stale, not blanket-true
    }

    @Test
    fun `a failed refresh holds off the next attempt while the stale snapshot stands`() = runTest {
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("1"))

        val clock = FakeClock(now = 1_000L)
        val cache = cache(clock)

        assertTrue(cache.isEnabled(Chain.Ethereum)) // populates the snapshot
        coEvery { api.providers() } throws RuntimeException("transport boom")
        clock.now += 24L * 60L * 60L * 1000L + 1L // past TTL — one refresh is attempted, and fails

        assertTrue(cache.isEnabled(Chain.Ethereum))
        clock.now += 60_000L // +1 min, well inside the retry window
        assertTrue(cache.isEnabled(Chain.Ethereum))
        assertTrue(cache.isEnabled(Chain.Ethereum))

        // Serving the stale snapshot leaves `fetchedAtMillis` behind the TTL, so without a second
        // deadline every one of these re-hits the failing endpoint — and an eligibility check tests
        // both legs of a pair, putting two dead round-trips in front of each quote.
        coVerify(exactly = 2) { api.providers() }
    }

    @Test
    fun `the refresh is attempted again once the retry window lapses`() = runTest {
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("1"))

        val clock = FakeClock(now = 1_000L)
        val cache = cache(clock)

        assertTrue(cache.isEnabled(Chain.Ethereum))
        coEvery { api.providers() } throws RuntimeException("transport boom")
        clock.now += 24L * 60L * 60L * 1000L + 1L // past TTL
        assertTrue(cache.isEnabled(Chain.Ethereum)) // refresh fails, stale answer served
        coVerify(exactly = 2) { api.providers() }

        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("1", "solana"))
        clock.now += 5L * 60L * 1000L // retry window lapsed
        assertTrue(cache.isEnabled(Chain.Solana)) // recovered endpoint is picked up
        coVerify(exactly = 3) { api.providers() }

        // A success clears the backoff along with the TTL, so the fresh snapshot is served outright
        // rather than through the stale path.
        clock.now += 60_000L
        assertTrue(cache.isEnabled(Chain.Solana))
        coVerify(exactly = 3) { api.providers() }
    }

    @Test
    fun `a failed refresh after invalidate has no snapshot to fall back on`() = runTest {
        // The genuine no-data edge still fails closed: `invalidate` drops the snapshot outright,
        // so there is nothing to serve and SwapKit is skipped until a refresh succeeds.
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("1"))

        val cache = cache()
        assertTrue(cache.isEnabled(Chain.Ethereum))

        cache.invalidate()
        coEvery { api.providers() } throws RuntimeException("transport boom")

        assertFalse(cache.isEnabled(Chain.Ethereum))

        // ...and stays eager rather than backing off. This is the second no-data edge — after
        // `invalidate`, as opposed to never-fetched — and what keeps it eager is `isServable`
        // gating both its branches on `fetchedAtMillis`, which `invalidate` cleared; the narrowing
        // in `backOffAndServeLastGood` is belt-and-braces on top. Ungating the retry branch would
        // strand SwapKit off for the full window with no answer to serve in the meantime.
        assertFalse(cache.isEnabled(Chain.Ethereum))
        coVerify(exactly = 3) { api.providers() }
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
    fun `non-EVM slugs enable their chains`() = runTest {
        coEvery { api.providers() } returns
            providersResponse(
                "CHAINFLIP" to listOf("bitcoin", "ton", "sui", "728126428", "cardano", "ripple")
            )

        val cache = cache()

        assertTrue(cache.isEnabled(Chain.Bitcoin))
        assertTrue(cache.isEnabled(Chain.Ton))
        assertTrue(cache.isEnabled(Chain.Sui))
        assertTrue(cache.isEnabled(Chain.Tron))
        assertTrue(cache.isEnabled(Chain.Cardano))
        assertTrue(cache.isEnabled(Chain.Ripple))
    }

    @Test
    fun `chain ids the wallet holds no account for map to null`() = runTest {
        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig("near"))
        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig("stellar"))
        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig("aleo"))
        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig("80094")) // Berachain
        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig(""))
    }

    @Test
    fun `Robinhood and HyperEVM resolve from their decimal chain ids`() = runTest {
        // Both are live in `/providers.enabledChainIds` as bare decimals. Neither has a
        // hand-written
        // entry — they resolve through the EVM chain-id map built off the Chain enum.
        coEvery { api.providers() } returns providersResponse("FLASHNET" to listOf("4663", "999"))

        val cache = cache()

        assertTrue(cache.isEnabled(Chain.Robinhood))
        assertTrue(cache.isEnabled(Chain.Hyperliquid))
    }

    @Test
    fun `HyperCore's hype id never resolves to the HyperEVM wallet chain`() {
        // `hype` is a separate venue in SwapKit's catalogue, with `USDC:0x…`-style asset addresses.
        // Reading it as Chain.Hyperliquid would offer routes against assets this wallet cannot
        // hold.
        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig("hype"))
        assertEquals(Chain.Hyperliquid, SwapKitProviderCacheImpl.swapKitChainToVultisig("999"))
    }

    @Test
    fun `a chain only listed as supported is not enabled`() = runTest {
        // Observed live: `hype` and `stellar` sit in supportedChainIds while never being enabled,
        // and a dark provider lists everything it knows. Only enablement is an offer.
        coEvery { api.providers() } returns
            listOf(
                SwapKitProviderEntry(
                    provider = "GARDEN",
                    enabledChainIds = listOf("1"),
                    supportedChainIds = listOf("1", "999", "bitcoin"),
                )
            )

        val cache = cache()

        assertTrue(cache.isEnabled(Chain.Ethereum))
        assertFalse(cache.isEnabled(Chain.Hyperliquid))
        assertFalse(cache.isEnabled(Chain.Bitcoin))
    }

    @Test
    fun `chains enabled only by THORChain or Maya sub-providers do not count`() = runTest {
        // Vultisig routes those two through its own integrations and drops their SwapKit routes at
        // ranking, so a chain they alone enable would offer a provider that can only lose. Casing
        // is normalized because the upstream has returned both spellings.
        coEvery { api.providers() } returns
            providersResponse(
                "THORCHAIN" to listOf("bitcoin"),
                "thorchain_streaming" to listOf("litecoin"),
                "MAYACHAIN" to listOf("dash"),
                "MAYACHAIN_STREAMING" to listOf("zcash"),
                "CHAINFLIP" to listOf("1"),
            )

        val cache = cache()

        assertTrue(cache.isEnabled(Chain.Ethereum))
        assertFalse(cache.isEnabled(Chain.Bitcoin))
        assertFalse(cache.isEnabled(Chain.Litecoin))
        assertFalse(cache.isEnabled(Chain.Dash))
        assertFalse(cache.isEnabled(Chain.Zcash))
    }
}
