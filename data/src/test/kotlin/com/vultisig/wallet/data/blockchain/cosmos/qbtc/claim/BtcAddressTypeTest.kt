package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BtcAddressTypeTest {

    @Test
    fun `detects address types from prefix and length`() {
        assertEquals(
            BtcAddressType.P2PKH,
            BtcAddressType.detect("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"),
        )
        assertEquals(
            BtcAddressType.P2SH_P2WPKH,
            BtcAddressType.detect("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"),
        )
        assertEquals(
            BtcAddressType.P2WPKH,
            BtcAddressType.detect("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"),
        )
        assertEquals(
            BtcAddressType.P2WSH,
            BtcAddressType.detect("bc1qft5p2uhsdcdc3l2ua4ap5qqfg4pjaqlp250x7us7a8qqhrxrxfsqseac85"),
        )
        assertEquals(
            BtcAddressType.P2TR,
            BtcAddressType.detect("bc1p5d7rjq7g6rdk2yhzks9smlaqtedr4dekq08ge8ztwac72sfr9rusxg3297"),
        )
    }

    @Test
    fun `ecdsa circuit for all non-taproot types, schnorr for taproot`() {
        assertEquals(QbtcClaimCircuit.ECDSA, BtcAddressType.P2PKH.circuit)
        assertEquals(QbtcClaimCircuit.ECDSA, BtcAddressType.P2WPKH.circuit)
        assertEquals(QbtcClaimCircuit.ECDSA, BtcAddressType.P2SH_P2WPKH.circuit)
        assertEquals(QbtcClaimCircuit.ECDSA, BtcAddressType.P2WSH.circuit)
        assertEquals(QbtcClaimCircuit.SCHNORR, BtcAddressType.P2TR.circuit)
    }

    @Test
    fun `rejects testnet addresses`() {
        assertThrows<UnsupportedBtcAddressException> {
            BtcAddressType.detect("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx")
        }
    }

    @Test
    fun `rejects unknown and malformed addresses`() {
        assertThrows<UnsupportedBtcAddressException> { BtcAddressType.detect("xyz123") }
        assertThrows<UnsupportedBtcAddressException> { BtcAddressType.detect("bc1pshort") }
        assertThrows<UnsupportedBtcAddressException> {
            BtcAddressType.detect("bc1qrp33g0q5b5698ahp5jnf017nmnzs75rz9eeee5946d7jjk37n4w4qschxhz")
        }
    }
}
