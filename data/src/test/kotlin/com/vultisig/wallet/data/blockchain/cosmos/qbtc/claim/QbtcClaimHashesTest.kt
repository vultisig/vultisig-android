@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Golden-vector tests pinning [QbtcClaimHashes] byte-for-byte against the SDK
 * (`computeClaimHashes.ts`) and iOS (`QBTCClaimHashes`). The expected hex values are independently
 * computed and hard-coded — any drift in the algorithm, domain tags, or truncation must fail here.
 */
class QbtcClaimHashesTest {

    // secp256k1 generator G — the well-known test compressed pubkey used across platforms.
    private val testPubkey =
        "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798".hexToByteArray()

    @Test
    fun `computeAddressHash returns Hash160 of compressed pubkey for ecdsa`() {
        val result = QbtcClaimHashes.computeAddressHash(testPubkey, QbtcClaimCircuit.ECDSA)
        assertEquals("751e76e8199196d454941c45d1b3a323f1433bd6", result.toHexString())
        assertEquals(20, result.size)
    }

    @Test
    fun `computeAddressHash returns x-only pubkey for schnorr`() {
        val result = QbtcClaimHashes.computeAddressHash(testPubkey, QbtcClaimCircuit.SCHNORR)
        assertEquals(
            "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
            result.toHexString(),
        )
        assertEquals(32, result.size)
    }

    @Test
    fun `computeAddressHash rejects wrong-length pubkey`() {
        assertThrows<IllegalArgumentException> {
            QbtcClaimHashes.computeAddressHash(ByteArray(32), QbtcClaimCircuit.ECDSA)
        }
    }

    @Test
    fun `computeAddressHash rejects bad prefix byte`() {
        val bad = ByteArray(33).also { it[0] = 0x04 }
        assertThrows<IllegalArgumentException> {
            QbtcClaimHashes.computeAddressHash(bad, QbtcClaimCircuit.ECDSA)
        }
    }

    @Test
    fun `computeQbtcAddressHash is sha256 of the utf8 address`() {
        val result = QbtcClaimHashes.computeQbtcAddressHash("qbtc1abc123")
        assertEquals(
            "3f3bae63997e452de9f72bc024928092ba7dcc6898cc177a20e1a2fcedd9f697",
            result.toHexString(),
        )
        assertEquals(32, result.size)
    }

    @Test
    fun `computeChainIdHash is the first 8 bytes of sha256`() {
        val result = QbtcClaimHashes.computeChainIdHash("qbtc-1")
        assertEquals("8e5368622627b0db", result.toHexString())
        assertEquals(8, result.size)
    }

    @Test
    fun `computeClaimMessageHash uses the ecdsa-hash160 domain tags`() {
        val result =
            QbtcClaimHashes.computeClaimMessageHash(
                addressHash = ByteArray(20) { 0xAA.toByte() },
                qbtcAddressHash = ByteArray(32) { 0xBB.toByte() },
                chainIdHash = ByteArray(8) { 0xCC.toByte() },
                circuit = QbtcClaimCircuit.ECDSA,
            )
        assertEquals(
            "47b2bb92333ebbc31930052d1d12d62108478b586a41586deed9c994977ce31b",
            result.toHexString(),
        )
    }

    @Test
    fun `computeClaimMessageHash rejects schnorr circuit`() {
        assertThrows<SchnorrClaimUnsupportedException> {
            QbtcClaimHashes.computeClaimMessageHash(
                addressHash = ByteArray(20),
                qbtcAddressHash = ByteArray(32),
                chainIdHash = ByteArray(8),
                circuit = QbtcClaimCircuit.SCHNORR,
            )
        }
    }

    @Test
    fun `computeClaimMessageHash rejects wrong-length addressHash`() {
        assertThrows<IllegalArgumentException> {
            QbtcClaimHashes.computeClaimMessageHash(
                addressHash = ByteArray(19),
                qbtcAddressHash = ByteArray(32),
                chainIdHash = ByteArray(8),
                circuit = QbtcClaimCircuit.ECDSA,
            )
        }
    }

    @Test
    fun `computeAll for a P2WPKH address produces the pinned message hash`() {
        val result =
            QbtcClaimHashes.computeAll(
                btcAddress = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
                compressedPubkey = testPubkey,
                qbtcAddress = "qbtc1test",
                chainId = "qbtc-1",
            )
        assertEquals(QbtcClaimCircuit.ECDSA, result.circuit)
        assertEquals("751e76e8199196d454941c45d1b3a323f1433bd6", result.addressHash.toHexString())
        assertEquals(
            "505c44c11e540e6b852eff66ffdd778710f15f1647f13b762e95139ec4e9fb1d",
            result.qbtcAddressHash.toHexString(),
        )
        assertEquals(
            "5863656cb1a43002cd7610d85943da22508c4a1eb2929a538c16331bd22c62eb",
            result.messageHash.toHexString(),
        )
    }

    @Test
    fun `computeAll rejects P2TR addresses until a schnorr tag exists`() {
        assertThrows<SchnorrClaimUnsupportedException> {
            QbtcClaimHashes.computeAll(
                btcAddress = "bc1p5d7rjq7g6rdk2yhzks9smlaqtedr4dekq08ge8ztwac72sfr9rusxg3297",
                compressedPubkey = testPubkey,
                qbtcAddress = "qbtc1test",
                chainId = "qbtc-1",
            )
        }
    }
}
