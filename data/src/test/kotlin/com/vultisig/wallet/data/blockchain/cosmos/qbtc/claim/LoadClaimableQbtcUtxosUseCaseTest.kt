package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadClaimableQbtcUtxosUseCaseTest {

    private val p2wpkh = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
    private val candidate = ClaimableUtxo(txid = "aa".repeat(32), vout = 0, amount = 100_000)

    @Test
    fun `unsupported btc address blocks before any network call`() = runTest {
        val chain = FakeChainService()
        val utxos = FakeUtxosService(listOf(candidate))
        val result = useCase(chain, utxos).invoke("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")

        assertTrue(result is QbtcClaimLoadResult.Blocked)
        result as QbtcClaimLoadResult.Blocked
        assertTrue(result.reason is QbtcClaimBlockedReason.UnsupportedBtcAddress)
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

    private fun useCase(chain: FakeChainService, utxos: FakeUtxosService) =
        LoadClaimableQbtcUtxosUseCaseImpl(chain, utxos)

    private class FakeChainService(
        private val disabled: Boolean = false,
        private val disabledError: Throwable? = null,
    ) : QbtcClaimChainService {
        var disabledCalls = 0

        override suspend fun isClaimWithProofDisabled(): Boolean {
            disabledCalls++
            disabledError?.let { throw it }
            return disabled
        }

        // Identity filter — the use-case test exercises pipeline branching, not the cross-check.
        override suspend fun filterClaimable(utxos: List<ClaimableUtxo>): List<ClaimableUtxo> =
            utxos
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
