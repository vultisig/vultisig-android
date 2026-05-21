package com.vultisig.wallet.data.repositories.swap

import com.vultisig.wallet.data.api.models.quotes.SwapKitProviderEntry
import com.vultisig.wallet.data.api.models.quotes.SwapKitProvidersResponseJson
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

    private class FakeClock(var now: Long = 0L) : SwapKitProviderCacheImpl.Clock {
        override fun nowMillis(): Long = now
    }

    private fun cache(clock: FakeClock = FakeClock()) =
        SwapKitProviderCacheImpl(api).also { it.clock = clock }

    private fun providersResponse(vararg entries: Pair<String, List<String>>) =
        SwapKitProvidersResponseJson(
            providers =
                entries.map { (provider, chains) ->
                    SwapKitProviderEntry(provider = provider, enabledChainIds = chains)
                }
        )

    @Test
    fun `isEnabled returns true for cached chain id`() = runTest {
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("ETH", "SOL"))

        val cache = cache()

        assertTrue(cache.isEnabled(Chain.Ethereum))
        assertTrue(cache.isEnabled(Chain.Solana))
    }

    @Test
    fun `isEnabled returns false for chain not present in providers response`() = runTest {
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("ETH"))

        val cache = cache()

        assertFalse(cache.isEnabled(Chain.Bitcoin))
        assertFalse(cache.isEnabled(Chain.Tron))
    }

    @Test
    fun `isEnabled unions enabledChainIds across sub-providers`() = runTest {
        coEvery { api.providers() } returns
            providersResponse(
                "CHAINFLIP" to listOf("ETH", "BTC"),
                "NEAR_INTENTS" to listOf("SOL", "ARB"),
                "GARDEN" to listOf("BSC"),
            )

        val cache = cache()

        assertTrue(cache.isEnabled(Chain.Ethereum))
        assertTrue(cache.isEnabled(Chain.Solana))
        assertTrue(cache.isEnabled(Chain.Arbitrum))
        assertTrue(cache.isEnabled(Chain.BscChain))
    }

    @Test
    fun `api is hit only once across repeated calls within TTL`() = runTest {
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("ETH"))

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
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("ETH"))

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
                providersResponse("CHAINFLIP" to listOf("ETH")),
                providersResponse("CHAINFLIP" to listOf("ETH", "SOL")),
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
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("ETH"))
        assertTrue(cache.isEnabled(Chain.Ethereum))
    }

    @Test
    fun `cancellation while fetching is re-thrown, not swallowed`() = runTest {
        coEvery { api.providers() } throws CancellationException("scope cancelled")

        val cache = cache()

        assertThrows<CancellationException> { cache.isEnabled(Chain.Ethereum) }
    }

    @Test
    fun `invalidate forces a refetch on next call`() = runTest {
        coEvery { api.providers() } returns providersResponse("CHAINFLIP" to listOf("ETH"))

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
        // the aliases iOS Phase 1 honours and the proxy is allowed to return.
        val aliases =
            mapOf(
                "ETH" to Chain.Ethereum,
                "ETHEREUM" to Chain.Ethereum,
                "BSC" to Chain.BscChain,
                "BNB" to Chain.BscChain,
                "AVAX" to Chain.Avalanche,
                "AVALANCHE" to Chain.Avalanche,
                "ARB" to Chain.Arbitrum,
                "ARBITRUM" to Chain.Arbitrum,
                "OP" to Chain.Optimism,
                "OPTIMISM" to Chain.Optimism,
                "BASE" to Chain.Base,
                "MATIC" to Chain.Polygon,
                "POL" to Chain.Polygon,
                "POLYGON" to Chain.Polygon,
                "SOL" to Chain.Solana,
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
        // BTC / TON / SUI / TRX / ADA are Phase 2/3 sources — they must NOT light up in Phase 1.
        coEvery { api.providers() } returns
            providersResponse("CHAINFLIP" to listOf("BTC", "TON", "SUI", "TRX", "ADA"))

        val cache = cache()

        assertFalse(cache.isEnabled(Chain.Bitcoin))
        assertFalse(cache.isEnabled(Chain.Ton))
        assertFalse(cache.isEnabled(Chain.Sui))
        assertFalse(cache.isEnabled(Chain.Tron))
        assertFalse(cache.isEnabled(Chain.Cardano))

        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig("BTC"))
        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig("XRP"))
        assertNull(SwapKitProviderCacheImpl.swapKitChainToVultisig(""))
    }
}
