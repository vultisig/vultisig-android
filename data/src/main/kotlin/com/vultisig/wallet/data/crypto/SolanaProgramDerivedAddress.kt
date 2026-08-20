package com.vultisig.wallet.data.crypto

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Solana's `find_program_address`: the account address a program owns for a given set of seeds.
 *
 * A program address is the first `sha256(seeds ‖ bump ‖ programId ‖ "ProgramDerivedAddress")` that
 * is **not** a point on the ed25519 curve — no private key can exist for it, which is what lets a
 * program sign for it. The bump counts down from 255, so the address is a pure function of the
 * seeds and the program.
 *
 * That is what makes it worth deriving locally rather than reading out of a transaction: an account
 * whose address is fixed by the seeds cannot be substituted, so recomputing it here says *which*
 * position an instruction moves without asking the response to be honest about it.
 *
 * Pure Kotlin and JNI-free on purpose — WalletCore exposes only the associated-token special case,
 * and the checks that use this have to run in plain unit tests.
 */
internal object SolanaProgramDerivedAddress {

    /** The domain separator the runtime appends before hashing. */
    private val PDA_MARKER = "ProgramDerivedAddress".toByteArray(Charsets.US_ASCII)

    /** Solana's own limits: at most 16 seeds, each at most 32 bytes. */
    private const val MAX_SEEDS = 16

    private const val MAX_SEED_LENGTH = 32

    private const val PUBLIC_KEY_LENGTH = 32

    private const val FIRST_BUMP = 255

    /**
     * The program address for [seeds] under [programId], or null when there is none to derive — an
     * unparseable program id, seeds outside the runtime's limits, or (with probability about
     * 2^-256) no off-curve candidate at any bump.
     *
     * Null is a refusal for every caller here rather than a check to skip: an address that could
     * not be derived is one that cannot be compared.
     */
    fun find(seeds: List<ByteArray>, programId: String): String? {
        if (seeds.size > MAX_SEEDS || seeds.any { it.size > MAX_SEED_LENGTH }) return null
        val program =
            Base58Codec.decode(programId)?.takeIf { it.size == PUBLIC_KEY_LENGTH } ?: return null

        for (bump in FIRST_BUMP downTo 0) {
            val digest = MessageDigest.getInstance("SHA-256")
            seeds.forEach(digest::update)
            digest.update(byteArrayOf(bump.toByte()))
            digest.update(program)
            digest.update(PDA_MARKER)
            val candidate = digest.digest()
            if (!isOnCurve(candidate)) return Base58Codec.encode(candidate)
        }
        return null
    }

    /** `BigInteger.TWO` is API 33 and this module ships to minSdk 26. */
    private val TWO = BigInteger.valueOf(2)

    /** 2^255 − 19, the ed25519 field prime. */
    private val FIELD = TWO.pow(255).subtract(BigInteger.valueOf(19))

    /** The curve constant `d = −121665 / 121666`. */
    private val D =
        BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(FIELD))
            .mod(FIELD)

    /** `sqrt(−1)`, the factor that recovers the other root when the first attempt misses. */
    private val SQRT_MINUS_ONE =
        TWO.modPow(FIELD.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), FIELD)

    /**
     * Whether [compressed] decodes to a point on ed25519 — that is, whether it could be a public
     * key someone holds the private half of.
     *
     * The 32 bytes are a little-endian `y` with the sign of `x` in the top bit. A point exists when
     * `x² = (y² − 1) / (d·y² + 1)` has a square root, so this solves for `x` and checks it back.
     */
    private fun isOnCurve(compressed: ByteArray): Boolean {
        val encoded = BigInteger(1, compressed.reversedArray())
        val sign = encoded.testBit(255)
        val y = encoded.clearBit(255)
        if (y >= FIELD) return false

        val ySquared = y.multiply(y).mod(FIELD)
        val numerator = ySquared.subtract(BigInteger.ONE).mod(FIELD)
        val denominator = D.multiply(ySquared).add(BigInteger.ONE).mod(FIELD)
        if (denominator.signum() == 0) return false

        val xSquared = numerator.multiply(denominator.modInverse(FIELD)).mod(FIELD)
        var x =
            xSquared.modPow(FIELD.add(BigInteger.valueOf(3)).divide(BigInteger.valueOf(8)), FIELD)
        if (x.multiply(x).mod(FIELD) != xSquared) {
            x = x.multiply(SQRT_MINUS_ONE).mod(FIELD)
        }
        if (x.multiply(x).mod(FIELD) != xSquared) return false
        // x = 0 has one root, so asking for its negative is an encoding no point answers to.
        return !(x.signum() == 0 && sign)
    }
}
