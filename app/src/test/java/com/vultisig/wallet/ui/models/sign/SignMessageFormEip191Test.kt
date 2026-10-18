package com.vultisig.wallet.ui.models.sign

import kotlin.test.Test
import kotlin.test.assertEquals

internal class SignMessageFormEip191Test {
    @Test
    fun `personal_sign text is wrapped in EIP-191 envelope`() {
        assertEquals(
            "\u0019Ethereum Signed Message:\n14Hello Vultisig",
            "Hello Vultisig".forMethod("personal_sign"),
        )
    }

    @Test
    fun `personal_sign uses UTF-8 byte length`() {
        assertEquals(
            "\u0019Ethereum Signed Message:\n11h\u00E9llo \uD83D\uDC4B",
            "h\u00E9llo \uD83D\uDC4B".forMethod("personal_sign"),
        )
    }

    @Test
    fun `personal_sign hex input envelopes decoded bytes`() {
        // "\x19Ethereum Signed Message:\n2" + 0x6869
        assertEquals(
            "0x19457468657265756d205369676e6564204d6573736167653a0a326869",
            "0x6869".forMethod("personal_sign"),
        )
    }

    @Test
    fun `other methods are untouched`() {
        assertEquals("hello", "hello".forMethod("eth_sign"))
    }
}
