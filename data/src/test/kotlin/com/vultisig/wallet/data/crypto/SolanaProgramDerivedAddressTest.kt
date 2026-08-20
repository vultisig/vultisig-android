package com.vultisig.wallet.data.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins [SolanaProgramDerivedAddress] against addresses this repo already trusts.
 *
 * The associated token account is the cross-check that matters: WalletCore derives it through JNI
 * and the app compares transactions against what it returns, so reproducing three of them in pure
 * Kotlin says this derivation agrees with the one already in production — including the on-curve
 * test, since two of the three land on a bump below 255 and would come out differently if
 * candidates were never rejected.
 */
class SolanaProgramDerivedAddressTest {

    @Test
    fun `it reproduces the associated token accounts WalletCore derives`() {
        mapOf(
                // The wallet's wrapped-SOL account, which the instrumented preparer test already
                // asserts equals `SolanaAddress(WALLET).defaultTokenAddress(WRAPPED_SOL_MINT)`.
                "So11111111111111111111111111111111111111112" to
                    "GppmkdEmuqNgS7uY5SSN3gXEamJrcPG9197wBdQ37NLc",
                // Its Steakhouse share account: slot 7 of the captured kVault deposit.
                "7D8C5pDFxug58L9zkwK7bCiDg4kD4AygzbcZUmf5usHS" to
                    "GSayQpRaoh1LFdBbja4vensNKDfihcixzCcQShKMCdMJ",
                // And its Allez SOL share account, from the captured SOL deposit.
                "FiM4VQdXXnTXL7GgChryf9zHNG9cmvKECwf34L2y3CkN" to
                    "Hq6N6sNE638VLULNEeAZRTMFmYtsG9ZLLPJYefxwPNWf",
            )
            .forEach { (mint, expected) ->
                val derived =
                    SolanaProgramDerivedAddress.find(
                        seeds =
                            listOf(
                                Base58Codec.decode(WALLET)!!,
                                Base58Codec.decode(TOKEN_PROGRAM)!!,
                                Base58Codec.decode(mint)!!,
                            ),
                        programId = ASSOCIATED_TOKEN_PROGRAM,
                    )
                assertEquals(expected, derived, mint)
            }
    }

    @Test
    fun `the same seeds under a different program give a different address`() {
        val seeds = listOf(Base58Codec.decode(WALLET)!!)
        val underToken = SolanaProgramDerivedAddress.find(seeds, TOKEN_PROGRAM)
        val underAssociatedToken = SolanaProgramDerivedAddress.find(seeds, ASSOCIATED_TOKEN_PROGRAM)

        assertNotNull(underToken)
        assertNotNull(underAssociatedToken)
        assertEquals(false, underToken == underAssociatedToken)
    }

    @Test
    fun `there is nothing to derive from an unreadable program id`() {
        // Null rather than a thrown exception or a plausible-looking address: every caller treats
        // it
        // as a refusal, and an address that could not be derived is one that cannot be compared.
        assertNull(SolanaProgramDerivedAddress.find(listOf(ByteArray(1)), "not an address"))
        assertNull(SolanaProgramDerivedAddress.find(listOf(ByteArray(1)), "1111"))
    }

    @Test
    fun `seeds outside the runtime's limits are refused rather than hashed anyway`() {
        // Solana's own bounds: at most 16 seeds, each at most 32 bytes. Deriving past them would
        // produce an address the runtime never would, which is worse than refusing.
        assertNull(SolanaProgramDerivedAddress.find(List(17) { ByteArray(1) }, TOKEN_PROGRAM))
        assertNull(SolanaProgramDerivedAddress.find(listOf(ByteArray(33)), TOKEN_PROGRAM))
        assertNotNull(SolanaProgramDerivedAddress.find(List(16) { ByteArray(32) }, TOKEN_PROGRAM))
    }

    private companion object {
        const val WALLET = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"
        const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val ASSOCIATED_TOKEN_PROGRAM = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
    }
}
