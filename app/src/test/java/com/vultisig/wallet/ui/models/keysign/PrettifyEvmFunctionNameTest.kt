package com.vultisig.wallet.ui.models.keysign

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PrettifyEvmFunctionNameTest {

    @Test
    fun `camelCase signature is split and title-cased`() {
        assertEquals(
            "Supply With Permit",
            prettifyEvmFunctionName("supplyWithPermit(address,uint256)"),
        )
    }

    @Test
    fun `acronym boundary keeps leading uppercase block intact`() {
        // The boundary regex splits on `lowercase→Uppercase` or `Uppercase→Uppercase[lowercase]`
        // so an all-caps acronym followed by a TitleCase word gets one space inserted.
        assertEquals("WBTC Swap", prettifyEvmFunctionName("WBTCSwap()"))
    }

    @Test
    fun `digit-prefixed names are intentionally left unsplit`() {
        // The boundary regex does not split on digit/letter transitions. The KDoc explicitly
        // calls this out: splitting `ERC20Approve` into `ERC 20 Approve` reads worse for the
        // long tail of 4byte selectors. This test locks in that intentional design choice so a
        // future regex tweak doesn't silently regress the look of the most common selectors.
        assertEquals("ERC20Approve", prettifyEvmFunctionName("ERC20Approve(address,uint256)"))
    }

    @Test
    fun `single lowercase function name is title-cased`() {
        assertEquals("Approve", prettifyEvmFunctionName("approve(address,uint256)"))
        assertEquals("Transfer", prettifyEvmFunctionName("transfer(address,uint256)"))
    }

    @Test
    fun `signature without parens returns null`() {
        // 4byte responses always include the argument list, so a signature missing `(` is
        // malformed and rejected up front. Returning null keeps the hero out of an indeterminate
        // state.
        assertNull(prettifyEvmFunctionName(""))
        assertNull(prettifyEvmFunctionName("transfer"))
    }

    @Test
    fun `bidi override codepoint is stripped before splitting`() {
        // U+202E (RIGHT-TO-LEFT OVERRIDE) is the canonical visual-spoofing codepoint; if it
        // survived into the title the user could see a flipped rendering of the function name.
        assertEquals("Transfer", prettifyEvmFunctionName("transfer‮(address,uint256)"))
    }

    @Test
    fun `zero-width joiners and word joiner are stripped`() {
        // Same threat profile as the bidi override but quieter. U+200C (ZWNJ), U+200D (ZWJ),
        // U+2060 (WORD JOINER) all break visual identity without changing rendered glyphs.
        assertEquals("Swap Tokens", prettifyEvmFunctionName("swap‌‍Tokens⁠(address)"))
    }

    @Test
    fun `variation selectors are stripped`() {
        // U+FE0F (VS-16) toggles the visual presentation of a base codepoint. Letting it through
        // would let an attacker morph an ASCII letter in the function name into an emoji glyph.
        assertEquals("Transfer", prettifyEvmFunctionName("transfer️(address)"))
    }

    @Test
    fun `tag codepoints in plane 14 are stripped via codepoint-aware iteration`() {
        // Plane-14 tag codepoints (U+E0020..U+E007E) render as nothing but stay in the string;
        // they are non-BMP, so the filter must iterate by codepoint, not by Char, otherwise the
        // surrogate pair sneaks through unchanged.
        val tagSpace = String(Character.toChars(0xE0020))
        val tagA = String(Character.toChars(0xE0041))
        assertEquals("Transfer", prettifyEvmFunctionName("transfer$tagSpace$tagA(address)"))
    }

    @Test
    fun `iso control characters are stripped`() {
        // Tabs, newlines, NULL, and the 0x7F-0x9F C1 controls are all caught by
        // `Character.isISOControl`. None should survive into the rendered title.
        assertEquals("Transfer", prettifyEvmFunctionName("trans\tfer\n(address)"))
    }
}
