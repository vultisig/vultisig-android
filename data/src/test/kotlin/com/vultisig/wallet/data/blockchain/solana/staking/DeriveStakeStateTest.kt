package com.vultisig.wallet.data.blockchain.solana.staking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [SolanaStakingService.deriveStakeState] — the pure lifecycle resolver that gates withdraw /
 * finish-move availability. Covers the `u64::MAX` "not deactivating" sentinel (arrives as a null
 * deactivationEpoch), the `currentEpoch == null` fallback, and the deactivation-before-activation
 * ordering.
 */
class DeriveStakeStateTest {

    private fun state(
        hasDelegation: Boolean = true,
        activationEpoch: Long? = null,
        deactivationEpoch: Long? = null,
        currentEpoch: Long? = null,
    ): SolanaStakeState =
        SolanaStakingService.deriveStakeState(
            hasDelegation = hasDelegation,
            activationEpoch = activationEpoch,
            deactivationEpoch = deactivationEpoch,
            currentEpoch = currentEpoch,
        )

    @Test
    fun `no delegation is NotDelegated regardless of epochs`() {
        assertEquals(
            SolanaStakeState.NotDelegated,
            state(hasDelegation = false, activationEpoch = 10, currentEpoch = 20),
        )
    }

    @Test
    fun `null current epoch with deactivation requested is Deactivating`() {
        assertEquals(
            SolanaStakeState.Deactivating,
            state(deactivationEpoch = 100, currentEpoch = null),
        )
    }

    @Test
    fun `null current epoch without deactivation is Active`() {
        assertEquals(SolanaStakeState.Active, state(activationEpoch = 100, currentEpoch = null))
    }

    @Test
    fun `deactivation epoch fully in the past is Inactive`() {
        assertEquals(
            SolanaStakeState.Inactive,
            state(activationEpoch = 998, deactivationEpoch = 1000, currentEpoch = 1001),
        )
    }

    @Test
    fun `current epoch equal to deactivation epoch is still Deactivating (cooling down)`() {
        assertEquals(
            SolanaStakeState.Deactivating,
            state(activationEpoch = 998, deactivationEpoch = 1000, currentEpoch = 1000),
        )
    }

    @Test
    fun `deactivation requested in a future epoch is Deactivating`() {
        assertEquals(
            SolanaStakeState.Deactivating,
            state(activationEpoch = 998, deactivationEpoch = 1000, currentEpoch = 999),
        )
    }

    @Test
    fun `active delegation past its activation epoch is Active`() {
        assertEquals(SolanaStakeState.Active, state(activationEpoch = 998, currentEpoch = 1000))
    }

    @Test
    fun `current epoch equal to activation epoch is still Activating (warming up)`() {
        assertEquals(
            SolanaStakeState.Activating,
            state(activationEpoch = 1000, currentEpoch = 1000),
        )
    }

    @Test
    fun `delegation with no activation epoch is Activating`() {
        assertEquals(
            SolanaStakeState.Activating,
            state(activationEpoch = null, currentEpoch = 1000),
        )
    }

    @Test
    fun `u64-MAX deactivation sentinel (null) with a live delegation resolves to Active not Deactivating`() {
        // On-chain deactivationEpoch = u64::MAX overflows Long -> toLongOrNull() == null, i.e. "not
        // deactivating". Such an account must read Active, never Deactivating.
        assertEquals(
            SolanaStakeState.Active,
            state(activationEpoch = 998, deactivationEpoch = null, currentEpoch = 1000),
        )
    }
}
