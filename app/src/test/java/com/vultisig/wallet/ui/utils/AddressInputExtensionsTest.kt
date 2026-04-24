package com.vultisig.wallet.ui.utils

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class AddressInputExtensionsTest {

    @Test
    fun `already-trimmed address passes through unchanged`() {
        assertEquals(EVM_ADDRESS, EVM_ADDRESS.asAddressInput())
    }

    @Test
    fun `trailing newline from paste is stripped`() {
        assertEquals(EVM_ADDRESS, "$EVM_ADDRESS\n".asAddressInput())
    }

    @Test
    fun `mixed surrounding whitespace is stripped`() {
        assertEquals(EVM_ADDRESS, " \t\n$EVM_ADDRESS \r\n ".asAddressInput())
    }

    @Test
    fun `only whitespace returns empty string`() {
        assertEquals("", "   \t\n  ".asAddressInput())
    }

    @Test
    fun `StringBuilder is handled via CharSequence dispatch`() {
        val builder = StringBuilder("  $EVM_ADDRESS  ")
        assertEquals(EVM_ADDRESS, builder.asAddressInput())
    }

    @Test
    fun `unicode nonbreaking space is stripped`() {
        val nbsp = ' '
        assertEquals(EVM_ADDRESS, "$nbsp$EVM_ADDRESS$nbsp".asAddressInput())
    }

    @Test
    fun `unicode en quad is stripped`() {
        val enQuad = ' '
        assertEquals(EVM_ADDRESS, "$enQuad$EVM_ADDRESS$enQuad".asAddressInput())
    }

    @Test
    fun `unicode narrow nobreak space is stripped`() {
        val nnbs = ' '
        assertEquals(EVM_ADDRESS, "$nnbs$EVM_ADDRESS$nnbs".asAddressInput())
    }

    @Test
    fun `unicode ideographic space is stripped`() {
        val ideographic = '　'
        assertEquals(EVM_ADDRESS, "$ideographic$EVM_ADDRESS$ideographic".asAddressInput())
    }

    @Test
    fun `tab and carriage return are stripped`() {
        assertEquals(EVM_ADDRESS, "\t$EVM_ADDRESS\r".asAddressInput())
    }

    @Test
    fun `bip21 uri extracts bare btc address`() {
        assertEquals(BTC_ADDRESS, "bitcoin:$BTC_ADDRESS?amount=50".asAddressInput())
    }

    @Test
    fun `bip21 uri without query extracts bare address`() {
        assertEquals(BTC_ADDRESS, "bitcoin:$BTC_ADDRESS".asAddressInput())
    }

    @Test
    fun `eip681 uri extracts bare evm address`() {
        assertEquals(EVM_ADDRESS, "ethereum:$EVM_ADDRESS@1?value=10".asAddressInput())
    }

    @Test
    fun `eip681 uri without chain id extracts bare address`() {
        assertEquals(EVM_ADDRESS, "ethereum:$EVM_ADDRESS?value=10".asAddressInput())
    }

    @Test
    fun `thorname passes through unchanged`() {
        assertEquals("abc.thor", "abc.thor".asAddressInput())
    }

    @Test
    fun `evm checksum case preserved`() {
        assertEquals(EVM_ADDRESS, "ethereum:$EVM_ADDRESS@1".asAddressInput())
    }

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
