@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.KeysignPayload
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class BlockaidSimulationServiceImplTest {

    @Test
    fun `non-eligible chain returns EMPTY without hitting RPC`() = runTest {
        val rpc = FakeRpc()
        val service = BlockaidSimulationServiceImpl(rpc)

        val result = service.scan(payload = bitcoinPayload())

        assertSame(BlockaidKeysignScanResult.EMPTY, result)
        assertEquals(0, rpc.evmCalls)
        assertEquals(0, rpc.solanaCalls)
    }

    @Test
    fun `evm scan caches on success`() = runTest {
        val rpc = FakeRpc(evmResponse = singleTransferEvmResponse())
        val service = BlockaidSimulationServiceImpl(rpc)
        val payload = evmPayload(memo = "0xa9059cbb000000")

        val first = service.scan(payload)
        val second = service.scan(payload)

        assertNotNull(first.simulation)
        assertSame(first.simulation, second.simulation)
        assertEquals(1, rpc.evmCalls, "second call should hit cache")
    }

    @Test
    fun `evm scan does not cache on RPC failure`() = runTest {
        val rpc = FakeRpc(evmThrows = RuntimeException("boom"))
        val service = BlockaidSimulationServiceImpl(rpc)
        val payload = evmPayload(memo = "0xfeed")

        val first = service.scan(payload)
        rpc.evmThrows = null
        rpc.evmResponse = singleTransferEvmResponse()
        val second = service.scan(payload)

        assertSame(BlockaidKeysignScanResult.EMPTY, first)
        assertNotNull(second.simulation)
        assertEquals(2, rpc.evmCalls, "failure should not poison the cache")
    }

    @Test
    fun `concurrent calls coalesce into a single RPC`() = runTest {
        val rpc = FakeRpc(evmResponse = singleTransferEvmResponse(), evmDelayMillis = 100)
        val service = BlockaidSimulationServiceImpl(rpc)
        val payload = evmPayload(memo = "0xabc")

        val results = coroutineScope { (1..8).map { async { service.scan(payload) } }.awaitAll() }

        assertEquals(8, results.size)
        results.forEach {
            // assertSame against the leader's simulation: every follower MUST observe the
            // identical instance the leader produced, not just any non-null value. A regression
            // where each call resolved to a fresh empty result would still pass assertNotNull.
            assertSame(results[0].simulation, it.simulation)
        }
        assertEquals(1, rpc.evmCalls, "inflight coalescing should fire one RPC")
    }

    @Test
    fun `empty simulation result is cached so we do not refetch`() = runTest {
        val rpc =
            FakeRpc(
                evmResponse =
                    BlockaidEvmSimulationResponseJson(
                        simulation = null,
                        validation = null,
                        error = null,
                    )
            )
        val service = BlockaidSimulationServiceImpl(rpc)
        val payload = evmPayload(memo = "0xfeedface")

        val first = service.scan(payload)
        val second = service.scan(payload)

        assertNull(first.simulation)
        assertNull(second.simulation)
        // Empty result is still a valid verdict — cached so subsequent screens
        // don't re-hit the network just to learn the same "no diff" answer.
        assertEquals(1, rpc.evmCalls)
    }

    @Test
    fun `invalidateAll clears cache and unblocks pending awaiters`() = runTest {
        val rpc = FakeRpc(evmResponse = singleTransferEvmResponse())
        val service = BlockaidSimulationServiceImpl(rpc)
        val payload = evmPayload(memo = "0xabc")
        service.scan(payload)

        service.invalidateAll()
        service.scan(payload)

        assertEquals(2, rpc.evmCalls)
    }

    @Test
    fun `follower cancellation propagates without affecting leader`() = runTest {
        // The follower's scan() call should let CancellationException
        // propagate when the follower's own coroutine is cancelled — not
        // swallow it into an EMPTY result. The leader and any other
        // followers must remain unaffected.
        val rpc = FakeRpc(evmResponse = singleTransferEvmResponse(), evmDelayMillis = 50)
        val service = BlockaidSimulationServiceImpl(rpc)
        val payload = evmPayload(memo = "0xabc")

        val leader = async { service.scan(payload) }
        runCurrent()
        val followerA = async { service.scan(payload) }
        val followerB = async { service.scan(payload) }
        runCurrent()

        followerA.cancel()

        val leaderResult = leader.await()
        val followerBResult = followerB.await()

        assertNotNull(leaderResult.simulation)
        assertNotNull(followerBResult.simulation)
        assertEquals(1, rpc.evmCalls)
        assertTrue(followerA.isCancelled)
    }

    @Test
    fun `invalidateAll while leader is in-flight returns EMPTY to leader and follower alike`() =
        runTest {
            // Race: a leader is mid-scan, a follower is awaiting on the leader's deferred, and
            // another caller wipes the cache. Both the follower (woken by invalidateAll) AND the
            // leader (whose dispatch eventually completes) must receive EMPTY — the caller asked
            // for the cache to be cleared, so the leader's pending verdict is stale by the time
            // the dispatch returns. Letting the leader still propagate the real result would
            // re-poison whatever UI state triggered the invalidation (e.g. a vault switch).
            val rpc = FakeRpc(evmResponse = singleTransferEvmResponse(), evmDelayMillis = 100)
            val service = BlockaidSimulationServiceImpl(rpc)
            val payload = evmPayload(memo = "0xabc")

            val leader = async { service.scan(payload) }
            runCurrent()
            val follower = async { service.scan(payload) }
            runCurrent()

            service.invalidateAll()
            advanceUntilIdle()

            assertNull(follower.await().simulation)
            assertNull(leader.await().simulation)
        }

    @Test
    fun `cancellation during scan does not poison the cache for retries`() = runTest {
        // After the leader is cancelled mid-flight, inflight must be cleared so the next caller
        // gets a fresh dispatch — a transient cancellation (e.g. ViewModel scope tearing down
        // between screens) shouldn't leave the cache in a state where the next screen sees
        // EMPTY forever.
        val rpc = FakeRpc(evmResponse = singleTransferEvmResponse(), evmDelayMillis = 50)
        val service = BlockaidSimulationServiceImpl(rpc)
        val payload = evmPayload(memo = "0xabc")

        val leader = launch {
            // Catch only CancellationException so that a non-cancellation throw from `scan`
            // would still fail the test. `runCatching` would have swallowed CE and any other
            // exception, masking regressions.
            try {
                service.scan(payload)
            } catch (_: CancellationException) {}
        }
        runCurrent()
        leader.cancel()
        leader.join()

        val retry = service.scan(payload)
        assertNotNull(retry.simulation)
    }

    @Test
    fun `cancelled scan propagates CancellationException rather than returning EMPTY`() = runTest {
        // The contract `BlockaidSimulationServiceImpl` MUST honour: when the caller's coroutine
        // is cancelled, scan rethrows CancellationException rather than swallowing it into an
        // EMPTY result. Otherwise structured concurrency breaks: a parent that expects to see
        // its child cancellation would instead see a normal completion.
        val rpc = FakeRpc(evmResponse = singleTransferEvmResponse(), evmDelayMillis = 50)
        val service = BlockaidSimulationServiceImpl(rpc)
        val payload = evmPayload(memo = "0xabc")

        val leader = async { service.scan(payload) }
        runCurrent()
        leader.cancel()

        assertThrows<CancellationException> { leader.await() }
        // The cancelled leader must not poison the cache.
        val retry = service.scan(payload)
        assertNotNull(retry.simulation)
    }

    // ---------- fixtures ----------------------------------------------------

    private fun singleTransferEvmResponse(): BlockaidEvmSimulationResponseJson {
        return BlockaidEvmSimulationResponseJson(
            simulation =
                BlockaidEvmSimulationJson(
                    accountSummary =
                        BlockaidEvmSimulationJson.AccountSummary(
                            assetsDiffs =
                                listOf(
                                    BlockaidEvmSimulationJson.AssetDiff(
                                        asset =
                                            BlockaidEvmSimulationJson.Asset(
                                                type = "ERC20",
                                                address = "0xUSDC",
                                                decimals = 6,
                                                symbol = "USDC",
                                                logoUrl = "https://logo/u.png",
                                            ),
                                        outgoing =
                                            listOf(
                                                BlockaidEvmSimulationJson.BalanceChange(
                                                    rawValue = "100"
                                                )
                                            ),
                                    )
                                )
                        )
                )
        )
    }

    private fun evmPayload(memo: String, to: String = "0xto"): KeysignPayload {
        val coin =
            mockk<Coin>(relaxed = true) {
                every { chain } returns Chain.Ethereum
                every { address } returns "0xfrom"
            }
        return mockk<KeysignPayload>(relaxed = true) {
            every { this@mockk.coin } returns coin
            every { this@mockk.memo } returns memo
            every { signSolana } returns null
            every { toAddress } returns to
            every { toAmount } returns BigInteger.ZERO
        }
    }

    private fun bitcoinPayload(): KeysignPayload {
        val coin =
            mockk<Coin>(relaxed = true) {
                every { chain } returns Chain.Bitcoin
                every { address } returns "bc1..."
            }
        return mockk<KeysignPayload>(relaxed = true) {
            every { this@mockk.coin } returns coin
            every { memo } returns null
            every { signSolana } returns null
        }
    }

    // ---------- fake RPC ----------------------------------------------------

    private class FakeRpc(
        var evmResponse: BlockaidEvmSimulationResponseJson? = null,
        var solanaResponse: BlockaidSolanaSimulationResponseJson? = null,
        var evmThrows: Throwable? = null,
        var solanaThrows: Throwable? = null,
        var evmDelayMillis: Long = 0,
    ) : BlockaidRpcClientContract {
        // Plain `Int` is sufficient — `runTest` runs on a single-threaded TestCoroutineScheduler,
        // so concurrent increments cannot race here.
        var evmCalls: Int = 0
            private set

        var solanaCalls: Int = 0
            private set

        override suspend fun simulateEvmTransaction(
            chain: Chain,
            from: String,
            to: String,
            amount: String,
            data: String,
        ): BlockaidEvmSimulationResponseJson {
            if (evmDelayMillis > 0) delay(evmDelayMillis)
            evmCalls++
            evmThrows?.let { throw it }
            return evmResponse ?: error("evm response not configured")
        }

        override suspend fun simulateSolanaTransaction(
            address: String,
            rawTransactionsBase58: List<String>,
        ): BlockaidSolanaSimulationResponseJson {
            solanaCalls++
            solanaThrows?.let { throw it }
            return solanaResponse ?: error("solana response not configured")
        }

        override suspend fun scanBitcoinTransaction(
            address: String,
            serializedTransaction: String,
        ): BlockaidTransactionScanResponseJson = error("not used")

        override suspend fun scanEVMTransaction(
            chain: Chain,
            from: String,
            to: String,
            amount: String,
            data: String,
        ): BlockaidTransactionScanResponseJson = error("not used")

        override suspend fun scanSolanaTransaction(
            address: String,
            serializedMessage: String,
        ): BlockaidTransactionScanResponseJson = error("not used")

        override suspend fun scanSuiTransaction(
            address: String,
            serializedTransaction: String,
        ): BlockaidTransactionScanResponseJson = error("not used")
    }
}
