package com.vultisig.wallet.ui.utils

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/** Tests for [CharSequence.asAddressInput]. */
internal class AddressInputExtensionsTest {

    /** Verifies a clean address passes through with no modification. */
    @Test
    fun `already-trimmed address passes through unchanged`() {
        assertEquals(EVM_ADDRESS, EVM_ADDRESS.asAddressInput())
    }

    /** Verifies a trailing newline appended during paste is removed. */
    @Test
    fun `trailing newline from paste is stripped`() {
        assertEquals(EVM_ADDRESS, "$EVM_ADDRESS\n".asAddressInput())
    }

    /** Verifies spaces, tabs, and newlines surrounding the address are all removed. */
    @Test
    fun `mixed surrounding whitespace is stripped`() {
        assertEquals(EVM_ADDRESS, " \t\n$EVM_ADDRESS \r\n ".asAddressInput())
    }

    /** Verifies that a string containing only whitespace collapses to an empty string. */
    @Test
    fun `only whitespace returns empty string`() {
        assertEquals("", "   \t\n  ".asAddressInput())
    }

    /** Verifies the extension dispatches correctly through the [CharSequence] receiver. */
    @Test
    fun `StringBuilder is handled via CharSequence dispatch`() {
        val builder = StringBuilder("  $EVM_ADDRESS  ")
        assertEquals(EVM_ADDRESS, builder.asAddressInput())
    }

    /** Verifies U+00A0 NO-BREAK SPACE is recognised and stripped. */
    @Test
    fun `unicode nonbreaking space is stripped`() {
        val nbsp = ' '
        assertEquals(EVM_ADDRESS, "$nbsp$EVM_ADDRESS$nbsp".asAddressInput())
    }

    /** Verifies U+2000 EN QUAD is recognised and stripped. */
    @Test
    fun `unicode en quad is stripped`() {
        val enQuad = ' '
        assertEquals(EVM_ADDRESS, "$enQuad$EVM_ADDRESS$enQuad".asAddressInput())
    }

    /** Verifies U+202F NARROW NO-BREAK SPACE is recognised and stripped. */
    @Test
    fun `unicode narrow nobreak space is stripped`() {
        val nnbs = ' '
        assertEquals(EVM_ADDRESS, "$nnbs$EVM_ADDRESS$nnbs".asAddressInput())
    }

    /** Verifies U+3000 IDEOGRAPHIC SPACE is recognised and stripped. */
    @Test
    fun `unicode ideographic space is stripped`() {
        val ideographic = '　'
        assertEquals(EVM_ADDRESS, "$ideographic$EVM_ADDRESS$ideographic".asAddressInput())
    }

    /** Verifies ASCII tab and carriage-return characters are stripped. */
    @Test
    fun `tab and carriage return are stripped`() {
        assertEquals(EVM_ADDRESS, "\t$EVM_ADDRESS\r".asAddressInput())
    }

    /** Verifies a BIP-21 URI with query parameters yields only the bare address. */
    @Test
    fun `bip21 uri extracts bare btc address`() {
        assertEquals(BTC_ADDRESS, "bitcoin:$BTC_ADDRESS?amount=50".asAddressInput())
    }

    /** Verifies a BIP-21 URI without query parameters yields only the bare address. */
    @Test
    fun `bip21 uri without query extracts bare address`() {
        assertEquals(BTC_ADDRESS, "bitcoin:$BTC_ADDRESS".asAddressInput())
    }

    /** Verifies an EIP-681 URI with chain-id and query parameters yields only the bare address. */
    @Test
    fun `eip681 uri extracts bare evm address`() {
        assertEquals(EVM_ADDRESS, "ethereum:$EVM_ADDRESS@1?value=10".asAddressInput())
    }

    /** Verifies an EIP-681 URI without a chain-id yields only the bare address. */
    @Test
    fun `eip681 uri without chain id extracts bare address`() {
        assertEquals(EVM_ADDRESS, "ethereum:$EVM_ADDRESS?value=10".asAddressInput())
    }

    /** Verifies that a non-URI identifier like a THORName is not modified. */
    @Test
    fun `thorname passes through unchanged`() {
        assertEquals("abc.thor", "abc.thor".asAddressInput())
    }

    /** Verifies that mixed-case EVM checksum addresses are not lowercased or altered. */
    @Test
    fun `evm checksum case preserved`() {
        assertEquals(EVM_ADDRESS, "ethereum:$EVM_ADDRESS@1".asAddressInput())
    }

    /** Verifies that a mix of Unicode and ASCII whitespace-only input collapses to empty. */
    @Test
    fun `whitespace only returns empty`() {
        val ideographic = '　'
        assertEquals("", "   $ideographic\t\n".asAddressInput())
    }

    companion object {
        private const val EVM_ADDRESS = "0xAf6a0cB4bA76B4720D345d09dCdB58B9a2570982"
        private const val BTC_ADDRESS = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
    }
}
