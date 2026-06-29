package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadClaimableQbtcUtxosUseCaseTest {

    private val p2wpkh = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
    // Mature (> 144 confirmations) so it passes the maturity gate.
    private val candidate =
        ClaimableUtxo(txid = "aa".repeat(32), vout = 0, amount = 100_000, confirmations = 145)

    private fun utxoWith(confirmations: Long?) =
        candidate.copy(txid = "bb".repeat(32), confirmations = confirmations)

    @Test
    fun `unsupported btc address blocks before any network call`() = runTest {
        val chain = FakeChainService()
        val utxos = FakeUtxosService(listOf(candidate))
        val result = useCase(chain, utxos).invoke("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")

        assertTrue(
            (result as QbtcClaimLoadResult.Blocked).reason
                is QbtcClaimBlockedReason.UnsupportedBtcAddress
        )
        assertEquals(0, chain.disabledCalls)
        assertEquals(0, utxos.fetchCalls)
    }

    @Test
    fun `kill switch closed blocks the claim`() = runTest {
        val result =
            useCase(FakeChainService(disabled = true), FakeUtxosService(listOf(candidate)))
                .invoke(p2wpkh)
        assertEquals(
            QbtcClaimBlockedReason.KillSwitchClosed,
            (result as QbtcClaimLoadResult.Blocked).reason,
        )
    }

    @Test
    fun `kill switch error fails closed`() = runTest {
        val result =
            useCase(
                    FakeChainService(disabledError = RuntimeException("net")),
                    FakeUtxosService(listOf(candidate)),
                )
                .invoke(p2wpkh)
        assertEquals(
            QbtcClaimBlockedReason.KillSwitchClosed,
            (result as QbtcClaimLoadResult.Blocked).reason,
        )
    }

    @Test
    fun `empty filtered set blocks as no utxos`() = runTest {
        val result = useCase(FakeChainService(), FakeUtxosService(emptyList())).invoke(p2wpkh)
        assertEquals(QbtcClaimBlockedReason.NoUtxos, (result as QbtcClaimLoadResult.Blocked).reason)
    }

    @Test
    fun `fetch failure blocks as fetch failed`() = runTest {
        val result =
            useCase(
                    FakeChainService(),
                    FakeUtxosService(error = RuntimeException("blockchair down")),
                )
                .invoke(p2wpkh)
        assertTrue(
            (result as QbtcClaimLoadResult.Blocked).reason is QbtcClaimBlockedReason.UtxoFetchFailed
        )
    }

    @Test
    fun `available when claimable utxos remain`() = runTest {
        val result = useCase(FakeChainService(), FakeUtxosService(listOf(candidate))).invoke(p2wpkh)
        assertEquals(listOf(candidate), (result as QbtcClaimLoadResult.Available).utxos)
    }

    @Test
    fun `utxo with 143 confirmations surfaces as maturing`() = runTest {
        val immature = utxoWith(143)
        val result = useCase(FakeChainService(), FakeUtxosService(listOf(immature))).invoke(p2wpkh)
        assertEquals(listOf(immature), (result as QbtcClaimLoadResult.Maturing).utxos)
    }

    @Test
    fun `utxo with exactly 144 confirmations surfaces as maturing since chain requires strictly more`() =
        runTest {
            val immature = utxoWith(144)
            val result =
                useCase(FakeChainService(), FakeUtxosService(listOf(immature))).invoke(p2wpkh)
            assertEquals(listOf(immature), (result as QbtcClaimLoadResult.Maturing).utxos)
        }

    @Test
    fun `utxo with 145 confirmations is available`() = runTest {
        val mature = utxoWith(145)
        val result = useCase(FakeChainService(), FakeUtxosService(listOf(mature))).invoke(p2wpkh)
        assertEquals(listOf(mature), (result as QbtcClaimLoadResult.Available).utxos)
    }

    @Test
    fun `utxo with null confirmations surfaces as maturing`() = runTest {
        val immature = utxoWith(null)
        val result = useCase(FakeChainService(), FakeUtxosService(listOf(immature))).invoke(p2wpkh)
        assertEquals(listOf(immature), (result as QbtcClaimLoadResult.Maturing).utxos)
    }

    @Test
    fun `only mature utxos survive a mixed set`() = runTest {
        val mature =
            ClaimableUtxo(txid = "cc".repeat(32), vout = 0, amount = 50_000, confirmations = 145)
        val immatureLow =
            ClaimableUtxo(txid = "dd".repeat(32), vout = 0, amount = 60_000, confirmations = 144)
        val immatureNull =
            ClaimableUtxo(txid = "ee".repeat(32), vout = 0, amount = 70_000, confirmations = null)
        val result =
            useCase(FakeChainService(), FakeUtxosService(listOf(immatureLow, mature, immatureNull)))
                .invoke(p2wpkh)
        assertEquals(listOf(mature), (result as QbtcClaimLoadResult.Available).utxos)
    }

    @Test
    fun `all immature utxos surface as maturing`() = runTest {
        val immature =
            listOf(
                ClaimableUtxo(txid = "11".repeat(32), vout = 0, amount = 1, confirmations = 0),
                ClaimableUtxo(txid = "22".repeat(32), vout = 0, amount = 1, confirmations = 143),
                ClaimableUtxo(txid = "33".repeat(32), vout = 0, amount = 1, confirmations = 144),
                ClaimableUtxo(txid = "44".repeat(32), vout = 0, amount = 1, confirmations = null),
            )
        val result = useCase(FakeChainService(), FakeUtxosService(immature)).invoke(p2wpkh)
        assertEquals(immature, (result as QbtcClaimLoadResult.Maturing).utxos)
    }

    @Test
    fun `mature utxos take priority and maturing ones are hidden from the available set`() =
        runTest {
            val mature =
                ClaimableUtxo(
                    txid = "cc".repeat(32),
                    vout = 0,
                    amount = 50_000,
                    confirmations = 200,
                )
            val immature =
                ClaimableUtxo(txid = "dd".repeat(32), vout = 0, amount = 60_000, confirmations = 10)
            val result =
                useCase(FakeChainService(), FakeUtxosService(listOf(immature, mature)))
                    .invoke(p2wpkh)
            // As long as something is claimable now, the screen shows the selectable set, not
            // maturing.
            assertEquals(listOf(mature), (result as QbtcClaimLoadResult.Available).utxos)
        }

    @Test
    fun `maturing utxos survive even when the chain has not indexed them yet`() = runTest {
        // The chain 404s every UTXO it hasn't indexed — which is exactly what happens for a
        // still-maturing (sub-threshold) UTXO. The maturing set must NOT be routed through the
        // cross-check, or it would be dropped and the screen would fall back to NoUtxos.
        val maturing = utxoWith(50)
        val result =
            useCase(FakeChainService(dropsAll = true), FakeUtxosService(listOf(maturing)))
                .invoke(p2wpkh)
        assertEquals(listOf(maturing), (result as QbtcClaimLoadResult.Maturing).utxos)
    }

    @Test
    fun `only the mature set is cross-checked against the chain`() = runTest {
        val mature =
            ClaimableUtxo(txid = "cc".repeat(32), vout = 0, amount = 50_000, confirmations = 200)
        val maturing =
            ClaimableUtxo(txid = "dd".repeat(32), vout = 0, amount = 60_000, confirmations = 50)
        val chain = FakeChainService()
        val result = useCase(chain, FakeUtxosService(listOf(maturing, mature))).invoke(p2wpkh)

        assertEquals(listOf(mature), (result as QbtcClaimLoadResult.Available).utxos)
        // The maturing UTXO must never reach filterClaimable.
        assertEquals(listOf(mature), chain.filteredArgument)
    }

    private fun useCase(chain: FakeChainService, utxos: FakeUtxosService) =
        LoadClaimableQbtcUtxosUseCaseImpl(chain, utxos)

    private class FakeChainService(
        private val disabled: Boolean = false,
        private val disabledError: Throwable? = null,
        private val dropsAll: Boolean = false,
    ) : QbtcClaimChainService {
        var disabledCalls = 0
        var filteredArgument: List<ClaimableUtxo>? = null

        override suspend fun isClaimWithProofDisabled(): Boolean {
            disabledCalls++
            disabledError?.let { throw it }
            return disabled
        }

        // Records what it was asked to cross-check. Identity filter by default — the use-case test
        // exercises pipeline branching, not the cross-check — or drops everything to emulate the
        // chain 404ing UTXOs it has not indexed.
        override suspend fun filterClaimable(utxos: List<ClaimableUtxo>): List<ClaimableUtxo> {
            filteredArgument = utxos
            return if (dropsAll) emptyList() else utxos
        }
    }

    private class FakeUtxosService(
        private val candidates: List<ClaimableUtxo> = emptyList(),
        private val error: Throwable? = null,
    ) : QbtcClaimableUtxosService {
        var fetchCalls = 0

        override suspend fun fetchClaimableCandidates(btcAddress: String): List<ClaimableUtxo> {
            fetchCalls++
            error?.let { throw it }
            return candidates
        }
    }
}
