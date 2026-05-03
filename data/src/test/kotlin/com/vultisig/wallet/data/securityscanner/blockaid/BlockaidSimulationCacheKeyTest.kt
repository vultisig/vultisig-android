package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.KeysignPayload
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SignSolana

internal class BlockaidSimulationCacheKeyTest {

    @Test
    fun `evm key normalises memo case`() {
        val payload = evmPayload(memo = "0xABCDEF", toAddress = "0xRecipient")

        val key = BlockaidSimulationCacheKey.from(payload) as BlockaidSimulationCacheKey.Evm

        assertEquals("0xabcdef", key.normalizedMemo)
        assertEquals("0xrecipient", key.normalizedTo)
        assertEquals("0xfrom", key.normalizedFrom)
        assertEquals(Chain.Ethereum, key.chain)
    }

    @Test
    fun `evm key differs when signer differs but everything else matches`() {
        val a = evmPayload(memo = "0xfeed", toAddress = "0xRouter", from = "0xVaultA")
        val b = evmPayload(memo = "0xfeed", toAddress = "0xRouter", from = "0xVaultB")

        // Two vaults running the same dApp call against the same router must not share a cache
        // entry. The cache key includes the signer so that on a multi-vault device, switching
        // vaults does not surface a stale verdict from the previous one.
        assertNotEquals(BlockaidSimulationCacheKey.from(a), BlockaidSimulationCacheKey.from(b))
    }

    @Test
    fun `evm key differs when to-address differs but memo matches`() {
        val a = evmPayload(memo = "0xfeed", toAddress = "0xRouterA")
        val b = evmPayload(memo = "0xfeed", toAddress = "0xRouterB")

        // Same calldata against different routers must NOT collide — the
        // simulation result depends on the target contract, so caching them
        // under one key would surface stale data on the second call.
        assertNotEquals(BlockaidSimulationCacheKey.from(a), BlockaidSimulationCacheKey.from(b))
    }

    @Test
    fun `evm key differs when native value differs but calldata matches`() {
        // payable contract calls with identical calldata can return different Blockaid verdicts
        // depending on msg.value. Without value in the cache key, the second call would surface
        // a stale verdict from the first.
        val a = evmPayload(memo = "0xfeed", toAddress = "0xRouter", value = BigInteger.ZERO)
        val b = evmPayload(memo = "0xfeed", toAddress = "0xRouter", value = BigInteger.ONE)

        assertNotEquals(BlockaidSimulationCacheKey.from(a), BlockaidSimulationCacheKey.from(b))
    }

    @Test
    fun `evm key is null for empty calldata`() {
        assertNull(BlockaidSimulationCacheKey.from(evmPayload(memo = "0x")))
        assertNull(BlockaidSimulationCacheKey.from(evmPayload(memo = null)))
        assertNull(BlockaidSimulationCacheKey.from(evmPayload(memo = "abcdef"))) // missing prefix
    }

