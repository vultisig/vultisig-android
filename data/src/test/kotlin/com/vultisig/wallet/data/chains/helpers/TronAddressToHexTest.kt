package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.chains.helpers.TronFunctions.tronAddressToHex
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

/**
 * The TRC20 fee simulation addresses its recipient by the `41`-prefixed hex the TVM expects, so a
 * decode that silently produced the wrong bytes would price a transfer to somebody else. Pinned
 * against published mainnet addresses rather than against the decoder's own output.
 */
class TronAddressToHexTest {

    @Test
    fun `a mainnet address decodes to its published hex`() {
        // The USDT-TRC20 contract, whose hex form is published in TRON's own token listings.
        assertEquals(
            "0x41a614f803b6fd780986a42c78ec9c7f77e6ded13c",
            tronAddressToHex("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"),
        )
    }

    @Test
    fun `the 41 prefix and all twenty account bytes survive the decode`() {
        assertEquals(
            "0x410102030405060708090a0b0c0d0e0f1011121314",
            tronAddressToHex("TA4Y62o6YC2Zsck9rZVGTvqW1AQ7X9zTnj"),
        )
    }

    @Test
    fun `a mistyped address is rejected instead of decoding to a different account`() {
        // One character off the address above: base58check exists precisely to catch this.
        assertFailsWith<IllegalArgumentException> {
            tronAddressToHex("TA4Y62o6YC2Zsck9rZVGTvqW1AQ7X9zTnk")
        }
    }

    @Test
    fun `a non-base58 character is rejected`() {
        // `0`, `O`, `I` and `l` are excluded from the alphabet to keep look-alikes apart.
        assertFailsWith<IllegalArgumentException> {
            tronAddressToHex("TA4Y62o6YC2Zsck9rZVGTvqW1AQ7X9zTn0")
        }
    }

    @Test
    fun `an address of the wrong length is rejected`() {
        assertFailsWith<IllegalArgumentException> { tronAddressToHex("TA4Y62o6YC2Zsck9") }
        assertFailsWith<IllegalArgumentException> { tronAddressToHex("") }
    }
}
