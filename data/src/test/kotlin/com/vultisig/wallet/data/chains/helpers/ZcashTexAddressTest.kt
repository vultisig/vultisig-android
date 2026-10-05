package com.vultisig.wallet.data.chains.helpers

import kotlin.test.Test
import kotlin.test.assertEquals

class ZcashTexAddressTest {
    // Live THORChain ZEC inbound (same vector as iOS ZcashZip317PlanTests).
    private val tex = "tex1z8e8k9jg5xh28ny8ctek2dwpnc6qd9qd037yju"

    @Test
    fun `TEX vault address maps to its transparent address`() {
        assertEquals("t1KWVyTZA6DPBDrHPERjuHSFWmAioomt5mE", ZcashTexAddress.toTransparent(tex))
        assertEquals(
            "t1KWVyTZA6DPBDrHPERjuHSFWmAioomt5mE",
            ZcashTexAddress.toTransparent(tex.uppercase()),
        )
    }

    @Test
    fun `corrupted or non-TEX address is returned unchanged`() {
        val bad = tex.dropLast(1) + "q"
        assertEquals(bad, ZcashTexAddress.toTransparent(bad))
        val t = "t1PoLLLwEcVhqMBhk53tANtSepnPXAQJkPM"
        assertEquals(t, ZcashTexAddress.toTransparent(t))
    }
}
