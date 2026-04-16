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

    companion object {
        private const val EVM_ADDRESS = "0xAf6a0cB4bA76B4720D345d09dCdB58B9a2570982"
    }
}
