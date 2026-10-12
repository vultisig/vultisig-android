package com.vultisig.wallet.data.chains.helpers

import kotlin.test.Test
import kotlin.test.assertEquals

/** Vectors shared with iOS `ZcashZip317PlanTests` (vultisig-ios#5530). */
class ZcashTexAddressTest {
    private val transparent = "t1PoLLLwEcVhqMBhk53tANtSepnPXAQJkPM"

    // The second is the live THORChain ZEC inbound.
    private val vectors =
        listOf(
            "tex1zclnr35llscdedzrwdmemm70es05ngg9m2d3lv" to "t1KuEaP8Lb2pUGtpciBCT2ppomGvFi8sEPY",
            "tex1z8e8k9jg5xh28ny8ctek2dwpnc6qd9qd037yju" to "t1KWVyTZA6DPBDrHPERjuHSFWmAioomt5mE",
            "tex1h55z0mdpnaxjxqs39sht9659ztnjk32reer52v" to "t1b7mpgEQnHCbYd34qLVwCTHp4mEKnyennf",
        )

    @Test
    fun `TEX addresses map to the expected transparent address in either case`() {
        vectors.forEach { (tex, expected) ->
            assertEquals(expected, ZcashTexAddress.toTransparent(tex), tex)
            assertEquals(expected, ZcashTexAddress.toTransparent(tex.uppercase()), "upper $tex")
        }
    }

    @Test
    fun `mixed-case TEX address is returned unchanged`() {
        val mixed = "Tex1zclnr35llscdedzrwdmemm70es05ngg9m2d3lv"
        assertEquals(mixed, ZcashTexAddress.toTransparent(mixed))
    }

    @Test
    fun `corrupted or non-TEX address is returned unchanged`() {
        val bad = "tex1z8e8k9jg5xh28ny8ctek2dwpnc6qd9qd037yjq"
        assertEquals(bad, ZcashTexAddress.toTransparent(bad))
        assertEquals(transparent, ZcashTexAddress.toTransparent(transparent))
    }
}
