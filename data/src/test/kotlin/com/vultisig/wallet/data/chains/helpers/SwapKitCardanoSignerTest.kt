@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Tests for [SwapKitCardanoSigner]'s pure (no-JNI) surface: the Blake2b-256 signing digest, the
 * CBOR envelope walker, and the witness-assembly framing. Cardano signs
 * `blake2b_256(cbor(tx_body))` — item 0 of the `[body, witness_set, is_valid, aux_data]` array —
 * and broadcasts the same envelope with item 1 replaced by `{0: [[vkey, sig]]}`. Expected digests
 * are recomputed here with an independent Blake2b so a drift in the walker's body boundary
 * surfaces.
 *
 * Ed25519 signature verification needs the WalletCore JNI and is exercised by the iOS port +
 * integration, mirroring the headless framing tests in [SwapKitSuiSignerTest].
 */
class SwapKitCardanoSignerTest {

    // The vault key is only read in the JNI-dependent signing path, not by digest()/assembly.
    private val signer = SwapKitCardanoSigner(vaultHexPublicKey = "")

    private fun blake2b256(data: ByteArray): ByteArray {
        val d = Blake2bDigest(256)
        d.update(data, 0, data.size)
        return ByteArray(d.digestSize).also { d.doFinal(it, 0) }
    }

    @Test
    fun `digest is blake2b-256 of the transaction body`() {
        // Envelope = array(4) [ body=empty-map(a0), witness_set=a0, is_valid=true(f5), aux=null(f6)
        // ]
        val body = "a0"
        val envelope = "84a0a0f5f6".hexToByteArray()
        val expected = blake2b256(body.hexToByteArray())

        val actual = signer.digest(envelope)

        assertEquals(32, actual.size, "Cardano digest is Blake2b-256")
        assertEquals(expected.toHexString(), actual.toHexString())
    }

    @Test
    fun `walker slices the body across nested arrays, maps, byte strings and wide ints`() {
        // A realistic-shaped body: { 0: [[<32-byte hash>, 0]], 1: [[<addr>, 1_000_000]] }.
        val body =
            "a2" + // map(2)
                "00" + // key 0 (inputs)
                "81" + // array(1)
                "82" + // array(2)
                "5820" +
                "11".repeat(32) + // byte string(32): tx hash
                "00" + // index 0
                "01" + // key 1 (outputs)
                "81" + // array(1)
                "82" + // array(2)
                "4100" + // byte string(1): address
                "1a000f4240" // uint 1_000_000 (4-byte argument)
        val envelope = ("84" + body + "a0" + "f5" + "f6").hexToByteArray()

        // The digest must equal Blake2b-256 over exactly the body bytes — proves the walker found
        // the correct body boundary through the nested structure.
        assertEquals(
            blake2b256(body.hexToByteArray()).toHexString(),
            signer.digest(envelope).toHexString(),
        )
    }

    @Test
    fun `pre-signed image hash is a single entry equal to the digest`() {
        val envelope = "84a0a0f5f6".hexToByteArray()
        val hashes = signer.getPreSignedImageHash(envelope)
        assertEquals(1, hashes.size)
        assertEquals(signer.digest(envelope).toHexString(), hashes[0])
    }

    @Test
    fun `assembly splices the vkey witness and re-emits items 0-2-3 verbatim`() {
        val envelope = "84a0a0f5f6".hexToByteArray()
        val vkey = ByteArray(32) { 0xAB.toByte() }
        val sig = ByteArray(64) { 0xCD.toByte() }

        // witness_set = { 0: [[vkey, sig]] } = a1 00 81 82 5820<vkey> 5840<sig>
        // envelope     = 84 <body=a0> <witness> <is_valid=f5> <aux=f6>
        val expected =
            "84a0" + "a1008182" + "5820" + "ab".repeat(32) + "5840" + "cd".repeat(64) + "f5f6"

        val assembled = signer.assembleSignedTransaction(envelope, sig, vkey)
        assertEquals(expected, assembled.toHexString())
    }

    @Test
    fun `empty payload is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { signer.digest(ByteArray(0)) }
    }

    @Test
    fun `non-array top-level header is rejected`() {
        // Leading 0xa0 (map) is not the expected array(4) envelope header.
        assertThrows(SwapKitCardanoSignerException::class.java) {
            signer.digest("a0".hexToByteArray())
        }
    }

    @Test
    fun `trailing bytes after the envelope are rejected`() {
        assertThrows(SwapKitCardanoSignerException::class.java) {
            signer.digest("84a0a0f5f600".hexToByteArray())
        }
    }
}
