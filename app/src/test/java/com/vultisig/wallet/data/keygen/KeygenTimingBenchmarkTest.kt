package com.vultisig.wallet.data.keygen

import kotlin.system.measureTimeMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

/**
 * Synthetic timing comparison for the parallel keygen orchestration.
 *
 * Each TSS keygen ceremony (ECDSA, EdDSA, MLDSA, chain-key) is modelled as a suspend function whose
 * wall-clock time is dominated by **relay I/O** — multiple rounds of message exchange between
 * parties via the mediator server. Crypto computation is fast; the wait for inbound messages is
 * slow.
 *
 * Because every ceremony creates its own native Handle and its own TssMessenger, and the relay
 * server already supports `message_id` header filtering, ceremonies are fully independent and can
 * run concurrently.
 *
 * Durations below are scaled **1:10** from observed real-world keygen times so the full benchmark
 * finishes in seconds, not minutes. Multiply reported times by 10 for production estimates.
 *
 * Uses [runBlocking] (real wall-clock), NOT `runTest` (virtual time).
 */
@Tag("benchmark")
@Disabled("Synthetic timing benchmark; enable locally when comparing orchestration changes.")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KeygenTimingBenchmarkTest {

    // Captured between ordered tests for the summary.
    // Safe because PER_CLASS shares a single instance across all test methods.
    private var sequentialDkls = 0L
    private var parallelDkls = 0L
    private var sequentialFull = 0L
    private var parallelFull = 0L
    private var sequentialImport = 0L
    private var parallelImport = 0L

    // -- simulated ceremony -----------------------------------------------

    private data class CeremonyResult(val name: String, val elapsedMs: Long)

    /**
     * Simulates a single TSS keygen ceremony (setup -> message rounds -> finish). The [durationMs]
     * models the network I/O wait that dominates real keygen.
     */
    private suspend fun simulateCeremony(name: String, durationMs: Long): CeremonyResult {
        val elapsed = measureTimeMillis { delay(durationMs) }
        return CeremonyResult(name, elapsed)
    }

    // -- scenario 1: DKLS keygen (standard vault) -------------------------

    @Test
    @Order(1)
    fun `1a - sequential DKLS keygen baseline`() = runBlocking {
        val results = mutableListOf<CeremonyResult>()
        val total = measureTimeMillis {
            results += simulateCeremony("ECDSA", ECDSA_MS)
            results += simulateCeremony("EdDSA", EDDSA_MS)
        }
        printPhases("Sequential DKLS Keygen", results, total)
        sequentialDkls = total
    }

    @Test
    @Order(2)
    fun `1b - parallel DKLS keygen`() = runBlocking {
        val results = mutableListOf<CeremonyResult>()
        val total = measureTimeMillis {
            coroutineScope {
                results +=
                    listOf(
                            async { simulateCeremony("ECDSA", ECDSA_MS) },
                            async { simulateCeremony("EdDSA", EDDSA_MS) },
                        )
                        .awaitAll()
            }
        }
        printPhases("Parallel DKLS Keygen", results, total)
        parallelDkls = total
    }

    // -- scenario 2: full keygen (ECDSA + EdDSA + MLDSA) ------------------

    @Test
    @Order(3)
    fun `2a - sequential full keygen baseline`() = runBlocking {
        val results = mutableListOf<CeremonyResult>()
        val total = measureTimeMillis {
            results += simulateCeremony("ECDSA", ECDSA_MS)
            results += simulateCeremony("EdDSA", EDDSA_MS)
            results += simulateCeremony("MLDSA", MLDSA_MS)
        }
        printPhases("Sequential Full Keygen", results, total)
        sequentialFull = total
    }

    @Test
    @Order(4)
    fun `2b - parallel full keygen`() = runBlocking {
        val results = mutableListOf<CeremonyResult>()
        val total = measureTimeMillis {
            coroutineScope {
                results +=
                    listOf(
                            async { simulateCeremony("ECDSA", ECDSA_MS) },
                            async { simulateCeremony("EdDSA", EDDSA_MS) },
                            async { simulateCeremony("MLDSA", MLDSA_MS) },
                        )
                        .awaitAll()
            }
        }
        printPhases("Parallel Full Keygen", results, total)
        parallelFull = total
    }

    // -- scenario 3: key import (root keys + per-chain keys) ---------------

    @Test
    @Order(5)
    fun `3a - sequential key import baseline`() = runBlocking {
        val results = mutableListOf<CeremonyResult>()
        val total = measureTimeMillis {
            // Phase 1-2: root keys
            results += simulateCeremony("Root ECDSA", ECDSA_MS)
            results += simulateCeremony("Root EdDSA", EDDSA_MS)
            // Phase 3: per-chain keys (one after another)
            for (chain in IMPORT_CHAINS) {
                results += simulateCeremony(chain, CHAIN_KEY_MS)
            }
        }
        printPhases("Sequential Key Import (${IMPORT_CHAINS.size} chains)", results, total)
        sequentialImport = total
    }

    @Test
    @Order(6)
    fun `3b - parallel key import`() = runBlocking {
        val results = mutableListOf<CeremonyResult>()
        val total = measureTimeMillis {
            // All root keys + chain keys are independent — run everything concurrently.
            // Each ceremony uses its own messageId (chain name) for relay routing.
            coroutineScope {
                val rootJobs =
                    listOf(
                        async { simulateCeremony("Root ECDSA", ECDSA_MS) },
                        async { simulateCeremony("Root EdDSA", EDDSA_MS) },
                    )
                val chainJobs =
                    IMPORT_CHAINS.map { chain -> async { simulateCeremony(chain, CHAIN_KEY_MS) } }
                results += (rootJobs + chainJobs).awaitAll()
            }
        }
        printPhases("Parallel Key Import (${IMPORT_CHAINS.size} chains)", results, total)
        parallelImport = total
    }

    // -- summary -----------------------------------------------------------

    @Test
    @Order(7)
    fun `4 - comparison summary`() {
        println()
        println("=".repeat(76))
        println("  KEYGEN TIMING BENCHMARK — SEQUENTIAL vs PARALLEL")
        println("  Durations scaled 1:$SCALE from real-world. Multiply by $SCALE for production.")
        println("=".repeat(76))
        println()
        printRow("Scenario", "Sequential", "Parallel", "Speedup")
        println("-".repeat(76))

        if (sequentialDkls > 0 && parallelDkls > 0) {
            printRow(
                "DKLS (ECDSA + EdDSA)",
                "${sequentialDkls}ms",
                "${parallelDkls}ms",
                speedup(sequentialDkls, parallelDkls),
            )
        }
        if (sequentialFull > 0 && parallelFull > 0) {
            printRow(
                "Full (ECDSA+EdDSA+MLDSA)",
                "${sequentialFull}ms",
                "${parallelFull}ms",
                speedup(sequentialFull, parallelFull),
            )
        }
        if (sequentialImport > 0 && parallelImport > 0) {
            printRow(
                "Key Import (${IMPORT_CHAINS.size} chains)",
                "${sequentialImport}ms",
                "${parallelImport}ms",
                speedup(sequentialImport, parallelImport),
            )
        }

        println("=".repeat(76))
        println()
        println("  WHY THIS WORKS:")
        println("  - Each ceremony has its own native Handle (independent state)")
        println("  - Each ceremony creates its own TssMessenger (independent counter)")
        println("  - Relay server supports message_id header for routing")
        println("  - No data dependency between ECDSA, EdDSA, MLDSA, or chain keys")
        println()
    }

    // -- helpers -----------------------------------------------------------

    private fun printPhases(title: String, results: List<CeremonyResult>, totalMs: Long) {
        println()
        println("  $title — total: ${totalMs}ms")
        for (r in results) {
            println("    %-20s %5dms".format(r.name, r.elapsedMs))
        }
    }

    private fun printRow(col1: String, col2: String, col3: String, col4: String) {
        println("  %-36s %12s %12s %10s".format(col1, col2, col3, col4))
    }

    private fun speedup(seqMs: Long, parMs: Long): String = "%.1fx".format(seqMs.toDouble() / parMs)

    private companion object {
        // Scale factor: test delays = real delays / SCALE
        const val SCALE = 10

        // Scaled durations (real ~5-12s per ceremony -> 500-1200ms in test)
        const val ECDSA_MS = 500L
        const val EDDSA_MS = 400L
        const val MLDSA_MS = 300L
        const val CHAIN_KEY_MS = 350L

        val IMPORT_CHAINS =
            listOf(
                "Bitcoin",
                "Ethereum",
                "Solana",
                "THORChain",
                "MayaChain",
                "Cosmos",
                "Polkadot",
                "Sui",
                "Ton",
                "Optimism",
            )
    }
}
