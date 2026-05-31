@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Tests for [SwapKitSuiSigner]'s pure (no-JNI) surface: the Blake2b-32 signing digest and the
 * single-entry pre-signed hash. Sui's digest is `blake2b_32([0x00,0x00,0x00] || ptb)` — the intent
 * prefix (scope/version/app) prepended to SwapKit's BCS-serialized PTB. The expected value is
 * recomputed here with an independent Blake2b so a drift in the prefix bytes or hashing surfaces.
 *
 * Ed25519 signature verification + envelope assembly need the WalletCore JNI and are exercised by
 * the iOS port + integration, mirroring the headless framing tests in [SwapKitBtcSignerTest].
 */
class SwapKitSuiSignerTest {

    // The vault key is only read in the JNI-dependent signing path, not by digest().
    private val signer = SwapKitSuiSigner(vaultHexPublicKey = "")

    private fun blake2b32(data: ByteArray): ByteArray {
        val d = Blake2bDigest(256)
        d.update(data, 0, data.size)
        return ByteArray(d.digestSize).also { d.doFinal(it, 0) }
    }

    @Test
    fun `digest is blake2b-32 of the intent-prefixed PTB`() {
        val ptb = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val expected = blake2b32(byteArrayOf(0x00, 0x00, 0x00) + ptb)

        val actual = signer.digest(ptb)

        assertEquals(32, actual.size, "Sui digest is Blake2b-32")
        assertArrayEquals(expected, actual)
    }

    @Test
    fun `digest depends on the intent prefix`() {
        val ptb = byteArrayOf(0x0a, 0x0b, 0x0c)
        // Hashing the bare PTB (no intent prefix) must NOT match — the prefix is part of the signed
        // message, so dropping it would produce a signature the Sui RPC rejects.
        assertNotEquals(blake2b32(ptb).toHexString(), signer.digest(ptb).toHexString())
    }

    @Test
    fun `pre-signed image hash is a single entry equal to the digest`() {
        val ptb = byteArrayOf(0x11, 0x22, 0x33)
        val hashes = signer.getPreSignedImageHash(ptb)
        assertEquals(1, hashes.size)
        assertEquals(signer.digest(ptb).toHexString(), hashes[0])
    }

    @Test
    fun `empty payload is rejected`() {
        assertThrows(SwapKitSuiSignerException::class.java) { signer.digest(ByteArray(0)) }
    }
}
