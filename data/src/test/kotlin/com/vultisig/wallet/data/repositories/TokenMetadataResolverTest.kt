@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class TokenMetadataResolverTest {

    @Test
    fun `resolve returns symbol and decimals from the underlying repository`() = runTest {
        val repo = FakeTokenRepository(usdcOnEthereum())
        val resolver = newResolver(repo)

        val metadata = resolver.resolve(Chain.Ethereum, USDC)

        assertEquals(TokenMetadata(symbol = "USDC", decimals = 6), metadata)
        assertEquals(1, repo.calls.get())
    }

    @Test
    fun `cached resolution does not hit the repository again`() = runTest {
        val repo = FakeTokenRepository(usdcOnEthereum())
        val resolver = newResolver(repo)

        resolver.resolve(Chain.Ethereum, USDC)
        resolver.resolve(Chain.Ethereum, USDC)
        resolver.resolve(Chain.Ethereum, USDC.uppercase())

        assertEquals(1, repo.calls.get())
    }

    @Test
    fun `concurrent calls for the same key dedupe to a single fetch`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeTokenRepository(usdcOnEthereum(), gate = gate)
        val resolver = newResolver(repo)

        val a = async { resolver.resolve(Chain.Ethereum, USDC) }
        val b = async { resolver.resolve(Chain.Ethereum, USDC) }
        val c = async { resolver.resolve(Chain.Ethereum, USDC) }
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(TokenMetadata("USDC", 6), a.await())
        assertEquals(TokenMetadata("USDC", 6), b.await())
        assertEquals(TokenMetadata("USDC", 6), c.await())
        assertEquals(1, repo.calls.get())
    }

    @Test
    fun `failures are not cached and the next call retries`() = runTest {
        val repo = FakeTokenRepository(usdcOnEthereum())
        repo.nextThrowable = RuntimeException("rpc down")
        val resolver = newResolver(repo)

        assertNull(resolver.resolve(Chain.Ethereum, USDC))
        assertEquals(1, repo.calls.get())

        val metadata = resolver.resolve(Chain.Ethereum, USDC)
        assertEquals(TokenMetadata("USDC", 6), metadata)
        assertEquals(2, repo.calls.get())
    }

    @Test
    fun `null coin from the repository yields null metadata and is not cached`() = runTest {
        val repo = FakeTokenRepository(coin = null)
        val resolver = newResolver(repo)

        assertNull(resolver.resolve(Chain.Ethereum, USDC))
        assertEquals(1, repo.calls.get())
    }

    @Test
    fun `blank symbol is rejected`() = runTest {
        val repo = FakeTokenRepository(usdcOnEthereum().copy(ticker = "   "))
        val resolver = newResolver(repo)

        assertNull(resolver.resolve(Chain.Ethereum, USDC))
    }

    @Test
    fun `decimals exceeding the ceiling are rejected`() = runTest {
        val repo = FakeTokenRepository(usdcOnEthereum().copy(decimal = 255))
        val resolver = newResolver(repo)

        assertNull(resolver.resolve(Chain.Ethereum, USDC))
    }

    @Test
    fun `negative decimals are rejected`() = runTest {
        val repo = FakeTokenRepository(usdcOnEthereum().copy(decimal = -1))
        val resolver = newResolver(repo)

        assertNull(resolver.resolve(Chain.Ethereum, USDC))
    }

    @Test
    fun `non-EVM chains return null without hitting the repository`() = runTest {
        val repo = FakeTokenRepository(usdcOnEthereum())
        val resolver = newResolver(repo)

        assertNull(resolver.resolve(Chain.Solana, USDC))
        assertEquals(0, repo.calls.get())
    }

    @Test
    fun `blank contract address returns null without hitting the repository`() = runTest {
        val repo = FakeTokenRepository(usdcOnEthereum())
        val resolver = newResolver(repo)

        assertNull(resolver.resolve(Chain.Ethereum, ""))
        assertNull(resolver.resolve(Chain.Ethereum, "   "))
        assertEquals(0, repo.calls.get())
    }

    @Test
    fun `cache key is chain-scoped so the same address on a different chain re-fetches`() =
        runTest {
            val repo = FakeTokenRepository(usdcOnEthereum())
            val resolver = newResolver(repo)

            val ethereum = resolver.resolve(Chain.Ethereum, USDC)
            val arbitrum = resolver.resolve(Chain.Arbitrum, USDC)

            assertEquals(2, repo.calls.get())
            assertNotEquals(null, ethereum)
            assertNotEquals(null, arbitrum)
        }

    @Test
    fun `entries past the TTL trigger a refetch`() = runTest {
        val now = AtomicInteger(0)
        val repo = FakeTokenRepository(usdcOnEthereum())
        val resolver =
            TokenMetadataResolver(
                tokenRepository = repo,
                ioDispatcher = testDispatcher(),
                clock = { now.get().toLong() },
                ttl = 1.hours,
            )

        resolver.resolve(Chain.Ethereum, USDC)
        assertEquals(1, repo.calls.get())

        now.set((1.hours.inWholeMilliseconds + 1L).toInt())
        resolver.resolve(Chain.Ethereum, USDC)
        assertEquals(2, repo.calls.get())
    }

    @Test
    fun `concurrent callers do not all observe the failure as a thrown exception`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeTokenRepository(coin = null, gate = gate)
        repo.nextThrowable = RuntimeException("rpc died")
        val resolver = newResolver(repo)

        val results = mutableListOf<TokenMetadata?>()
        repeat(3) { launch { results += resolver.resolve(Chain.Ethereum, USDC) } }
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(3, results.size)
        results.forEach { assertNull(it) }
    }

    private fun TestScope.newResolver(repo: TokenRepository): TokenMetadataResolver =
        TokenMetadataResolver(
            tokenRepository = repo,
            ioDispatcher = testDispatcher(),
            clock = { 0L },
            ttl = 24.hours,
        )

    private fun TestScope.testDispatcher(): CoroutineDispatcher =
        StandardTestDispatcher(testScheduler)

    private fun usdcOnEthereum(): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = USDC,
            isNativeToken = false,
        )

    private companion object {
        private const val USDC = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
    }

    private class FakeTokenRepository(
        private val coin: Coin?,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : TokenRepository {
        val calls = AtomicInteger(0)
        var nextThrowable: Throwable? = null

        override suspend fun getToken(tokenId: String): Coin? = error("unused")

        override suspend fun getNativeToken(chainId: String): Coin = error("unused")

        override suspend fun getEVMTokenByContract(
            chainId: String,
            contractAddress: String,
        ): Coin? {
            calls.incrementAndGet()
            gate?.await()
            nextThrowable?.let {
                nextThrowable = null
                throw it
            }
            return coin
        }

        override suspend fun getTokensWithBalance(
            chain: Chain,
            address: String,
            enabledDenoms: Set<String>,
        ): List<Coin> = error("unused")

        override suspend fun getRefreshTokens(chain: Chain, vault: Vault): List<Coin> =
            error("unused")

        override val builtInTokens: Flow<List<Coin>> = emptyFlow()
        override val nativeTokens: Flow<List<Coin>> = emptyFlow()
    }
}
