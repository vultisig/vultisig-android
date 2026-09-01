package com.vultisig.wallet.ui.models.defi

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class DeFiPositionsSnapshotCacheTest {

    private data class Alpha(val value: String)

    private data class Beta(val value: Int)

    private val cache = DeFiPositionsSnapshotCache()

    @Test
    fun `reads back what it was handed for that vault`() {
        cache.write(VAULT_A, Alpha("thor"))

        cache.read(VAULT_A, Alpha::class) shouldBe Alpha("thor")
    }

    @Test
    fun `a vault with no snapshot reads null`() {
        cache.read(VAULT_A, Alpha::class) shouldBe null
    }

    @Test
    fun `vaults do not share a snapshot`() {
        cache.write(VAULT_A, Alpha("thor"))
        cache.write(VAULT_B, Alpha("maya"))

        cache.read(VAULT_A, Alpha::class) shouldBe Alpha("thor")
        cache.read(VAULT_B, Alpha::class) shouldBe Alpha("maya")
    }

    @Test
    fun `two screens on one vault keep separate snapshots`() {
        // The Solana screen runs two view-models against the same vault; neither may overwrite the
        // other's snapshot.
        cache.write(VAULT_A, Alpha("staking"))
        cache.write(VAULT_A, Beta(7))

        cache.read(VAULT_A, Alpha::class) shouldBe Alpha("staking")
        cache.read(VAULT_A, Beta::class) shouldBe Beta(7)
    }

    @Test
    fun `a read can only hand back the type it asked for`() {
        cache.write(VAULT_A, Alpha("thor"))

        cache.read(VAULT_A, Beta::class) shouldBe null
    }

    @Test
    fun `a later write replaces the snapshot`() {
        cache.write(VAULT_A, Alpha("stale"))
        cache.write(VAULT_A, Alpha("fresh"))

        cache.read(VAULT_A, Alpha::class) shouldBe Alpha("fresh")
    }

    private companion object {
        const val VAULT_A = "vault-a"
        const val VAULT_B = "vault-b"
    }
}