    @Test
    fun `solana key digests raw transactions to a fixed-size hex hash`() {
        val payload = solanaPayload(rawTransactions = listOf("AAA==", "BBB=="))

        val key = BlockaidSimulationCacheKey.from(payload) as BlockaidSimulationCacheKey.Solana

        // SHA-256 hex digest is always 64 characters; any future regression
        // that drops back to a raw concatenation would break this length
        // invariant immediately.
        assertEquals(64, key.transactionsDigest.length)
        assertTrue(key.transactionsDigest.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `solana keys are stable across calls for the same input`() {
        val a = solanaPayload(rawTransactions = listOf("AAA==", "BBB=="))
        val b = solanaPayload(rawTransactions = listOf("AAA==", "BBB=="))

        assertEquals(BlockaidSimulationCacheKey.from(a), BlockaidSimulationCacheKey.from(b))
    }

    @Test
    fun `solana keys differ when raw transactions differ`() {
        val a = solanaPayload(rawTransactions = listOf("AAA==", "BBB=="))
        val b = solanaPayload(rawTransactions = listOf("AAA==", "CCC=="))

        assertNotEquals(BlockaidSimulationCacheKey.from(a), BlockaidSimulationCacheKey.from(b))
    }

    @Test
    fun `solana keys differ when raw transactions are reordered`() {
        // Solana batches are order-sensitive; reordering transactions produces a different
        // logical message, so it MUST produce a different cache key. A future regression that
        // sorted or re-ordered the digest input would fail this test.
        val a = solanaPayload(rawTransactions = listOf("AAA==", "BBB=="))
        val b = solanaPayload(rawTransactions = listOf("BBB==", "AAA=="))

        assertNotEquals(BlockaidSimulationCacheKey.from(a), BlockaidSimulationCacheKey.from(b))
    }

    @Test
    fun `solana keys differ when signer differs`() {
        val a = solanaPayload(rawTransactions = listOf("AAA=="), signer = "VaultA")
        val b = solanaPayload(rawTransactions = listOf("AAA=="), signer = "VaultB")

        assertNotEquals(BlockaidSimulationCacheKey.from(a), BlockaidSimulationCacheKey.from(b))
    }

    @Test
    fun `solana digest is 64 hex chars regardless of high-bit bytes in the SHA-256 output`() {
        // Defends against a regression where Byte sign-extension in `%02x`.format(byte)
        // would emit 8 hex chars for any byte with the high bit set, breaking the
        // fixed-size invariant. Iterates many candidate inputs to cover the high-bit
        // probability space.
        repeat(64) { i ->
            val payload = solanaPayload(rawTransactions = listOf("seed-$i"))
            val key = BlockaidSimulationCacheKey.from(payload) as BlockaidSimulationCacheKey.Solana
            assertEquals(64, key.transactionsDigest.length, "iteration $i")
            assertTrue(
                key.transactionsDigest.all { it in '0'..'9' || it in 'a'..'f' },
                "iteration $i",
            )
        }
    }

    @Test
    fun `evm key lowercases EIP-55 checksummed to-address`() {
        // Defensive regression test: a real-world checksummed Ethereum address
        // must collapse to the lowercase form so two calls with different
        // displayed casing share the same cache entry.
        val payload =
            evmPayload(
                memo = "0xa9059cbb",
                toAddress = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B",
            )

        val key = BlockaidSimulationCacheKey.from(payload) as BlockaidSimulationCacheKey.Evm

        assertEquals("0xab5801a7d398351b8be11c439e05c5b3259aec9b", key.normalizedTo)
    }

    @Test
    fun `solana key is null for empty raw transactions`() {
        val payload = solanaPayload(rawTransactions = emptyList())

        assertNull(BlockaidSimulationCacheKey.from(payload))
    }

    @Test
    fun `unsupported chain returns null`() {
        val coin =
            mockk<Coin>(relaxed = true) {
                every { chain } returns Chain.Bitcoin
                every { address } returns "bc1q..."
            }
        val payload =
            mockk<KeysignPayload>(relaxed = true) {
                every { this@mockk.coin } returns coin
                every { memo } returns "irrelevant"
                every { signSolana } returns null
            }

        assertNull(BlockaidSimulationCacheKey.from(payload))
    }

    // ---------- helpers ----------------------------------------------------

    private fun evmPayload(
        memo: String?,
        toAddress: String = "0xto",
        from: String = "0xfrom",
        value: BigInteger = BigInteger.ZERO,
    ): KeysignPayload {
        val coin =
            mockk<Coin>(relaxed = true) {
                every { chain } returns Chain.Ethereum
                every { address } returns from
            }
        return mockk<KeysignPayload>(relaxed = true) {
            every { this@mockk.coin } returns coin
            every { this@mockk.memo } returns memo
            every { this@mockk.toAddress } returns toAddress
            every { toAmount } returns value
            every { signSolana } returns null
        }
    }

    private fun solanaPayload(
        rawTransactions: List<String>,
        signer: String = "Sol1...",
    ): KeysignPayload {
        val coin =
            mockk<Coin>(relaxed = true) {
                every { chain } returns Chain.Solana
                every { address } returns signer
            }
        val solana = SignSolana(rawTransactions = rawTransactions)
        return mockk<KeysignPayload>(relaxed = true) {
            every { this@mockk.coin } returns coin
            every { memo } returns null
            every { signSolana } returns solana
        }
    }
}
