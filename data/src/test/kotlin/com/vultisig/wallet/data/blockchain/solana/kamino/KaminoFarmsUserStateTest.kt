package com.vultisig.wallet.data.blockchain.solana.kamino

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins [KaminoFarmsUserState] against the addresses the captured deposits actually name.
 *
 * Both fixtures carry this account as a *static* key — not a lookup-table entry — so these two
 * expectations were read off the wire and then reproduced from the farm the registry pins, which is
 * what makes the derivation a check rather than a restatement.
 */
class KaminoFarmsUserStateTest {

    @Test
    fun `it derives the state the captured deposits name`() {
        assertEquals(
            "A1b83WVHAKXeRQAHsdAzJY23ShXCPjqKshzmFFXwGP4Z",
            KaminoFarmsUserState.derive(KaminoVaultRegistry.STEAKHOUSE_USDC.farm, WALLET),
        )
        assertEquals(
            "8ULTfRg47DWt5VBDT7UURTPW6P5Fc5vPMfncfPKpZc3J",
            KaminoFarmsUserState.derive(KaminoVaultRegistry.ALLEZ_SOL.farm, WALLET),
        )
    }

    @Test
    fun `one wallet has a different state in every farm`() {
        // Which is the property the validator leans on: the farm slot is unreadable offline, so
        // this
        // address is what says which farm an instruction moves shares in.
        val states =
            KaminoVaultRegistry.ALLOW_LIST.map { KaminoFarmsUserState.derive(it.farm, WALLET) }
        assertEquals(states.size, states.toSet().size, states.toString())
        assertEquals(false, states.any { it == null })
    }

    @Test
    fun `and every wallet a different state in one farm`() {
        val farm = KaminoVaultRegistry.STEAKHOUSE_USDC.farm
        assertNotEquals(
            KaminoFarmsUserState.derive(farm, WALLET),
            KaminoFarmsUserState.derive(farm, "HDsayqAsDWy3QvANGqh2yNraqcD8Fnjgh73Mhb3WRS5E"),
        )
    }

    @Test
    fun `an address that is not a public key derives nothing`() {
        // Null travels to the validator as a refusal, so a farm or an owner it cannot read stops
        // the
        // transaction rather than skipping the check.
        assertNull(KaminoFarmsUserState.derive("not a farm", WALLET))
        assertNull(KaminoFarmsUserState.derive(KaminoVaultRegistry.STEAKHOUSE_USDC.farm, "nope"))
        assertNull(KaminoFarmsUserState.derive(KaminoVaultRegistry.STEAKHOUSE_USDC.farm, "1111"))
    }

    private companion object {
        /** The wallet both captured deposits were built for. */
        const val WALLET = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"
    }
}
