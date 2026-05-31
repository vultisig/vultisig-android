@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QbtcClaimOrchestratorTest {

    private val testPubkeyHex = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
    private val btcAddress = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa" // P2PKH
    private val qbtcAddress = "qbtc1abc"

    private val vault = Vault(id = "v1", name = "Test")
    private val btcCoin =
        Coin.EMPTY.copy(chain = Chain.Bitcoin, address = btcAddress, hexPublicKey = testPubkeyHex)
    private val qbtcCoin = Coin.EMPTY.copy(chain = Chain.Qbtc, address = qbtcAddress)

    private val utxos =
        listOf(
            ClaimableUtxo(txid = "aa".repeat(32), vout = 0, amount = 60_000),
            ClaimableUtxo(txid = "bb".repeat(32), vout = 1, amount = 40_000),
        )

    private val input = QbtcClaimRunInput(vault, btcCoin, qbtcCoin, utxos)

    private val expectedHashes =
        QbtcClaimHashes.computeAll(
            btcAddress = btcAddress,
            compressedPubkey = testPubkeyHex.hexToByteArray(),
            qbtcAddress = qbtcAddress,
            chainId = QbtcClaimConfig.CHAIN_ID,
        )

    private fun matchingProofResponse(txHash: String? = "ab".repeat(32)) =
        ClaimProofResponse(
            proof = "ff00",
            messageHash = expectedHashes.messageHash.toHexString(),
            addressHash = expectedHashes.addressHash.toHexString(),
            qbtcAddressHash = expectedHashes.qbtcAddressHash.toHexString(),
            txHash = txHash,
        )

    @Test
    fun `happy path produces a done result with uppercased tx hash and summed total`() = runTest {
        val runner =
            FakeRoundRunner(QbtcClaimBtcRoundResult(rHex = "01".repeat(24), sHex = "02".repeat(32)))
        val proof = FakeProofService(matchingProofResponse())
        val pusher = FakePusher()
        val orchestrator = QbtcClaimOrchestrator(proof, runner, pusher)

        orchestrator.run(input)

        val phase = orchestrator.phase.value
        assertTrue(phase is QbtcClaimPhase.Done, "expected Done but was $phase")
        phase as QbtcClaimPhase.Done
        assertEquals(("ab".repeat(32)).uppercase(), phase.result.txHashHex)
        assertEquals(100_000, phase.result.totalSatsClaimed)

        // BTC round signed the locally-computed message hash.
        assertEquals(expectedHashes.messageHash.toHexString(), runner.lastInput?.messageHashHex)
        // Proof requested with broadcast and zero-padded signature components.
        assertEquals(true, proof.lastRequest?.broadcast)
        assertEquals(48, proof.lastRequest?.signatureR?.length)
        assertEquals(64, proof.lastRequest?.signatureS?.length)
        // Peer notified with the uppercased hash + total.
        assertEquals(("ab".repeat(32)).uppercase(), pusher.lastTxHash)
        assertEquals(100_000, pusher.lastTotal)
    }

    @Test
    fun `invalid btc public key fails before any network call`() = runTest {
        val runner = FakeRoundRunner(QbtcClaimBtcRoundResult("01".repeat(24), "02".repeat(32)))
        val proof = FakeProofService(matchingProofResponse())
        val orchestrator = QbtcClaimOrchestrator(proof, runner)

        orchestrator.run(input.copy(btcCoin = btcCoin.copy(hexPublicKey = "not-hex")))

        assertEquals(
            QbtcClaimPhase.Failed(QbtcClaimError.INVALID_BTC_PUBLIC_KEY),
            orchestrator.phase.value,
        )
        assertEquals(0, runner.callCount)
        assertEquals(0, proof.callCount)
    }

    @Test
    fun `tampered proof hashes fail with a mismatch`() = runTest {
        val runner = FakeRoundRunner(QbtcClaimBtcRoundResult("01".repeat(24), "02".repeat(32)))
        val proof = FakeProofService(matchingProofResponse().copy(messageHash = "bb".repeat(32)))
        val orchestrator = QbtcClaimOrchestrator(proof, runner)

        orchestrator.run(input)

        assertEquals(
            QbtcClaimPhase.Failed(QbtcClaimError.PROOF_HASH_MISMATCH),
            orchestrator.phase.value,
        )
    }

    @Test
    fun `missing tx hash fails as broadcast unavailable`() = runTest {
        val runner = FakeRoundRunner(QbtcClaimBtcRoundResult("01".repeat(24), "02".repeat(32)))
        val proof = FakeProofService(matchingProofResponse(txHash = null))
        val orchestrator = QbtcClaimOrchestrator(proof, runner)

        orchestrator.run(input)

        assertEquals(
            QbtcClaimPhase.Failed(QbtcClaimError.BROADCAST_UNAVAILABLE),
            orchestrator.phase.value,
        )
    }

    @Test
    fun `a failing btc round surfaces a generic failure`() = runTest {
        val runner = FakeRoundRunner(error = IllegalStateException("relay down"))
        val proof = FakeProofService(matchingProofResponse())
        val orchestrator = QbtcClaimOrchestrator(proof, runner)

        orchestrator.run(input)

        assertEquals(QbtcClaimPhase.Failed(QbtcClaimError.GENERIC), orchestrator.phase.value)
        assertEquals(0, proof.callCount)
    }

    @Test
    fun `reset returns to idle`() = runTest {
        val orchestrator =
            QbtcClaimOrchestrator(
                FakeProofService(matchingProofResponse()),
                FakeRoundRunner(QbtcClaimBtcRoundResult("01".repeat(24), "02".repeat(32))),
            )
        orchestrator.run(input)
        orchestrator.reset()
        assertEquals(QbtcClaimPhase.Idle, orchestrator.phase.value)
    }

    private class FakeRoundRunner(
        private val result: QbtcClaimBtcRoundResult? = null,
        private val error: Throwable? = null,
    ) : QbtcClaimBtcRoundRunner {
        var callCount = 0
        var lastInput: QbtcClaimBtcRoundInput? = null

        override suspend fun run(input: QbtcClaimBtcRoundInput): QbtcClaimBtcRoundResult {
            callCount++
            lastInput = input
            error?.let { throw it }
            return requireNotNull(result)
        }
    }

    private class FakeProofService(private val response: ClaimProofResponse) : QbtcProofService {
        var callCount = 0
        var lastRequest: ClaimProofRequest? = null

        override suspend fun isHealthy(): Boolean = true

        override suspend fun generateProof(request: ClaimProofRequest): ClaimProofResponse {
            callCount++
            lastRequest = request
            return response
        }
    }

    private class FakePusher : QbtcClaimPeerResultPusher {
        var lastTxHash: String? = null
        var lastTotal: Long? = null

        override suspend fun push(txHashHex: String, totalSats: Long) {
            lastTxHash = txHashHex
            lastTotal = totalSats
        }
    }
}
