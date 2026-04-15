package com.vultisig.wallet.ui.utils

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class AddressInputExtensionsTest {

    @Test
    fun `already-trimmed address passes through unchanged`() {
        assertEquals(EVM_ADDRESS, EVM_ADDRESS.asAddressInput())
    }

    @Test
    fun `leading spaces are stripped`() {
        assertEquals(EVM_ADDRESS, "   $EVM_ADDRESS".asAddressInput())
    }

    @Test
    fun `trailing spaces are stripped`() {
        assertEquals(EVM_ADDRESS, "$EVM_ADDRESS   ".asAddressInput())
    }

    @Test
    fun `surrounding spaces are stripped`() {
        assertEquals(EVM_ADDRESS, "  $EVM_ADDRESS  ".asAddressInput())
    }

    @Test
    fun `trailing newline from paste is stripped`() {
        assertEquals(EVM_ADDRESS, "$EVM_ADDRESS\n".asAddressInput())
    }

    @Test
    fun `CRLF from clipboard is stripped`() {
        assertEquals(EVM_ADDRESS, "$EVM_ADDRESS\r\n".asAddressInput())
    }

    @Test
    fun `tabs are stripped`() {
        assertEquals(EVM_ADDRESS, "\t$EVM_ADDRESS\t".asAddressInput())
    }

    @Test
    fun `mixed surrounding whitespace is stripped`() {
        assertEquals(EVM_ADDRESS, " \t\n$EVM_ADDRESS \r\n ".asAddressInput())
    }

    @Test
    fun `internal characters are preserved`() {
        val cosmosStyle = "cosmos1abc def"
        assertEquals(cosmosStyle, cosmosStyle.asAddressInput())
    }

    @Test
    fun `empty input returns empty string`() {
        assertEquals("", "".asAddressInput())
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
    fun `non-whitespace unicode is preserved`() {
        // ENS-style name, which will later be resolved — must not be mutated.
        val ensName = "vitalik.eth"
        assertEquals(ensName, "  $ensName  ".asAddressInput())
    }

    @Test
    fun `thorname with colon is preserved`() {
        val thorname = "rk:THOR"
        assertEquals(thorname, " $thorname ".asAddressInput())
    }

    companion object {
        private const val EVM_ADDRESS = "0xAf6a0cB4bA76B4720D345d09dCdB58B9a2570982"
    }
}
