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
    fun `unicode_nonbreaking_space_is_stripped`() {
        val nbsp = '\u00A0'
        assertEquals(EVM_ADDRESS, "$nbsp$EVM_ADDRESS$nbsp".asAddressInput())
    }

    @Test
    fun `unicode_en_quad_is_stripped`() {
        val enQuad = '\u2000'
        assertEquals(EVM_ADDRESS, "$enQuad$EVM_ADDRESS$enQuad".asAddressInput())
    }

    @Test
    fun `unicode_narrow_nobreak_space_is_stripped`() {
        val nnbs = '\u202F'
        assertEquals(EVM_ADDRESS, "$nnbs$EVM_ADDRESS$nnbs".asAddressInput())
    }

    @Test
    fun `unicode_ideographic_space_is_stripped`() {
        val ideographic = '\u3000'
        assertEquals(EVM_ADDRESS, "$ideographic$EVM_ADDRESS$ideographic".asAddressInput())
    }

    @Test
    fun `tab_and_carriage_return_are_stripped`() {
        assertEquals(EVM_ADDRESS, "\t$EVM_ADDRESS\r".asAddressInput())
    }

    @Test
    fun `bip21_uri_extracts_bare_btc_address`() {
        assertEquals(BTC_ADDRESS, "bitcoin:$BTC_ADDRESS?amount=50".asAddressInput())
    }

    @Test
    fun `bip21_uri_without_query_extracts_bare_address`() {
        assertEquals(BTC_ADDRESS, "bitcoin:$BTC_ADDRESS".asAddressInput())
    }

    @Test
    fun `eip681_uri_extracts_bare_evm_address`() {
        assertEquals(EVM_ADDRESS, "ethereum:$EVM_ADDRESS@1?value=10".asAddressInput())
    }

    @Test
    fun `eip681_uri_without_chain_id_extracts_bare_address`() {
        assertEquals(EVM_ADDRESS, "ethereum:$EVM_ADDRESS?value=10".asAddressInput())
    }

    @Test
    fun `thorname_passes_through_unchanged`() {
        assertEquals("abc.thor", "abc.thor".asAddressInput())
    }

    @Test
    fun `evm_checksum_case_preserved`() {
        assertEquals(EVM_ADDRESS, EVM_ADDRESS.asAddressInput())
    }

    @Test
    fun `whitespace_only_returns_empty`() {
        val ideographic = '\u3000'
        assertEquals("", "   $ideographic\t\n".asAddressInput())
    }

    companion object {
        private const val EVM_ADDRESS = "0xAf6a0cB4bA76B4720D345d09dCdB58B9a2570982"
        private const val BTC_ADDRESS = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
    }
}
